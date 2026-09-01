package org.example.normalize;

import org.example.config.BankProfile;
import org.example.diagnostics.ErrorCode;
import org.example.parse.RawRow;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * 01-components.md C5 {@code parse_row}/{@code parse_amount}/{@code parse_statement_date}.
 * Every monetary value is constructed only from {@code String} (ADR-0018) - never
 * {@code Double.parseDouble} anywhere in this path.
 */
public final class RowParser {

    private RowParser() {
    }

    /** ROW-level errors per 03-interfaces-and-contracts.md §6: the row is dropped, the file continues. */
    public static final class RowParseException extends RuntimeException {
        private final ErrorCode code;

        public RowParseException(ErrorCode code, String message) {
            super(message);
            this.code = code;
        }

        public ErrorCode code() {
            return code;
        }
    }

    public record AmountResult(BigDecimal amount, Direction direction) {
    }

    /**
     * One parsed money cell. {@code creditMarker}/{@code debitMarker} record a Dr/Cr-style
     * direction indicator found <em>in the cell itself</em>, which is how single-amount-column
     * statements encode direction (02-data-model.md §1.5 {@code amount_sign: dr_cr_suffix}).
     */
    private record Money(BigDecimal magnitude, boolean negative, boolean creditMarker, boolean debitMarker) {
    }

    public static Transaction parseRow(RawRow row, BankProfile profile, String currencyDefault) {
        LocalDate date = parseStatementDate(row.dateText(), profile.dateFormats());
        if (date == null) {
            throw new RowParseException(ErrorCode.ROW_301, "date could not be parsed: '" + row.dateText() + "'");
        }

        AmountResult amountResult = parseAmount(row, profile);
        if (amountResult == null) {
            boolean anyAmountText = notBlank(row.debitText()) || notBlank(row.creditText())
                    || notBlank(row.amountText());
            throw anyAmountText
                    ? new RowParseException(ErrorCode.ROW_302, "amount could not be parsed")
                    : new RowParseException(ErrorCode.ROW_303, "row has neither a debit nor a credit amount");
        }

        String description = cleanDescription(row.descriptionText());
        if (description.isEmpty()) {
            throw new RowParseException(ErrorCode.ROW_304, "description was empty after cleaning");
        }

        String id = computeId(date, row.descriptionText(), amountResult.amount(), amountResult.direction(),
                row.sourceFile(), row.rowIndex());
        String merchantKey = MerchantKey.of(row.descriptionText());

        return new Transaction(
                id,
                date,
                description,
                row.descriptionText(),
                amountResult.amount(),
                currencyDefault,
                amountResult.direction(),
                row.accountHint(),
                row.sourceFile(),
                "Uncategorized",
                CategorySource.UNCATEGORIZED,
                false,
                false,
                merchantKey,
                row.confidence(),
                null,
                null,
                row.rowIndex()
        );
    }

    /**
     * Resolves magnitude and direction from whichever encoding the profile declares
     * (02-data-model.md §1.5 {@code amount_sign}). Separate debit/credit columns win whenever
     * they carry a value, because a statement that has them is unambiguous; the single-column
     * encodings are only consulted when they do not.
     */
    public static AmountResult parseAmount(RawRow row, BankProfile profile) {
        Money debit = parseMoney(row.debitText(), profile);
        Money credit = parseMoney(row.creditText(), profile);

        boolean hasDebit = isPositive(debit);
        boolean hasCredit = isPositive(credit);
        if (hasDebit && !hasCredit) {
            return new AmountResult(scale(debit.magnitude()), Direction.DEBIT);
        }
        if (hasCredit && !hasDebit) {
            return new AmountResult(scale(credit.magnitude()), Direction.CREDIT);
        }
        if (hasDebit) {
            // Both columns populated: the statement contradicts itself. Refusing the row is
            // the honest outcome - guessing here silently mis-signs a transaction (NFR6).
            return null;
        }

        Money single = parseMoney(row.amountText(), profile);
        if (!isPositive(single)) {
            return null;
        }
        return new AmountResult(scale(single.magnitude()), directionOf(single, profile));
    }

    private static Direction directionOf(Money money, BankProfile profile) {
        if (money.creditMarker()) {
            return Direction.CREDIT;
        }
        if (money.debitMarker()) {
            return Direction.DEBIT;
        }
        if (BankProfile.SIGN_DEBIT_NEGATIVE.equals(profile.amountSign())) {
            return money.negative() ? Direction.DEBIT : Direction.CREDIT;
        }
        // dr_cr_suffix and anything else: an unmarked amount in a single column is a debit -
        // a statement that flags its credits says nothing about its debits, which is the whole
        // point of the marker.
        return Direction.DEBIT;
    }

    /**
     * C5's {@code parse_amount} contract: "Handles 1,234.56 / 1.234,56 / (123.45) negatives /
     * trailing Cr|Dr / embedded currency symbols and codes."
     */
    private static Money parseMoney(String text, BankProfile profile) {
        if (text == null || text.isBlank()) {
            return null;
        }
        String cleaned = text.trim();

        boolean negative = false;
        if (cleaned.startsWith("(") && cleaned.endsWith(")")) {
            negative = true;
            cleaned = cleaned.substring(1, cleaned.length() - 1).trim();
        }

        boolean creditMarker = false;
        for (String marker : profile.creditMarkersOrDefault()) {
            String stripped = stripMarker(cleaned, marker);
            if (stripped != null) {
                creditMarker = true;
                cleaned = stripped;
                break;
            }
        }
        boolean debitMarker = false;
        if (!creditMarker) {
            for (String marker : List.of("Dr", "DR")) {
                String stripped = stripMarker(cleaned, marker);
                if (stripped != null) {
                    debitMarker = true;
                    cleaned = stripped;
                    break;
                }
            }
        }

        if (cleaned.startsWith("-") || cleaned.endsWith("-")) {
            negative = true;
        }

        String thousands = profile.thousandsSeparator() == null ? "," : profile.thousandsSeparator();
        String decimal = profile.decimalSeparator() == null ? "." : profile.decimalSeparator();
        cleaned = cleaned.replace(thousands, "");
        if (!decimal.equals(".")) {
            cleaned = cleaned.replace(decimal, ".");
        }
        // Whatever survives - a rupee sign, an ISO code, stray spaces - is not part of the
        // number. Signs were already captured above, so they are stripped here too.
        cleaned = cleaned.replaceAll("[^0-9.]", "");
        if (cleaned.isEmpty() || cleaned.equals(".")) {
            return null;
        }
        try {
            // from String only - never a double intermediate (ADR-0018)
            return new Money(new BigDecimal(cleaned), negative, creditMarker, debitMarker);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    /**
     * Removes a direction marker from either end of a cell. Markers are matched at the cell
     * edges only: "CR" inside "CRED CARD" is part of a description, not a direction flag.
     */
    private static String stripMarker(String cell, String marker) {
        if (marker == null || marker.isBlank()) {
            return null;
        }
        String m = marker.trim();
        if (cell.length() < m.length()) {
            return null;
        }
        if (cell.regionMatches(true, cell.length() - m.length(), m, 0, m.length())) {
            return cell.substring(0, cell.length() - m.length()).trim();
        }
        if (cell.regionMatches(true, 0, m, 0, m.length())) {
            return cell.substring(m.length()).trim();
        }
        return null;
    }

    private static boolean isPositive(Money money) {
        return money != null && money.magnitude().compareTo(BigDecimal.ZERO) > 0;
    }

    private static BigDecimal scale(BigDecimal value) {
        return value.setScale(2, RoundingMode.HALF_UP);
    }

    private static boolean notBlank(String s) {
        return s != null && !s.isBlank();
    }

    /**
     * Tries the profile's declared formats against the cell, then against the leading part of
     * it. A PDF date cell routinely carries more than the date - the validated card statement
     * prints "11/07/2026| 14:02" in one cell - and dropping such a row would lose a real
     * transaction over punctuation.
     */
    public static LocalDate parseStatementDate(String text, List<String> dateFormats) {
        if (text == null || text.isBlank() || dateFormats == null) {
            return null;
        }
        for (String candidate : dateCandidates(text.trim())) {
            for (String pattern : dateFormats) {
                try {
                    return LocalDate.parse(candidate, DateTimeFormatter.ofPattern(pattern, Locale.ENGLISH));
                } catch (DateTimeParseException | IllegalArgumentException ignored) {
                    // try the next declared format
                }
            }
        }
        return null;
    }

    /** The whole cell first, then progressively shorter leading fragments of it. */
    private static List<String> dateCandidates(String trimmed) {
        List<String> candidates = new ArrayList<>();
        candidates.add(trimmed);
        int pipe = trimmed.indexOf('|');
        if (pipe > 0) {
            candidates.add(trimmed.substring(0, pipe).trim());
        }
        int space = trimmed.indexOf(' ');
        if (space > 0) {
            candidates.add(trimmed.substring(0, space).trim());
        }
        return candidates.stream().filter(c -> !c.isEmpty()).distinct().toList();
    }

    private static String cleanDescription(String raw) {
        if (raw == null) {
            return "";
        }
        return raw.trim().replaceAll("\\s+", " ");
    }

    private static String computeId(LocalDate date, String rawDescription, BigDecimal amount, Direction direction,
                                     String sourceFile, int rowIndex) {
        String basis = String.join("|", date.toString(), rawDescription, amount.toPlainString(), direction.name(),
                sourceFile, String.valueOf(rowIndex));
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(basis.getBytes(StandardCharsets.UTF_8));
            StringBuilder hex = new StringBuilder();
            for (byte b : hash) {
                hex.append(String.format("%02x", b));
            }
            return hex.substring(0, 16);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 not available", e);
        }
    }
}

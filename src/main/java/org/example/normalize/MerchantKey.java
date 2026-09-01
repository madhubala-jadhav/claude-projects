package org.example.normalize;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * The stable merchant identity a saved correction is remembered against (FR9, AC4).
 *
 * <p>F6 freezes this derivation: "Changing it <em>orphans every saved correction</em>, because
 * keys stop matching." This implementation was therefore revised deliberately <strong>before
 * Slice 5 ships</strong> — no {@code corrections.json} has ever been written, so today is the
 * last moment the algorithm is free to change. After the first correction is stored, a change
 * here costs a migration that re-derives keys from {@code example_description}.</p>
 *
 * <p>01-components.md C6 states the rule in prose — "Uppercase, strip POS/UPI/NEFT/IMPS/ATM
 * prefixes, strip trailing reference numbers and dates, collapse whitespace, keep the first 3
 * significant tokens" — without pseudocode. Three worked examples exist across the architecture
 * docs, and all three are pinned as tests:</p>
 * <pre>
 *   'POS SWIGGY*ORDER 4471 BANGALORE IN'     -&gt; 'SWIGGY'         (01-components.md C6)
 *   'UPI/AMAZON PAY/9928311/PAYMENT'         -&gt; 'AMAZON PAY'     (01-components.md C6)
 *   'NEFT DR-HDFC0001234-RENT TRANSFER-AUG'  -&gt; 'RENT TRANSFER'  (02-data-model.md §3.7)
 * </pre>
 *
 * <p>The third one is why this was rewritten: the original implementation produced
 * {@code 'DR-RENT TRANSFER-AUG'} for it. Validating against the real HDFC statement found two
 * more failures that no fabricated fixture had exposed — {@code 'IND*ADOBEhttps://www.'}
 * keyed as {@code 'IND'} (the aggregator tag, not the merchant, so every {@code IND*} merchant
 * on the statement would have collided into one key), and a merchant glued to its city by a
 * missing space keyed as {@code 'BIGBASKETCHENNAI'}.</p>
 */
public final class MerchantKey {

    /** 02-data-model.md §1.1: "Uppercase, ≤ 60 chars". */
    private static final int MAX_LENGTH = 60;

    private static final int SIGNIFICANT_TOKENS = 3;

    /**
     * Payment rails and mandate types that prefix a description without naming a merchant. C6
     * names the first five; ACH/ECS/NACH are the same kind of thing and appear on real Indian
     * statements ("ACH DR HOME LOAN EMI").
     */
    private static final Pattern RAIL_PREFIX = Pattern.compile(
            "^(POS|UPI|NEFT|IMPS|ATM|ACH|ECS|NACH)[\\s/:-]+", Pattern.CASE_INSENSITIVE);

    /** A debit/credit marker sitting between the rail and the merchant: "NEFT DR-...". */
    private static final Pattern DIRECTION_MARKER = Pattern.compile("^(DR|CR)[\\s/:-]+");

    /**
     * Aggregator tags that appear <em>before</em> the star, where the merchant is what follows:
     * "IND*ADOBE" is Adobe billed through an aggregator, not a merchant called IND.
     * Contrast "SWIGGY*ORDER 4471", where the merchant precedes the star.
     */
    private static final Set<String> AGGREGATOR_TAGS = Set.of(
            "IND", "SQ", "PAYU", "RAZ", "RAZP", "PYTM", "PAYTM", "BD", "CCA", "TPV", "MSWIPE");

    /** Length below which an unrecognised pre-star token is treated as a tag rather than a name. */
    private static final int MAX_TAG_LENGTH = 4;

    private static final Pattern URL = Pattern.compile("(HTTPS?://|WWW\\.)\\S*", Pattern.CASE_INSENSITIVE);
    private static final Pattern BRACKETED = Pattern.compile("\\([^)]*\\)");
    private static final Pattern REFERENCE_LABEL = Pattern.compile("REF\\s*#?\\s*\\S*", Pattern.CASE_INSENSITIVE);
    private static final Pattern CAMEL_BOUNDARY = Pattern.compile("([a-z])([A-Z])");

    private static final Pattern NUMERIC_TOKEN = Pattern.compile("^[0-9./:-]+$");
    /** A reference like HDFC0001234: letters welded to a long digit run is never a merchant name. */
    private static final Pattern REFERENCE_TOKEN = Pattern.compile("^(?=.*[A-Z])(?=(?:[^0-9]*[0-9]){4,}).*$");
    private static final Pattern YEAR_TOKEN = Pattern.compile("^(19|20)[0-9]{2}$");
    private static final Set<String> MONTHS = Set.of(
            "JAN", "FEB", "MAR", "APR", "MAY", "JUN", "JUL", "AUG", "SEP", "SEPT", "OCT", "NOV", "DEC");
    private static final Set<String> NOISE_TOKENS = Set.of(
            "POS", "UPI", "NEFT", "IMPS", "ATM", "ACH", "ECS", "NACH", "DR", "CR", "REF", "TXN");

    private MerchantKey() {
    }

    public static String of(String rawDescription) {
        if (rawDescription == null) {
            return "";
        }

        String s = URL.matcher(rawDescription.trim()).replaceAll(" ");
        // Restore the space a statement dropped between a merchant and what follows it. PDF
        // extraction routinely welds cells together ("BigbasketCHENNAI"), and the lower-to-upper
        // boundary is the only evidence left that there were two words.
        s = CAMEL_BOUNDARY.matcher(s).replaceAll("$1 $2");
        s = s.toUpperCase(Locale.ROOT);
        s = BRACKETED.matcher(s).replaceAll(" ");
        s = REFERENCE_LABEL.matcher(s).replaceAll(" ");
        s = stripRepeatedly(RAIL_PREFIX, s);
        s = DIRECTION_MARKER.matcher(s.trim()).replaceFirst("");
        s = beforeOrAfterStar(s);

        // Only the first slash-delimited segment names the merchant; the rest are reference
        // numbers and free-text notes ("UPI/AMAZON PAY/9928311/PAYMENT").
        int slash = s.indexOf('/');
        if (slash >= 0) {
            s = s.substring(0, slash);
        }

        List<String> significant = new ArrayList<>();
        List<String> fallback = new ArrayList<>();
        // Hyphens join fields as often as they join words on a statement, so they split tokens
        // here: "DR-HDFC0001234-RENT" is three fields, only one of which is the merchant.
        for (String token : s.split("[\\s\\-]+")) {
            if (token.isBlank()) {
                continue;
            }
            if (fallback.size() < SIGNIFICANT_TOKENS) {
                fallback.add(token);
            }
            if (isNoise(token)) {
                continue;
            }
            significant.add(token);
            if (significant.size() == SIGNIFICANT_TOKENS) {
                break;
            }
        }

        List<String> kept = significant.isEmpty() ? fallback : significant;
        String key = String.join(" ", kept).trim();
        return key.length() > MAX_LENGTH ? key.substring(0, MAX_LENGTH).trim() : key;
    }

    /**
     * Decides which side of an aggregator star carries the merchant name. A short or known tag
     * before the star means the merchant follows it; anything longer is the merchant itself and
     * what follows is an order reference.
     */
    private static String beforeOrAfterStar(String s) {
        int star = s.indexOf('*');
        if (star < 0) {
            return s;
        }
        String before = s.substring(0, star).trim();
        String after = s.substring(star + 1).trim();
        String lastToken = before.isEmpty() ? "" : before.substring(before.lastIndexOf(' ') + 1);
        boolean tag = AGGREGATOR_TAGS.contains(lastToken)
                || (!lastToken.isEmpty() && lastToken.length() <= MAX_TAG_LENGTH);
        if (tag && !after.isEmpty()) {
            return after;
        }
        return before.isEmpty() ? after : before;
    }

    /** Real descriptions sometimes stack rails, e.g. "UPI/POS/...". */
    private static String stripRepeatedly(Pattern pattern, String value) {
        String previous;
        String current = value.trim();
        do {
            previous = current;
            current = pattern.matcher(current).replaceFirst("").trim();
        } while (!current.equals(previous));
        return current;
    }

    private static boolean isNoise(String token) {
        return NOISE_TOKENS.contains(token)
                || MONTHS.contains(token)
                || YEAR_TOKEN.matcher(token).matches()
                || NUMERIC_TOKEN.matcher(token).matches()
                || REFERENCE_TOKEN.matcher(token).matches();
    }
}

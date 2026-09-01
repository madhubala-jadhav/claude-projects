package org.example.report;

import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVPrinter;
import org.example.normalize.Transaction;

import java.io.BufferedWriter;
import java.io.IOException;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

/**
 * FR21 / AC9 - {@code output/<month>/transactions.csv}.
 *
 * <p>F3 freezes both the column set and its order (02-data-model.md §3.7): "New columns append
 * to the right only; existing columns never move, rename or change meaning." The consumer is
 * the user's own spreadsheet, so a reordering here silently breaks pivot tables built on last
 * month's file.</p>
 */
public final class TransactionsCsvWriter {

    /** The 15 frozen columns of F3, in their frozen order. */
    static final List<String> COLUMNS = List.of(
            "date", "description", "raw_description", "merchant_key", "amount", "currency", "direction",
            "category", "category_source", "is_transfer", "needs_review", "confidence", "source_account",
            "source_file", "transaction_id");

    private TransactionsCsvWriter() {
    }

    public static Path write(List<Transaction> transactions, Path monthDir) throws IOException {
        Files.createDirectories(monthDir);
        Path target = monthDir.resolve("transactions.csv");
        try (OutputStream out = Files.newOutputStream(target)) {
            // §3.7: "UTF-8 with BOM so Excel on Windows renders the rupee sign and non-ASCII
            // merchant names correctly." Excel guesses the system codepage without it.
            out.write(new byte[]{(byte) 0xEF, (byte) 0xBB, (byte) 0xBF});
            try (Writer writer = new BufferedWriter(new OutputStreamWriter(out, StandardCharsets.UTF_8));
                 CSVPrinter printer = new CSVPrinter(writer,
                         CSVFormat.RFC4180.builder().setHeader(COLUMNS.toArray(new String[0])).build())) {
                for (Transaction txn : transactions) {
                    printer.printRecord(
                            txn.date().toString(),
                            txn.description(),
                            txn.rawDescription(),
                            txn.merchantKey(),
                            // toPlainString, never toString: a BigDecimal can render in
                            // scientific notation, which no spreadsheet reads back as money.
                            txn.amount().toPlainString(),
                            txn.currency(),
                            txn.direction().name().toLowerCase(java.util.Locale.ROOT),
                            txn.category(),
                            txn.categorySource().wireName(),
                            txn.isTransfer(),
                            txn.needsReview(),
                            txn.confidence(),
                            txn.sourceAccount() == null ? "" : txn.sourceAccount(),
                            txn.sourceFile(),
                            txn.id());
                }
            }
        }
        return target;
    }
}

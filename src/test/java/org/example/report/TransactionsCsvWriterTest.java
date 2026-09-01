package org.example.report;

import org.example.normalize.CategorySource;
import org.example.normalize.Direction;
import org.example.normalize.Transaction;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** FR21 / AC9, against the F3 frozen column contract (02-data-model.md §3.7). */
class TransactionsCsvWriterTest {

    @Test
    void writesTheFifteenFrozenColumnsInTheirFrozenOrder(@TempDir Path tmp) throws IOException {
        TransactionsCsvWriter.write(List.of(sample()), tmp);

        String header = firstLine(tmp.resolve("transactions.csv"));

        // F3: "New columns append to the right only; existing columns never move, rename or
        // change meaning." Spelled out literally so a reorder cannot pass unnoticed.
        assertEquals("date,description,raw_description,merchant_key,amount,currency,direction,"
                + "category,category_source,is_transfer,needs_review,confidence,source_account,"
                + "source_file,transaction_id", header);
    }

    @Test
    void startsWithAUtf8BomSoExcelOnWindowsRendersTheRupeeSign(@TempDir Path tmp) throws IOException {
        Path csv = TransactionsCsvWriter.write(List.of(sample()), tmp);

        byte[] first = new byte[3];
        System.arraycopy(Files.readAllBytes(csv), 0, first, 0, 3);
        assertArrayEquals(new byte[]{(byte) 0xEF, (byte) 0xBB, (byte) 0xBF}, first);
    }

    @Test
    void writesAmountsInPlainDecimalAndTheFrozenLowercaseEnums(@TempDir Path tmp) throws IOException {
        Path csv = TransactionsCsvWriter.write(List.of(sample()), tmp);

        String body = Files.readString(csv, StandardCharsets.UTF_8).lines().skip(1).findFirst().orElseThrow();

        assertTrue(body.contains("487.00"), body);
        assertTrue(body.contains("debit"), body);
        assertTrue(body.contains("uncategorized"), body);
        assertTrue(body.contains("HDFC-XXXX1234"), body);
    }

    @Test
    void quotesADescriptionContainingACommaSoTheColumnsStayAligned(@TempDir Path tmp) throws IOException {
        Transaction awkward = sample("POS SWIGGY, BANGALORE \"IN\"");
        Path csv = TransactionsCsvWriter.write(List.of(awkward), tmp);

        String body = Files.readString(csv, StandardCharsets.UTF_8).lines().skip(1).findFirst().orElseThrow();

        assertTrue(body.contains("\"POS SWIGGY, BANGALORE \"\"IN\"\"\""), body);
    }

    @Test
    void aNullAccountIsAnEmptyCellRatherThanTheWordNull(@TempDir Path tmp) throws IOException {
        Transaction anonymous = new Transaction(
                "1a4b7c22de90f331", LocalDate.of(2026, 8, 2), "SWIGGY", "POS SWIGGY",
                new BigDecimal("487.00"), "INR", Direction.DEBIT, null, "hdfc_aug2026.csv",
                "Food & Dining", CategorySource.RULE, false, false, "SWIGGY", 1.0, null, null, 0);

        Path csv = TransactionsCsvWriter.write(List.of(anonymous), tmp);
        String body = Files.readString(csv, StandardCharsets.UTF_8).lines().skip(1).findFirst().orElseThrow();

        assertTrue(body.contains(",,hdfc_aug2026.csv,"), body);
    }

    private static String firstLine(Path csv) throws IOException {
        // Skip the BOM, which is a byte-order marker and not part of the header text.
        return Files.readString(csv, StandardCharsets.UTF_8).substring(1).lines().findFirst().orElseThrow();
    }

    private static Transaction sample() {
        return sample("POS SWIGGY*ORDER 4471 BANGALORE IN");
    }

    private static Transaction sample(String rawDescription) {
        return new Transaction(
                "8f2c41ad9be07c53", LocalDate.of(2026, 8, 14), "SWIGGY*ORDER 4471", rawDescription,
                new BigDecimal("487.00"), "INR", Direction.DEBIT, "HDFC-XXXX1234", "hdfc_aug2026.pdf",
                "Uncategorized", CategorySource.UNCATEGORIZED, false, true, "SWIGGY", 1.0, null, null, 42);
    }
}

package org.example.diagnostics;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

class RedactionTest {

    @Test
    void masksAnEightPlusDigitRunKeepingTheLastFour() {
        String redacted = Redaction.redact("Account No: 123456789012");
        assertEquals("Account No: XXXXXXXX9012", redacted); // 12 digits -> 8 X's + last 4
        assertFalse(redacted.contains("123456789012"));
    }

    @Test
    void leavesShortDigitRunsAlone() {
        assertEquals("row 42 of 100", Redaction.redact("row 42 of 100"));
    }

    @Test
    void masksAPasswordArgumentValueOnly() {
        String redacted = Redaction.redact("prompted with --password hunter2secret");
        assertFalse(redacted.contains("hunter2secret"));
        assertEquals("prompted with --password <redacted>", redacted);
    }

    @Test
    void masksMultipleDigitRunsInOneLine() {
        String redacted = Redaction.redact("card 4111111111111111 linked to acct 987654321098");
        assertFalse(redacted.contains("4111111111111111"));
        assertFalse(redacted.contains("987654321098"));
    }
}

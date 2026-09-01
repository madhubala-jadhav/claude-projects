package org.example.diagnostics;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 01-components.md C9 {@code redact()}: "Masks anything that looks like an account number
 * (&gt;=8 consecutive digits -&gt; last 4 kept), a card PAN, or a password argument, before
 * it reaches a log line. Applied by the logging handler, not by callers (NFR1, §13)."
 *
 * <p>Account numbers are already masked to last-4 at parse time (02-data-model.md §1.1,
 * "Masked to last 4 digits at parse time"); this is the second line of defence the same
 * section names. Absolute paths are guarded separately, structurally - every log-writing
 * call site in this codebase passes a basename, never {@code Path.toAbsolutePath()} - rather
 * than by this regex, since a path does not reliably look like "an account number, a card
 * PAN, or a password argument".</p>
 */
public final class Redaction {

    private Redaction() {
    }

    /** A card PAN (13-19 digits) or a bank account number (>=8 digits), run together. */
    private static final Pattern LONG_DIGIT_RUN = Pattern.compile("\\d{8,}");

    /** {@code --password X}, {@code password=X}, {@code password: X} - value only, never the key. */
    private static final Pattern PASSWORD_ARG = Pattern.compile(
            "(?i)(--password|\\bpassword)([=:]|\\s+)(\\S+)");

    public static String redact(String text) {
        if (text == null) {
            return null;
        }
        return maskDigitRuns(maskPasswords(text));
    }

    private static String maskPasswords(String text) {
        Matcher m = PASSWORD_ARG.matcher(text);
        StringBuilder sb = new StringBuilder();
        while (m.find()) {
            String replacement = m.group(1) + m.group(2) + "<redacted>";
            m.appendReplacement(sb, Matcher.quoteReplacement(replacement));
        }
        m.appendTail(sb);
        return sb.toString();
    }

    private static String maskDigitRuns(String text) {
        Matcher m = LONG_DIGIT_RUN.matcher(text);
        StringBuilder sb = new StringBuilder();
        while (m.find()) {
            String digits = m.group();
            String masked = "X".repeat(digits.length() - 4) + digits.substring(digits.length() - 4);
            m.appendReplacement(sb, Matcher.quoteReplacement(masked));
        }
        m.appendTail(sb);
        return sb.toString();
    }
}

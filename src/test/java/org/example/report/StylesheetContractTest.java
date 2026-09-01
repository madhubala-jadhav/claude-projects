package org.example.report;

import org.example.diagnostics.RunReport;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Guards the seams between the three files that together make the page: {@code tokens.json}
 * emits the custom properties, {@code report.css} and the template consume them, and
 * {@code report.js} reads both the properties and the JSON island.
 *
 * <p>These seams fail silently. A mistyped {@code var(--surfce-raised)} is not a CSS error - the
 * declaration is simply dropped and the element renders transparent; a renamed island field
 * reads as {@code undefined} and the chart quietly draws nothing. Neither shows up in a
 * screenshot review of the one theme the author happened to look at, so they are checked here
 * instead.</p>
 */
class StylesheetContractTest {

    private static final Pattern VAR_USE = Pattern.compile("var\\((--[a-z0-9-]+)");
    private static final Pattern VAR_DEF = Pattern.compile("(--[a-z0-9-]+)\\s*:");

    /**
     * Set by an inline {@code style} attribute on the row that uses it, so it is legitimately
     * absent from the stylesheet's own definitions.
     */
    private static final Set<String> DEFINED_INLINE = Set.of("--slot-top");

    @Test
    void everyCustomPropertyTheStylesheetUsesIsActuallyDefined() throws IOException {
        String css = resource("/templates/report.css");
        String generated = DesignTokens.load().css();

        Set<String> defined = matches(VAR_DEF, generated);
        defined.addAll(matches(VAR_DEF, css));
        defined.addAll(DEFINED_INLINE);
        // The two font stacks are declared by the renderer, not by tokens.json - tokens.json's
        // font families are Figma proxies and the shipped report loads no web font (NFR1).
        defined.add("--font-sans");
        defined.add("--font-mono");

        Set<String> used = matches(VAR_USE, css);
        assertTrue(used.size() > 30, "the check is only meaningful if it actually found usages: " + used.size());
        used.removeAll(defined);
        assertEquals(Set.of(), used, "report.css references custom properties nothing defines");
    }

    @Test
    void everyCustomPropertyThePageScriptResolvesIsActuallyDefined() throws IOException {
        String js = resource("/templates/report.js");
        Set<String> defined = matches(VAR_DEF, DesignTokens.load().css());
        defined.add("--font-sans");

        Set<String> used = matches(Pattern.compile("token\\(\"(--[a-z0-9-]+)\""), js);
        assertTrue(used.size() >= 4, "the check found no usages, so it proves nothing");
        // Slot fills are built by concatenation, so they are named only in part in the source.
        used.remove("--chart-series-slot");
        used.removeAll(defined);
        assertEquals(Set.of(), used, "report.js resolves custom properties nothing defines");
    }

    @Test
    void theDataIslandCarriesEveryFieldTheChartScriptReads() throws IOException {
        String html = ReportRenderer.render(ReportRendererTest.fixtureSummary(),
                ReportRendererTest.fixtureTransactions(), new RunReport(), 8, 1.0);
        String island = html.substring(html.indexOf("id=\"expense-data\">") + 18);
        island = island.substring(0, island.indexOf("</script>"));

        // The contract between ChartModel and report.js. Renaming either side without the other
        // leaves an undefined in the chart rather than an error anyone would notice.
        for (String field : new String[]{"\"name\"", "\"amount\"", "\"percent\"", "\"txn_count\"",
                "\"kind\"", "\"fill_var\"", "\"members\""}) {
            assertTrue(island.contains(field), "slice field missing from the data island: " + field);
        }
        for (String field : new String[]{"\"top_category\"", "\"by_category\"", "\"currency\"",
                "\"top_n_slices\"", "\"min_slice_percent\""}) {
            assertTrue(island.contains(field), "summary field missing from the data island: " + field);
        }
        for (String field : new String[]{"\"raw_description\"", "\"needs_review\"", "\"direction\"",
                "\"category\"", "\"date\""}) {
            assertTrue(island.contains(field), "transaction field missing from the data island: " + field);
        }
    }

    @Test
    void bothThemesDefineTheSamePropertySet() throws IOException {
        DesignTokens tokens = DesignTokens.load();

        // A colour defined in only one theme renders as nothing in the other - the failure mode
        // that a light-mode-only review never catches.
        assertEquals(tokens.lightColors().keySet(), tokens.darkColors().keySet());
    }

    private static Set<String> matches(Pattern pattern, String text) {
        Set<String> found = new LinkedHashSet<>();
        Matcher m = pattern.matcher(text);
        while (m.find()) {
            found.add(m.group(1));
        }
        return found;
    }

    private static String resource(String path) throws IOException {
        try (InputStream in = StylesheetContractTest.class.getResourceAsStream(path)) {
            if (in == null) {
                throw new IOException("missing resource " + path);
            }
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
    }
}

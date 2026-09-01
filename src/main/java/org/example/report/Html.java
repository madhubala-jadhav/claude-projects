package org.example.report;

/**
 * Escaping for the few places the renderer writes markup directly rather than through Pebble,
 * which autoescapes on its own.
 *
 * <p>It matters here because the strings involved come from bank statements: a merchant
 * description is arbitrary text from an untrusted file, and it reaches both the SVG chart and
 * the JSON island. A description containing {@code </script>} would otherwise end the data
 * island early and turn a statement into markup.</p>
 */
final class Html {

    private Html() {
    }

    static String escape(String value) {
        if (value == null) {
            return "";
        }
        return value.replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;")
                .replace("'", "&#39;");
    }

    /**
     * Neutralises the one sequence that can terminate a {@code <script>} block from inside a
     * JSON string. The HTML parser looks for the literal characters {@code </script} without
     * interpreting JSON escapes, so splitting the tag is what makes the island safe; JSON
     * readers see the identical string either way.
     */
    static String escapeJsonIsland(String json) {
        return json.replace("</", "<\\/");
    }
}

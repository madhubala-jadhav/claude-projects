package org.example.report;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.io.InputStream;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

/**
 * Turns the design package's {@code tokens.json} into the CSS custom properties the report is
 * styled with.
 *
 * <p>ADR-0007 makes this the anti-drift mechanism, not a convenience: "Design tokens flow from
 * tokens.json → CSS custom properties → both the report and the Figma plugin, so the report and
 * the design file cannot drift." Hand-copying the palette into a stylesheet would let the
 * shipped report and the Figma file disagree about what {@code slot2} is, silently, forever -
 * so nothing here hard-codes a colour, and {@code tokens.json} is packaged (see {@code pom.xml})
 * rather than duplicated.</p>
 *
 * <p>The emitted theme structure is the three-state one the toggle needs: bare {@code :root}
 * carries the complete light palette, a {@code prefers-color-scheme} block overrides it for
 * viewers who have not chosen, and {@code :root[data-theme="dark"]} lets an explicit choice win
 * in both directions.</p>
 */
public final class DesignTokens {

    private static final String RESOURCE = "/design/tokens.json";

    private final Map<String, String> light;
    private final Map<String, String> dark;
    private final Map<String, String> shared;

    private DesignTokens(Map<String, String> light, Map<String, String> dark, Map<String, String> shared) {
        this.light = light;
        this.dark = dark;
        this.shared = shared;
    }

    public static DesignTokens load() throws IOException {
        try (InputStream in = DesignTokens.class.getResourceAsStream(RESOURCE)) {
            if (in == null) {
                throw new IOException("design tokens are missing from the package: " + RESOURCE);
            }
            JsonNode root = new ObjectMapper().readTree(in);

            Map<String, String> light = new LinkedHashMap<>();
            Map<String, String> dark = new LinkedHashMap<>();
            Map<String, String> shared = new LinkedHashMap<>();

            walk(root.path("color").path("light"), "", light);
            walk(root.path("color").path("dark"), "", dark);
            for (String group : new String[]{"spacing", "radius", "borderWidth", "size", "grid"}) {
                walk(root.path(group), kebab(group), shared);
            }
            walk(root.path("typography").path("fontSize"), "font-size", shared);
            walk(root.path("typography").path("lineHeight"), "line-height", shared);
            walk(root.path("typography").path("letterSpacing"), "letter-spacing", shared);

            return new DesignTokens(light, dark, shared);
        }
    }

    /** The full {@code <style>} body: variables for every theme state. */
    public String css() {
        StringBuilder sb = new StringBuilder();
        sb.append(":root{\n");
        emit(sb, shared);
        emit(sb, light);
        sb.append("}\n");

        sb.append("@media (prefers-color-scheme: dark){:root:not([data-theme=\"light\"]){\n");
        emit(sb, dark);
        sb.append("}}\n");

        sb.append(":root[data-theme=\"dark\"]{\n");
        emit(sb, dark);
        sb.append("}\n");
        return sb.toString();
    }

    /**
     * The chart palette, for the JSON island. The chart is drawn on a canvas, which cannot read
     * a CSS custom property, so the page's script resolves them at run time - these names are
     * the contract between this class and that lookup.
     */
    public Map<String, String> lightColors() {
        return Map.copyOf(light);
    }

    public Map<String, String> darkColors() {
        return Map.copyOf(dark);
    }

    private static void emit(StringBuilder sb, Map<String, String> vars) {
        for (Map.Entry<String, String> e : vars.entrySet()) {
            sb.append("  --").append(e.getKey()).append(':').append(e.getValue()).append(";\n");
        }
    }

    /**
     * Depth-first walk collecting every leaf that carries a {@code $value}. Keys beginning with
     * {@code $} are token metadata ({@code $type}, {@code $description}) and never become
     * variables.
     */
    private static void walk(JsonNode node, String prefix, Map<String, String> out) {
        if (node == null || node.isMissingNode()) {
            return;
        }
        JsonNode value = node.get("$value");
        if (value != null && (value.isTextual() || value.isNumber())) {
            out.put(prefix, format(prefix, value));
            return;
        }
        node.fieldNames().forEachRemaining(name -> {
            if (name.startsWith("$")) {
                return;
            }
            String key = prefix.isEmpty() ? kebab(name) : prefix + "-" + kebab(name);
            walk(node.get(name), key, out);
        });
    }

    private static String format(String key, JsonNode value) {
        if (value.isTextual()) {
            return value.asText();
        }
        // Line heights are ratios and the grid column count is a count; every other numeric
        // token in this file is a pixel length.
        if (key.startsWith("line-height") || key.equals("grid-columns")) {
            return trimNumber(value.asDouble());
        }
        return trimNumber(value.asDouble()) + "px";
    }

    private static String trimNumber(double v) {
        return v == Math.rint(v) ? String.valueOf((long) v) : String.valueOf(v);
    }

    /** {@code accentSubtle} → {@code accent-subtle}; {@code slot1} is left intact. */
    private static String kebab(String name) {
        return name.replaceAll("([a-z0-9])([A-Z])", "$1-$2").toLowerCase(Locale.ROOT);
    }
}

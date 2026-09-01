package org.example.normalize;

import java.util.Locale;

/** 01-components.md: {@code "rule" | "user_override" | "uncategorized"}. */
public enum CategorySource {
    RULE, USER_OVERRIDE, UNCATEGORIZED;

    /**
     * The spelling F1 and F3 freeze for this field. The Java constant names are an
     * implementation detail; {@code transactions.csv} and the report data island must carry
     * the lowercase form the contracts name, so the conversion lives here rather than being
     * repeated (and eventually diverging) at each serialization site.
     */
    public String wireName() {
        return name().toLowerCase(Locale.ROOT);
    }
}

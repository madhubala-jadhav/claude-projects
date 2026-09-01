package org.example.config;

/**
 * 01-components.md C2: "Raises ConfigError with a human-readable path like
 * 'categories.yaml: categories.Groceries.keywords must be a list of strings, got str'."
 * CFG-001/002/003/004 in the error taxonomy (03-interfaces-and-contracts.md §6) all surface
 * as this exception; C1 catches it and exits 2 (the only fatal *data* class).
 */
public class ConfigException extends RuntimeException {
    public ConfigException(String message) {
        super(message);
    }

    public ConfigException(String message, Throwable cause) {
        super(message, cause);
    }
}

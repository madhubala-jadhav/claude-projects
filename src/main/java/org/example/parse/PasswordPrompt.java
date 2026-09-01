package org.example.parse;

/**
 * spec §11.1: "If a PDF is password-protected, prompt once for a password (not stored)".
 *
 * <p>§13 makes the handling rule absolute - the password "is used in-memory only for that run
 * and is never written to disk or logs". Implementations must therefore not echo, cache or
 * log what they return, and callers must not put the value into {@code RunReport} or any
 * exception message.</p>
 */
@FunctionalInterface
public interface PasswordPrompt {
    /** @return the password, or {@code null} if the user declined or no console is available. */
    String ask(String fileName);
}

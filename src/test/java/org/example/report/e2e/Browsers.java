package org.example.report.e2e;

import com.microsoft.playwright.Browser;
import com.microsoft.playwright.BrowserContext;
import com.microsoft.playwright.Playwright;
import org.junit.jupiter.api.Assumptions;

/**
 * One Chromium instance shared by every browser-level test in this package, closed by a
 * shutdown hook. Launching a browser costs about a second; a context per test is free, and
 * gives each test its own {@code localStorage} and download directory.
 *
 * <p><b>Why this can skip.</b> Playwright downloads its browser binaries on first use, which
 * needs network access and about 150 MB of cache. Following the precedent
 * {@code RealStatementRegressionTest} set for the owner's statement, a missing browser skips
 * these tests rather than failing a build that has nothing wrong with it. That would let a
 * broken CI go quietly green, so CI passes {@code -De2e.strict=true} and the same condition
 * then fails loudly instead.</p>
 */
final class Browsers {

    /** Chromium is the engine that ships the File System Access API ADR-0006 names as the
     *  progressive enhancement, so it is the one where the corrections loop has the most left
     *  to prove. The report itself uses no engine-specific API. */
    private static Playwright playwright;
    private static Browser browser;
    private static String unavailable;

    private Browsers() {
    }

    static synchronized Browser browser() {
        if (browser != null) {
            return browser;
        }
        if (unavailable != null) {
            return giveUp();
        }
        try {
            playwright = Playwright.create();
            browser = playwright.chromium().launch();
            Runtime.getRuntime().addShutdownHook(new Thread(Browsers::closeQuietly));
            return browser;
        } catch (RuntimeException e) {
            unavailable = "no Playwright browser is available (" + e.getClass().getSimpleName() + ": "
                    + e.getMessage() + "). Install one with:\n"
                    + "  mvn exec:java -e -D exec.mainClass=com.microsoft.playwright.CLI "
                    + "-D exec.args=\"install --with-deps chromium\"";
            return giveUp();
        }
    }

    private static Browser giveUp() {
        if (Boolean.getBoolean("e2e.strict")) {
            throw new IllegalStateException("e2e.strict is set, so a missing browser is a failure: " + unavailable);
        }
        Assumptions.abort(unavailable);
        return null; // unreachable: abort throws.
    }

    /**
     * A context per test. {@code acceptDownloads} is what makes the tray's {@code Blob} export
     * observable - ADR-0006's whole loop ends in a downloaded file, so a test that could not
     * catch one would stop just short of the thing worth checking.
     */
    static BrowserContext context() {
        return browser().newContext(new Browser.NewContextOptions().setAcceptDownloads(true));
    }

    private static void closeQuietly() {
        try {
            if (browser != null) {
                browser.close();
            }
            if (playwright != null) {
                playwright.close();
            }
        } catch (RuntimeException e) {
            // The JVM is on its way out; a driver that will not close cleanly is not a test result.
        }
    }
}

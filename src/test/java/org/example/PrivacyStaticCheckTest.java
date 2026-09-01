package org.example;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * AC8 / NFR1 / F10 "the offline invariant": no component may open a network socket. This is
 * the Java equivalent of the roadmap's "import-lint" - a plain source-text scan for the
 * banned networking APIs, since Slice 1 doesn't add a Checkstyle/PMD dependency (that's an
 * ADR-0017 Dev-tier item, not required by Slice 1's exit criteria).
 */
class PrivacyStaticCheckTest {

    private static final List<String> BANNED = List.of(
            "java.net.Socket",
            "java.net.http.HttpClient",
            "HttpURLConnection",
            "URLConnection",
            "java.net.DatagramSocket"
    );

    @Test
    void mainSourceContainsNoNetworkingImportsOrClasses() throws IOException {
        Path srcMain = Path.of("src/main/java");
        try (Stream<Path> files = Files.walk(srcMain)) {
            List<Path> javaFiles = files.filter(p -> p.toString().endsWith(".java")).toList();
            assertTrue(javaFiles.size() > 10, "expected the Slice 1 source tree to be present");

            for (Path file : javaFiles) {
                String content = Files.readString(file);
                for (String banned : BANNED) {
                    assertTrue(!content.contains(banned),
                            file + " references a banned networking API: " + banned);
                }
            }
        }
    }
}

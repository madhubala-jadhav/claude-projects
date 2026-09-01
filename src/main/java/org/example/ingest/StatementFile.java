package org.example.ingest;

import java.nio.file.Path;

public record StatementFile(Path path, FileKind kind, long sizeBytes, String profileHint) {
}

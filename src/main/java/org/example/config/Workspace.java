package org.example.config;

import java.nio.file.Path;

/**
 * F7 (03-interfaces-and-contracts.md): the workspace directory layout - config/, input/,
 * output/&lt;YYYY-MM&gt;/, archive/ - is frozen.
 */
public record Workspace(Path configDir, Path inputDir, Path outputDir, Path archiveDir) {
}

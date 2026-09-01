package org.example.cli;

import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * FR23: zero required arguments. Slice 1 implements a minimal slice of the full F9 CLI
 * contract - {@code --config}/{@code --input}/{@code --output}/{@code --no-open}. The rest
 * ({@code --month}, {@code --no-ocr}, {@code --dry-run}, {@code --verbose}, {@code
 * --version}) is deliberately not implemented yet; none of it gates Slice 1's exit criteria.
 */
public final class Main {

    private Main() {
    }

    public static void main(String[] args) {
        Path configDir = null;
        Path inputDir = null;
        Path outputDir = null;
        boolean openBrowser = true;

        for (int i = 0; i < args.length; i++) {
            switch (args[i]) {
                case "--config" -> configDir = Paths.get(args[++i]);
                case "--input" -> inputDir = Paths.get(args[++i]);
                case "--output" -> outputDir = Paths.get(args[++i]);
                case "--no-open" -> openBrowser = false;
                default -> System.err.println("Unrecognized argument: " + args[i]
                        + " (Slice 1 supports --config, --input, --output, --no-open)");
            }
        }

        Cli.Options options = new Cli.Options(configDir, inputDir, outputDir, openBrowser);
        Cli.RunResult result = Cli.run(options, System.out);
        System.exit(result.exitCode());
    }
}

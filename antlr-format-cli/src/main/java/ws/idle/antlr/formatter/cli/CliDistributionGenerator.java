package ws.idle.antlr.formatter.cli;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Build-time entry point that writes generated shell completion scripts for distribution packaging.
 */
public final class CliDistributionGenerator {

    /** Prevents instantiation of the utility entry point. */
    private CliDistributionGenerator() {
    }

    /**
     * Writes the generated shell completion scripts into the supplied output directory.
     *
     * @param args command line arguments where {@code args[0]} is the output directory
     * @throws IOException if the completion files cannot be written
     */
    public static void main(String[] args) throws IOException {
        if (args.length != 1) {
            throw new IllegalArgumentException("Expected exactly one argument: <output-directory>");
        }

        Path outputDirectory = Path.of(args[0]);
        Files.createDirectories(outputDirectory);
        write(outputDirectory.resolve("antlr-format.bash"), CliCompletionScripts.bashCompletionScript());
        write(outputDirectory.resolve("_antlr-format"), CliCompletionScripts.zshCompletionScript());
        write(outputDirectory.resolve("antlr-format.fish"), CliCompletionScripts.fishCompletionScript());
    }

    /**
     * Writes a generated text file using UTF-8 encoding.
     *
     * @param file the output file path
     * @param content the text content to write
     * @throws IOException if the file cannot be written
     */
    private static void write(Path file, String content) throws IOException {
        Files.writeString(file, content, StandardCharsets.UTF_8);
    }
}


package ws.idle.antlr.formatter.cli;

import java.io.IOException;
import java.io.PrintWriter;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.Callable;
import picocli.CommandLine;
import picocli.CommandLine.Command;
import picocli.CommandLine.Mixin;
import picocli.CommandLine.Option;
import picocli.CommandLine.Parameters;
import picocli.CommandLine.Spec;
import picocli.CommandLine.Model.CommandSpec;
import ws.idle.antlr.formatter.FormattingOptions;
import ws.idle.antlr.formatter.FormattingOutputs;
import ws.idle.antlr.formatter.FormattingResult;
import ws.idle.antlr.formatter.GrammarFormatter;

/**
 * Dedicated command line entrypoint for formatting ANTLR grammars from a runnable jar.
 */
@Command(name = "antlr-format",
    description = "Formats an ANTLR grammar file using the Java formatter implementation.",
    mixinStandardHelpOptions = true,
    versionProvider = AntlrFormatCliVersionProvider.class)
public final class AntlrFormatCli implements Callable<Integer> {

    @Spec
    private CommandSpec spec;

    @Parameters(index = "0", paramLabel = "<grammar-file>", description = "The grammar file to format.")
    private Path input;

    @Option(names = { "-o", "--output" }, paramLabel = "<file>",
        description = "Write the formatted grammar to the specified file.")
    private Path output;

    @Option(names = { "-w", "--write" },
        description = "Overwrite the input grammar file in place.")
    private boolean writeInPlace;

    @Option(names = "--encoding", defaultValue = "UTF-8", paramLabel = "<charset>",
        description = "Read and write grammars using the specified character encoding. Default: ${DEFAULT-VALUE}.")
    private Charset encoding;

    @Option(names = "--add-options", negatable = true, defaultValue = "false",
        description = "Emit the effective formatter options as a directive comment at the top of the output.")
    private boolean addOptions;

    @Mixin
    private CliFormattingOptions formattingOptions = new CliFormattingOptions();

    /**
     * Launches the command line formatter.
     *
     * @param args the raw command line arguments
     */
    public static void main(String[] args) {
        int exitCode = new CommandLine(new AntlrFormatCli()).execute(args);
        System.exit(exitCode);
    }

    /**
     * Executes the formatter command.
     *
     * @return zero when formatting completes successfully
     * @throws IOException if the grammar cannot be read or written
     */
    @Override
    public Integer call() throws IOException {
        validateOutputArguments();

        String grammar = Files.readString(input, encoding);
        FormattingResult result = new GrammarFormatter(grammar, addOptions).formatGrammar(formattingOptions());
        String normalized = FormattingOutputs.normalizeForOutput(result.text());

        Path destination = resolveDestination();
        if (destination == null) {
            writeToStdout(normalized);
        } else {
            writeToFile(destination, normalized);
        }
        return 0;
    }

    /**
     * Exposes the parsed formatter overrides for tests and internal helpers.
     *
     * @return the sparse formatter options assembled from explicit CLI arguments
     */
    FormattingOptions formattingOptions() {
        return formattingOptions.toFormattingOptions();
    }

    /**
     * Ensures the selected output mode is unambiguous.
     */
    private void validateOutputArguments() {
        if (writeInPlace && output != null) {
            throw new CommandLine.ParameterException(spec.commandLine(),
                "Use either --write or --output, but not both.");
        }
    }

    /**
     * Resolves the destination file for the formatted grammar.
     *
     * @return the target file path, or {@code null} when output should be written to standard output
     */
    private Path resolveDestination() {
        if (writeInPlace) {
            return input;
        }
        return output;
    }

    /**
     * Writes the formatted grammar to standard output.
     *
     * @param text the normalized output text
     */
    private void writeToStdout(String text) {
        PrintWriter out = spec.commandLine().getOut();
        out.print(text);
        out.flush();
    }

    /**
     * Writes the formatted grammar to a file, creating parent directories when necessary.
     *
     * @param destination the destination file path
     * @param text the normalized output text
     * @throws IOException if the destination cannot be written
     */
    private void writeToFile(Path destination, String text) throws IOException {
        Path parent = destination.getParent();
        if (parent != null) {
            Files.createDirectories(parent);
        }
        Files.writeString(destination, text, encoding);
    }
}


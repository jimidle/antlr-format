package ws.idle.antlr.formatter;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

/** Tiny runner used as a bridge until the dedicated CLI project is added. */
public final class FormatterRunner {

    /** Prevents instantiation of the utility runner. */
    private FormatterRunner() {
    }

    /**
     * Formats the grammar file supplied on the command line and writes the result to standard output.
     *
     * @param args a single path to the grammar file to format
     * @throws IOException if the input file cannot be read
     */
    public static void main(String[] args) throws IOException {
        if (args.length != 1) {
            System.err.println("Usage: FormatterRunner <grammar-file>");
            System.exit(2);
        }

        Path input = Path.of(args[0]);
        String grammar = Files.readString(input, StandardCharsets.UTF_8);

        FormattingResult result = new GrammarFormatter(grammar).formatGrammar(new FormattingOptions());
        System.out.print(result.text());
    }
}


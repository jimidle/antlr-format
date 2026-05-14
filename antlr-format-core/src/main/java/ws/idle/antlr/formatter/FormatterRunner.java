package ws.idle.antlr.formatter;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

/** Tiny runner used as a bridge until the dedicated CLI project is added. */
public final class FormatterRunner {

    private FormatterRunner() {
    }

    public static void main(String[] args) throws IOException {
        if (args.length != 1) {
            System.err.println("Usage: FormatterRunner <grammar-file>");
            System.exit(2);
        }

        Path input = Path.of(args[0]);
        String grammar = Files.readString(input, StandardCharsets.UTF_8);

        FormattingConfiguration config = new FormattingConfiguration();
        config.main = new FormattingOptions();

        FormattingResult result = new AntlrFormatterService().format(grammar, config, false, 0, Integer.MAX_VALUE);
        System.out.print(result.text());
    }
}


package ws.idle.antlr.formatter.cli;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import picocli.CommandLine;
import ws.idle.antlr.formatter.ColonAlignment;
import ws.idle.antlr.formatter.FormattingOptions;
import ws.idle.antlr.formatter.FormattingOutputs;
import ws.idle.antlr.formatter.GrammarFormatter;
import ws.idle.antlr.formatter.SemicolonAlignment;

class AntlrFormatCliTest {

    private static final String UNFORMATTED_GRAMMAR = "grammar Demo;\na:'a';\n";

    @TempDir
    Path tempDir;

    @Test
    void formatsToStandardOutputByDefault() throws Exception {
        Path grammar = writeGrammar("Demo.g4", UNFORMATTED_GRAMMAR);
        AntlrFormatCli cli = new AntlrFormatCli();
        StringWriter stdout = new StringWriter();
        CommandLine commandLine = configuredCommandLine(cli, stdout);

        int exitCode = commandLine.execute(grammar.toString());

        assertEquals(0, exitCode);
        assertEquals(expectedOutput(UNFORMATTED_GRAMMAR, new FormattingOptions(), false), stdout.toString());
    }

    @Test
    void writesInPlaceWithTrailingSystemLineSeparator() throws Exception {
        String formattedWithoutTrailingNewline = new GrammarFormatter(UNFORMATTED_GRAMMAR)
            .formatGrammar(new FormattingOptions())
            .text()
            .stripTrailing();
        Path grammar = writeGrammar("InPlace.g4", formattedWithoutTrailingNewline);
        AntlrFormatCli cli = new AntlrFormatCli();
        CommandLine commandLine = configuredCommandLine(cli, new StringWriter());

        int exitCode = commandLine.execute("--write", grammar.toString());

        assertEquals(0, exitCode);
        String actual = Files.readString(grammar, StandardCharsets.UTF_8);
        assertTrue(actual.endsWith(System.lineSeparator()));
        assertEquals(expectedOutput(UNFORMATTED_GRAMMAR, new FormattingOptions(), false), actual);
    }

    @Test
    void writeInPlaceLeavesTimestampUnchangedWhenFormattingIsAlreadyStable() throws Exception {
        String alreadyFormatted = expectedOutput(UNFORMATTED_GRAMMAR, new FormattingOptions(), false);
        Path grammar = writeGrammar("Stable.g4", alreadyFormatted);
        FileTime expectedTimestamp = FileTime.fromMillis(1_700_000_000_000L);
        Files.setLastModifiedTime(grammar, expectedTimestamp);
        AntlrFormatCli cli = new AntlrFormatCli();
        CommandLine commandLine = configuredCommandLine(cli, new StringWriter());

        int exitCode = commandLine.execute("--write", grammar.toString());

        assertEquals(0, exitCode);
        assertEquals(alreadyFormatted, Files.readString(grammar, StandardCharsets.UTF_8));
        assertEquals(expectedTimestamp, Files.getLastModifiedTime(grammar));
    }

    @Test
    void grammarCommentsOverrideCommandLineOptions() throws Exception {
        String grammarText = "// $antlr-format spaceBeforeAssignmentOperators off\ngrammar Demo;\na: value=ID;\n";
        Path grammar = writeGrammar("Override.g4", grammarText);
        AntlrFormatCli cli = new AntlrFormatCli();
        StringWriter stdout = new StringWriter();
        CommandLine commandLine = configuredCommandLine(cli, stdout);

        int exitCode = commandLine.execute("--space-before-assignment-operators", grammar.toString());

        assertEquals(0, exitCode);
        assertTrue(stdout.toString().contains("value=ID"));
        assertFalse(stdout.toString().contains("value = ID"));
    }

    @Test
    void addOptionsCanInjectDirectiveComment() throws Exception {
        Path grammar = writeGrammar("Commented.g4", UNFORMATTED_GRAMMAR);
        AntlrFormatCli cli = new AntlrFormatCli();
        StringWriter stdout = new StringWriter();
        CommandLine commandLine = configuredCommandLine(cli, stdout);

        int exitCode = commandLine.execute("--add-options", grammar.toString());

        assertEquals(0, exitCode);
        assertTrue(stdout.toString().startsWith(GrammarFormatter.convertToComment(FormattingOptions.defaults())));
        assertTrue(stdout.toString().contains("grammar Demo;"));
    }

    @Test
    void everyInlineDirectiveOptionHasACliFlagAndParsesIntoFormattingOptions() throws Exception {
        Path grammar = writeGrammar("AllOptions.g4", UNFORMATTED_GRAMMAR);
        AntlrFormatCli cli = new AntlrFormatCli();
        CommandLine commandLine = configuredCommandLine(cli, new StringWriter());
        List<String> args = optionFields().stream()
            .filter(field -> !"disabled".equals(field.getName()))
            .flatMap(field -> Arrays.stream(cliArgs(field.getName())))
            .collect(java.util.stream.Collectors.toCollection(java.util.ArrayList::new));
        args.add(grammar.toString());

        commandLine.parseArgs(args.toArray(String[]::new));
        FormattingOptions parsed = cli.formattingOptions();

        for (Field field : optionFields()) {
            if ("disabled".equals(field.getName())) {
                continue;
            }
            CliFormattingOptions.class.getDeclaredField(field.getName());
            assertEquals(sampleValue(field.getName()), field.get(parsed), field.getName());
        }
    }

    private CommandLine configuredCommandLine(AntlrFormatCli cli, StringWriter stdout) {
        CommandLine commandLine = new CommandLine(cli);
        commandLine.setOut(new PrintWriter(stdout, true));
        return commandLine;
    }

    private Path writeGrammar(String fileName, String grammarText) throws Exception {
        Path grammar = tempDir.resolve(fileName);
        Files.writeString(grammar, grammarText, StandardCharsets.UTF_8);
        return grammar;
    }

    private static String expectedOutput(String grammar, FormattingOptions options, boolean addOptions) {
        String formatted = new GrammarFormatter(grammar, addOptions).formatGrammar(options).text();
        return FormattingOutputs.normalizeForOutput(formatted);
    }

    private static List<Field> optionFields() {
        return Arrays.stream(FormattingOptions.class.getFields())
            .filter(field -> !Modifier.isStatic(field.getModifiers()))
            .sorted(Comparator.comparing(Field::getName))
            .toList();
    }

    private static Object sampleValue(String optionName) {
        return switch (optionName) {
            case "disabled" -> true;
            case "alignTrailingComments" -> true;
            case "allowShortBlocksOnASingleLine" -> false;
            case "breakBeforeBraces" -> true;
            case "columnLimit" -> 73;
            case "continuationIndentWidth" -> 9;
            case "indentWidth" -> 2;
            case "keepEmptyLinesAtTheStartOfBlocks" -> true;
            case "maxEmptyLinesToKeep" -> 4;
            case "reflowComments" -> true;
            case "spaceBeforeAssignmentOperators" -> false;
            case "tabWidth" -> 8;
            case "useTab" -> true;
            case "alignColons" -> ColonAlignment.HANGING;
            case "singleLineOverrulesHangingColon" -> false;
            case "allowShortRulesOnASingleLine" -> false;
            case "alignSemicolons" -> SemicolonAlignment.HANGING;
            case "breakBeforeParens" -> true;
            case "ruleInternalsOnSingleLine" -> true;
            case "minEmptyLines" -> 3;
            case "groupedAlignments" -> false;
            case "alignFirstTokens" -> true;
            case "alignLexerCommands" -> true;
            case "alignActions" -> true;
            case "alignLabels" -> false;
            case "alignTrailers" -> true;
            default -> throw new IllegalArgumentException("Unhandled option: " + optionName);
        };
    }

    private static String[] cliArgs(String optionName) {
        return switch (optionName) {
            case "alignTrailingComments" -> new String[] { "--align-trailing-comments" };
            case "allowShortBlocksOnASingleLine" -> new String[] { "--no-allow-short-blocks-on-a-single-line" };
            case "breakBeforeBraces" -> new String[] { "--break-before-braces" };
            case "columnLimit" -> new String[] { "--column-limit", "73" };
            case "continuationIndentWidth" -> new String[] { "--continuation-indent-width", "9" };
            case "indentWidth" -> new String[] { "--indent-width", "2" };
            case "keepEmptyLinesAtTheStartOfBlocks" -> new String[] { "--keep-empty-lines-at-the-start-of-blocks" };
            case "maxEmptyLinesToKeep" -> new String[] { "--max-empty-lines-to-keep", "4" };
            case "reflowComments" -> new String[] { "--reflow-comments" };
            case "spaceBeforeAssignmentOperators" -> new String[] { "--no-space-before-assignment-operators" };
            case "tabWidth" -> new String[] { "--tab-width", "8" };
            case "useTab" -> new String[] { "--use-tab" };
            case "alignColons" -> new String[] { "--align-colons", "hanging" };
            case "singleLineOverrulesHangingColon" -> new String[] { "--no-single-line-overrules-hanging-colon" };
            case "allowShortRulesOnASingleLine" -> new String[] { "--no-allow-short-rules-on-a-single-line" };
            case "alignSemicolons" -> new String[] { "--align-semicolons", "hanging" };
            case "breakBeforeParens" -> new String[] { "--break-before-parens" };
            case "ruleInternalsOnSingleLine" -> new String[] { "--rule-internals-on-single-line" };
            case "minEmptyLines" -> new String[] { "--min-empty-lines", "3" };
            case "groupedAlignments" -> new String[] { "--no-grouped-alignments" };
            case "alignFirstTokens" -> new String[] { "--align-first-tokens" };
            case "alignLexerCommands" -> new String[] { "--align-lexer-commands" };
            case "alignActions" -> new String[] { "--align-actions" };
            case "alignLabels" -> new String[] { "--no-align-labels" };
            case "alignTrailers" -> new String[] { "--align-trailers" };
            default -> throw new IllegalArgumentException("Unhandled option: " + optionName);
        };
    }
}


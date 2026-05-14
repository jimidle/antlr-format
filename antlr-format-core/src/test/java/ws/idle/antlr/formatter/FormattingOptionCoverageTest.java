package ws.idle.antlr.formatter;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;

class FormattingOptionCoverageTest {

    @Test
    void everyInlineDirectiveOptionIsParsedAndOverridesIncomingFormattingOptions() throws Exception {
        List<Field> inlineFields = optionFields().stream()
            .filter(field -> !"disabled".equals(field.getName()))
            .toList();

        String directiveComment = "// $antlr-format " + inlineFields.stream()
            .map(field -> field.getName() + " " + inlineDirectiveValue(field.getName()))
            .collect(Collectors.joining(", "));

        FormatterDirectiveParser.ParseResult parseResult = FormatterDirectiveParser.parse(directiveComment);
        assertTrue(parseResult.containsFormattingOptions());
        assertEquals(inlineFields.size(), parseResult.directives().size());
        for (FormatterDirectiveParser.Directive directive : parseResult.directives()) {
            assertFalse(directive instanceof FormatterDirectiveParser.InvalidDirective);
        }

        FormatterDirectiveParser.ParseResult disabledResult = FormatterDirectiveParser.parse("// $antlr-format disabled true");
        assertTrue(disabledResult.containsFormattingOptions());
        assertEquals(1, disabledResult.directives().size());
        assertInstanceOf(FormatterDirectiveParser.InvalidDirective.class, disabledResult.directives().getFirst());

        GrammarFormattingSession session = new GrammarFormattingSession(FormatterTokenStream.fromGrammar(
            directiveComment + "\ngrammar Demo;\na: 'a';\n"), false);
        Method initializeOptions = GrammarFormattingSession.class.getDeclaredMethod("initializeOptions",
            FormattingOptions.class);
        initializeOptions.setAccessible(true);
        initializeOptions.invoke(session, oppositeOverrides());

        Method processFormattingCommands = GrammarFormattingSession.class.getDeclaredMethod("processFormattingCommands",
            int.class);
        processFormattingCommands.setAccessible(true);
        processFormattingCommands.invoke(session, 0);

        Field optionsField = GrammarFormattingSession.class.getDeclaredField("options");
        optionsField.setAccessible(true);
        FormattingOptions options = (FormattingOptions) optionsField.get(session);

        for (Field field : inlineFields) {
            assertEquals(sampleValue(field.getName()), field.get(options), field.getName());
        }
    }

    @Test
    void defaultsAndMergeStayExhaustiveForEveryFormattingOptionField() throws IllegalAccessException {
        FormattingOptions defaults = FormattingOptions.defaults();
        for (Field field : optionFields()) {
            assertNotNull(field.get(defaults), field.getName());
        }

        FormattingOptions overrides = new FormattingOptions();
        for (Field field : optionFields()) {
            field.set(overrides, sampleValue(field.getName()));
        }

        FormattingOptions merged = FormattingOptions.defaults().mergeFrom(overrides);
        for (Field field : optionFields()) {
            assertEquals(sampleValue(field.getName()), field.get(merged), field.getName());
        }
    }

    @Test
    void directiveReferenceDocumentsEveryInlineOptionAndTheDisabledCaveat() throws IOException {
        String docs = Files.readString(findDirectiveReference());

        assertTrue(docs.contains("The following table is intended to be exhaustive"));
        for (Field field : optionFields()) {
            String name = field.getName();
            if (!"disabled".equals(name)) {
                assertTrue(docs.contains("`" + name + "`"), name);
            }
        }

        assertTrue(docs.contains("`disabled` is not a recognized inline option name"));
        assertTrue(docs.contains("// $antlr-format disabled true"));
        assertTrue(docs.contains("inline grammar comments always override the surrounding plugin, CLI, or library configuration"));
        assertTrue(docs.contains("resets the active formatter state to the built-in defaults"));
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

    private static FormattingOptions oppositeOverrides() throws IllegalAccessException {
        FormattingOptions overrides = new FormattingOptions();
        for (Field field : optionFields()) {
            if ("disabled".equals(field.getName())) {
                continue;
            }
            field.set(overrides, oppositeValue(field.getName()));
        }
        return overrides;
    }

    private static Object oppositeValue(String optionName) {
        return switch (optionName) {
            case "alignTrailingComments" -> false;
            case "allowShortBlocksOnASingleLine" -> true;
            case "breakBeforeBraces" -> false;
            case "columnLimit" -> 101;
            case "continuationIndentWidth" -> 2;
            case "indentWidth" -> 6;
            case "keepEmptyLinesAtTheStartOfBlocks" -> false;
            case "maxEmptyLinesToKeep" -> 1;
            case "reflowComments" -> false;
            case "spaceBeforeAssignmentOperators" -> true;
            case "tabWidth" -> 4;
            case "useTab" -> false;
            case "alignColons" -> ColonAlignment.TRAILING;
            case "singleLineOverrulesHangingColon" -> true;
            case "allowShortRulesOnASingleLine" -> true;
            case "alignSemicolons" -> SemicolonAlignment.OWN_LINE;
            case "breakBeforeParens" -> false;
            case "ruleInternalsOnSingleLine" -> false;
            case "minEmptyLines" -> 0;
            case "groupedAlignments" -> true;
            case "alignFirstTokens" -> false;
            case "alignLexerCommands" -> false;
            case "alignActions" -> false;
            case "alignLabels" -> true;
            case "alignTrailers" -> false;
            default -> throw new IllegalArgumentException("Unhandled option: " + optionName);
        };
    }

    private static String inlineDirectiveValue(String optionName) {
        Object value = sampleValue(optionName);
        return switch (value) {
            case Boolean bool -> bool ? "on" : "off";
            case Integer number -> number.toString();
            case ColonAlignment alignment -> switch (alignment) {
                case NONE -> "none";
                case TRAILING -> "trailing";
                case HANGING -> "hanging";
            };
            case SemicolonAlignment alignment -> switch (alignment) {
                case NONE -> "none";
                case OWN_LINE -> "ownLine";
                case HANGING -> "hanging";
            };
            default -> throw new IllegalArgumentException("Unhandled directive value for option: " + optionName);
        };
    }

    private static Path findDirectiveReference() throws IOException {
        Path current = Path.of(System.getProperty("user.dir")).toAbsolutePath();
        while (current != null) {
            Path candidate = current.resolve("docs/formatter-directives.md").normalize();
            if (Files.exists(candidate)) {
                return candidate;
            }
            current = current.getParent();
        }
        throw new IOException("Unable to locate repository file: docs/formatter-directives.md");
    }
}



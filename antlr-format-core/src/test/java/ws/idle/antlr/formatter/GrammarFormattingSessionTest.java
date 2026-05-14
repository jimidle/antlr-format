package ws.idle.antlr.formatter;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.Test;

class GrammarFormattingSessionTest {

    @Test
    void emptyTokenStreamReturnsEmptyFormattingResult() {
        GrammarFormattingSession session = new GrammarFormattingSession(FormatterTokenStream.fromTokens(List.of()), false);

        FormattingResult result = session.format(new FormattingOptions(), 0, 10);

        assertEquals("", result.text());
        assertEquals(-1, result.targetStart());
        assertEquals(-1, result.targetStop());
    }

    @Test
    void injectsFormattingOptionsCommentWhenRequestedAndMissing() {
        String grammar = "grammar Demo;\na:'a';\n";

        FormattingResult result = format(grammar, new FormattingOptions(), true);
        String expectedComment = GrammarFormatter.convertToComment(FormattingOptions.defaults());

        assertTrue(result.text().startsWith(expectedComment));
        assertTrue(result.text().contains("grammar Demo;"));
    }

    @Test
    void existingFormatterDirectiveSuppressesInjectedOptionsComment() {
        String grammar = "// $antlr-format alignLabels off\ngrammar Demo;\na:'a';\n";

        FormattingResult result = format(grammar, new FormattingOptions(), true);

        assertEquals(1, countOccurrences(result.text(), "$antlr-format"));
        assertTrue(result.text().contains("// $antlr-format alignLabels off"));
    }

    @Test
    void invalidFormatterDirectiveProducesVisibleErrorMarker() {
        String grammar = "grammar Demo;\n// $antlr-format alignLabels maybe\na:'a';\n";

        FormattingResult result = format(grammar, new FormattingOptions(), false);

        assertTrue(result.text().contains("<<Unexpected input or wrong formatter command>>"));
        assertTrue(result.text().contains("// $antlr-format alignLabels maybe"));
    }

    @Test
    void formatterOffDirectivePreservesRawSourceUntilFormattingIsReEnabled() {
        String grammar = """
            grammar Demo;
            a:'a';
            // $antlr-format off
            b :    'b'   |'c'  ;
            // $antlr-format on
            c:'d';
            """;

        FormattingResult result = format(grammar, new FormattingOptions(), false);

        assertTrue(result.text().contains("// $antlr-format off\nb :    'b'   |'c'  ;"));
        assertTrue(result.text().contains("// $antlr-format on"));
        assertTrue(result.text().contains("c"));
    }

    @Test
    void breakBeforeBracesMovesKeywordBlockBraceToOwnLine() {
        String grammar = """
            grammar Demo;
            options { superClass = BaseParser; }
            a: 'a';
            """;
        FormattingOptions options = new FormattingOptions();
        options.breakBeforeBraces = true;

        FormattingResult result = format(grammar, options, false);

        assertTrue(result.text().contains("options\n{"));
        assertFalse(result.text().contains("options {"));
    }

    @Test
    void breakBeforeBracesMovesTopLevelNamedActionBraceToOwnLine() {
        String grammar = """
            grammar Demo;
            @parser::members {int value() { return 1; }}
            a: 'a';
            """;
        FormattingOptions options = new FormattingOptions();
        options.breakBeforeBraces = true;

        FormattingResult result = format(grammar, options, false);

        assertTrue(result.text().contains("@parser::members\n{"));
        assertFalse(result.text().contains("@parser::members {"));
    }

    @Test
    void breakBeforeBracesDoesNotMoveInlineRuleActionBrace() {
        String grammar = """
            grammar Demo;
            a: 'a' {doIt();};
            """;
        FormattingOptions options = new FormattingOptions();
        options.breakBeforeBraces = true;

        FormattingResult result = format(grammar, options, false);

        assertTrue(result.text().contains("'a' {doIt();}"));
        assertFalse(result.text().contains("'a'\n{"));
    }

    private static FormattingResult format(String grammar, FormattingOptions options, boolean addOptionsAsComment) {
        return new GrammarFormattingSession(FormatterTokenStream.fromGrammar(grammar), addOptionsAsComment)
            .format(options, 0, Integer.MAX_VALUE);
    }

    private static int countOccurrences(String text, String needle) {
        int count = 0;
        int index = 0;
        while ((index = text.indexOf(needle, index)) >= 0) {
            ++count;
            index += needle.length();
        }
        return count;
    }
}


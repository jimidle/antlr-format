package ws.idle.antlr.formatter;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class GrammarFormatterSmokeTest {

    @Test
    void detectsLexerGrammar() {
        String grammar = "lexer grammar Demo;\nA: 'a';\n";
        assertTrue(GrammarKindDetector.isLexerGrammar(grammar));
    }

    @Test
    void disabledOptionReturnsEmptyRange() {
        String grammar = "grammar Demo;\na: 'a';\n";
        FormattingOptions options = new FormattingOptions();
        options.disabled = true;

        FormattingResult result = new GrammarFormatter(grammar).formatGrammar(options, 0, Integer.MAX_VALUE);
        assertEquals("", result.text());
        assertEquals(-1, result.targetStart());
        assertEquals(-1, result.targetStop());
    }

    @Test
    void emitsFormattingCommentText() {
        FormattingOptions options = new FormattingOptions();
        options.columnLimit = 150;
        options.alignLabels = true;

        String comment = GrammarFormatter.convertToComment(options);
        assertTrue(comment.contains("$antlr-format"));
        assertTrue(comment.contains("columnLimit 150"));
        assertTrue(comment.contains("alignLabels true"));
    }
}


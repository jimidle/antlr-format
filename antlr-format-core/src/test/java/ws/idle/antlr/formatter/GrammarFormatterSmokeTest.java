package ws.idle.antlr.formatter;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.antlr.v4.runtime.CharStreams;
import org.antlr.v4.runtime.CommonTokenStream;
import org.junit.jupiter.api.Test;
import ws.idle.antlr.formatter.lexer.ANTLRv4Lexer;

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

    @Test
    void supportsTokenStreamConstructorAndConvenienceOverload() {
        String grammar = "grammar Demo;\na: 'a';\n";
        ANTLRv4Lexer lexer = new ANTLRv4Lexer(CharStreams.fromString(grammar));
        CommonTokenStream tokenStream = new CommonTokenStream(lexer);
        tokenStream.fill();

        FormattingOptions options = new FormattingOptions();
        FormattingResult fromTokens = new GrammarFormatter(tokenStream.getTokens()).formatGrammar(options);
        FormattingResult fromText = new GrammarFormatter(grammar).formatGrammar(options);

        assertEquals(fromText.text(), fromTokens.text());
    }
}


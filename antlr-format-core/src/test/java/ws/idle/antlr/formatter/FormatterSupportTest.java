package ws.idle.antlr.formatter;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.antlr.v4.runtime.CharStreams;
import org.antlr.v4.runtime.CommonTokenStream;
import org.junit.jupiter.api.Test;
import ws.idle.antlr.formatter.lexer.ANTLRv4Lexer;

class FormatterSupportTest {

    @Test
    void tokenStreamsBuiltFromTextAndTokensExposeEquivalentMetadata() {
        String grammar = "grammar Demo;\n// comment\na:'a';\n";

        FormatterTokenStream fromGrammar = FormatterTokenStream.fromGrammar(grammar);

        ANTLRv4Lexer lexer = new ANTLRv4Lexer(CharStreams.fromString(grammar));
        CommonTokenStream tokenStream = new CommonTokenStream(lexer);
        tokenStream.fill();
        FormatterTokenStream fromTokens = FormatterTokenStream.fromTokens(tokenStream.getTokens());

        assertEquals(grammar, fromGrammar.sourceText());
        assertEquals(grammar, fromTokens.sourceText());
        assertEquals(fromGrammar.size(), fromTokens.size());

        int ruleIndex = grammar.indexOf("a:'a'");
        int fromGrammarTokenIndex = fromGrammar.tokenIndexForCharIndex(ruleIndex, true);
        int fromTokensTokenIndex = fromTokens.tokenIndexForCharIndex(ruleIndex, true);

        assertEquals(fromGrammar.type(fromGrammarTokenIndex), fromTokens.type(fromTokensTokenIndex));
        assertEquals(ANTLRv4Lexer.RULE_REF, fromGrammar.type(fromGrammarTokenIndex));
    }

    @Test
    void commentHelperReflowsLongLineComments() {
        FormattingOptions options = FormattingOptions.defaults();
        options.columnLimit = 24;

        String comment = "// this comment should wrap nicely";
        String reflowed = FormatterComments.reflowComment(comment, ANTLRv4Lexer.LINE_COMMENT, options, 0, 0);

        assertTrue(reflowed.contains("\n// "));
    }
}


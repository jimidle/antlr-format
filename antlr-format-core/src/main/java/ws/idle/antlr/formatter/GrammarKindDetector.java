package ws.idle.antlr.formatter;

import org.antlr.v4.runtime.CharStreams;
import org.antlr.v4.runtime.CommonTokenStream;
import org.antlr.v4.runtime.Token;
import ws.idle.antlr.formatter.lexer.ANTLRv4Lexer;

/** Detects grammar kind using the first default-channel token strategy. */
public final class GrammarKindDetector {

    /** Prevents instantiation of the utility class. */
    private GrammarKindDetector() {
    }

    /**
     * Determines whether the supplied grammar is a lexer grammar.
     *
     * <p>The implementation mirrors the original formatter's lightweight approach: it lexes the input and inspects
     * the first token on the default channel.
     *
     * @param grammarText the grammar text to inspect
     * @return {@code true} when the first default-channel token is {@code lexer}, otherwise {@code false}
     */
    public static boolean isLexerGrammar(String grammarText) {
        ANTLRv4Lexer lexer = new ANTLRv4Lexer(CharStreams.fromString(grammarText));
        lexer.removeErrorListeners();
        CommonTokenStream tokenStream = new CommonTokenStream(lexer);
        tokenStream.fill();

        for (Token token : tokenStream.getTokens()) {
            if (token.getChannel() == Token.DEFAULT_CHANNEL) {
                return "lexer".equals(token.getText());
            }
        }

        return false;
    }
}


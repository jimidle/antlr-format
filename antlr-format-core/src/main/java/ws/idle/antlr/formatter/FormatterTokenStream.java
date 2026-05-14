package ws.idle.antlr.formatter;

import java.util.List;
import org.antlr.v4.runtime.CharStream;
import org.antlr.v4.runtime.CharStreams;
import org.antlr.v4.runtime.CommonToken;
import org.antlr.v4.runtime.CommonTokenStream;
import org.antlr.v4.runtime.Token;
import org.antlr.v4.runtime.misc.Interval;
import ws.idle.antlr.formatter.lexer.ANTLRv4Lexer;

final class FormatterTokenStream {

    private final List<Token> tokens;
    private final String sourceText;

    /**
     * Creates a token stream wrapper around immutable token and source-text state.
     *
     * @param tokens the tokens to wrap
     * @param sourceText the source text that produced the tokens
     */
    private FormatterTokenStream(List<Token> tokens, String sourceText) {
        this.tokens = tokens;
        this.sourceText = sourceText;
    }

    /**
     * Creates a token stream helper by lexing raw grammar text.
     *
     * @param grammar the grammar text to tokenize
     * @return a token stream wrapper backed by the supplied grammar text
     */
    static FormatterTokenStream fromGrammar(String grammar) {
        return new FormatterTokenStream(tokenize(grammar), grammar);
    }

    /**
     * Creates a token stream helper from an existing token list.
     *
     * @param tokens the token list to wrap
     * @return a token stream wrapper backed by the supplied tokens
     */
    static FormatterTokenStream fromTokens(List<Token> tokens) {
        List<Token> tokenList = List.copyOf(tokens);
        return new FormatterTokenStream(tokenList, extractSource(tokenList));
    }

    /** @return whether the wrapped token list is empty. */
    boolean isEmpty() {
        return tokens.isEmpty();
    }

    /** @return the number of wrapped tokens. */
    int size() {
        return tokens.size();
    }

    /**
     * Returns the token at the requested token index.
     *
     * @param index the token index
     * @return the token at that position
     */
    Token token(int index) {
        return tokens.get(index);
    }

    /**
     * Returns the ANTLR token type at the requested index.
     *
     * @param index the token index
     * @return the token type
     */
    int type(int index) {
        return token(index).getType();
    }

    /**
     * Returns the source line for the token at the requested index.
     *
     * @param index the token index
     * @return the 1-based source line
     */
    int line(int index) {
        return token(index).getLine();
    }

    /**
     * Returns the source column for the token at the requested index.
     *
     * @param index the token index
     * @return the 0-based source column
     */
    int column(int index) {
        return token(index).getCharPositionInLine();
    }

    /**
     * Returns the inclusive character start index for the token at the requested index.
     *
     * @param index the token index
     * @return the inclusive start index
     */
    int start(int index) {
        return token(index).getStartIndex();
    }

    /**
     * Returns the inclusive character stop index for the token at the requested index.
     *
     * @param index the token index
     * @return the inclusive stop index
     */
    int stop(int index) {
        return token(index).getStopIndex();
    }

    /** @return the full source text associated with the wrapped token stream. */
    String sourceText() {
        return sourceText;
    }

    /**
     * Returns a substring from the wrapped source text.
     *
     * @param startInclusive the inclusive start character index
     * @param stopInclusive the inclusive stop character index
     * @return the extracted source slice
     */
    String sourceSlice(int startInclusive, int stopInclusive) {
        return sourceText.substring(startInclusive, stopInclusive + 1);
    }

    /**
     * Maps a source character index to the closest token index.
     *
     * @param charIndex the source character index
     * @param first whether to bias toward the first token on the matched source line
     * @return the resolved token index
     */
    int tokenIndexForCharIndex(int charIndex, boolean first) {
        if (charIndex < 0) {
            return 0;
        }
        if (charIndex >= sourceText.length()) {
            return tokens.size() - 1;
        }
        for (int i = 0; i < tokens.size(); ++i) {
            Token token = tokens.get(i);
            if (token.getStartIndex() > charIndex) {
                if (i == 0) {
                    return i;
                }
                --i;
                if (!first) {
                    return i;
                }
                int row = tokens.get(i).getLine();
                while (i > 0 && tokens.get(i - 1).getLine() == row) {
                    --i;
                }
                return i;
            }
        }
        return tokens.size() - 1;
    }

    /**
     * Safely returns token text, replacing {@code null} token text with an empty string.
     *
     * @param token the token to inspect
     * @return the token text or an empty string
     */
    static String text(Token token) {
        return token.getText() == null ? "" : token.getText();
    }

    /**
     * Tokenizes raw grammar text using the ANTLR v4 lexer grammar.
     *
     * @param grammar the grammar text to tokenize
     * @return an immutable list of tokens
     */
    private static List<Token> tokenize(String grammar) {
        ANTLRv4Lexer lexer = new ANTLRv4Lexer(CharStreams.fromString(grammar));
        lexer.removeErrorListeners();
        CommonTokenStream tokenStream = new CommonTokenStream(lexer);
        tokenStream.fill();
        return List.copyOf(tokenStream.getTokens());
    }

    /**
     * Reconstructs the original source text from a token list.
     *
     * @param tokens the token list to inspect
     * @return the original source text when available, otherwise a concatenation of token text
     */
    private static String extractSource(List<Token> tokens) {
        if (tokens.isEmpty()) {
            return "";
        }
        Token first = tokens.getFirst();
        if (first instanceof CommonToken commonToken) {
            CharStream inputStream = commonToken.getInputStream();
            if (inputStream != null && inputStream.size() > 0) {
                return inputStream.getText(Interval.of(0, inputStream.size() - 1));
            }
        }
        StringBuilder builder = new StringBuilder();
        for (Token token : tokens) {
            if (token.getType() != Token.EOF) {
                builder.append(text(token));
            }
        }
        return builder.toString();
    }
}


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

    private FormatterTokenStream(List<Token> tokens, String sourceText) {
        this.tokens = tokens;
        this.sourceText = sourceText;
    }

    static FormatterTokenStream fromGrammar(String grammar) {
        return new FormatterTokenStream(tokenize(grammar), grammar);
    }

    static FormatterTokenStream fromTokens(List<Token> tokens) {
        List<Token> tokenList = List.copyOf(tokens);
        return new FormatterTokenStream(tokenList, extractSource(tokenList));
    }

    boolean isEmpty() {
        return tokens.isEmpty();
    }

    int size() {
        return tokens.size();
    }

    Token token(int index) {
        return tokens.get(index);
    }

    int type(int index) {
        return token(index).getType();
    }

    int line(int index) {
        return token(index).getLine();
    }

    int column(int index) {
        return token(index).getCharPositionInLine();
    }

    int start(int index) {
        return token(index).getStartIndex();
    }

    int stop(int index) {
        return token(index).getStopIndex();
    }

    String sourceText() {
        return sourceText;
    }

    String sourceSlice(int startInclusive, int stopInclusive) {
        return sourceText.substring(startInclusive, stopInclusive + 1);
    }

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

    static String text(Token token) {
        return token.getText() == null ? "" : token.getText();
    }

    private static List<Token> tokenize(String grammar) {
        ANTLRv4Lexer lexer = new ANTLRv4Lexer(CharStreams.fromString(grammar));
        lexer.removeErrorListeners();
        CommonTokenStream tokenStream = new CommonTokenStream(lexer);
        tokenStream.fill();
        return List.copyOf(tokenStream.getTokens());
    }

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


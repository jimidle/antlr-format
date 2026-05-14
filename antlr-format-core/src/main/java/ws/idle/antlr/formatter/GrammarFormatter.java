package ws.idle.antlr.formatter;

import java.util.List;
import org.antlr.v4.runtime.Token;

/**
 * Public formatter façade. The mutable formatting algorithm lives in
 * {@link GrammarFormattingSession}, while this type keeps the user-facing API compact.
 */
public final class GrammarFormatter {

    private final FormatterTokenStream tokenStream;
    private final boolean addOptionsAsComment;

    public GrammarFormatter(String grammar) {
        this(grammar, false);
    }

    public GrammarFormatter(String grammar, boolean addOptionsAsComment) {
        this(FormatterTokenStream.fromGrammar(grammar), addOptionsAsComment);
    }

    public GrammarFormatter(List<Token> tokens) {
        this(tokens, false);
    }

    public GrammarFormatter(List<Token> tokens, boolean addOptionsAsComment) {
        this(FormatterTokenStream.fromTokens(tokens), addOptionsAsComment);
    }

    private GrammarFormatter(FormatterTokenStream tokenStream, boolean addOptionsAsComment) {
        this.tokenStream = tokenStream;
        this.addOptionsAsComment = addOptionsAsComment;
    }

    public static String convertToComment(FormattingOptions options) {
        return FormatterComments.convertToComment(options);
    }

    public FormattingResult formatGrammar(FormattingOptions options, Integer start, Integer stop) {
        return new GrammarFormattingSession(tokenStream, addOptionsAsComment).format(options, start, stop);
    }

    public FormattingResult formatGrammar(FormattingOptions options) {
        return formatGrammar(options, 0, Integer.MAX_VALUE);
    }
}

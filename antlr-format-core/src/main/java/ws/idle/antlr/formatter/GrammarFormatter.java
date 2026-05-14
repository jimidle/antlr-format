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

    /**
     * Creates a formatter from raw grammar text.
     *
     * @param grammar the grammar text to format
     */
    public GrammarFormatter(String grammar) {
        this(grammar, false);
    }

    /**
     * Creates a formatter from raw grammar text.
     *
     * @param grammar the grammar text to format
     * @param addOptionsAsComment whether to inject the effective options as an {@code $antlr-format} comment
     */
    public GrammarFormatter(String grammar, boolean addOptionsAsComment) {
        this(FormatterTokenStream.fromGrammar(grammar), addOptionsAsComment);
    }

    /**
     * Creates a formatter from a pre-tokenized ANTLR token list.
     *
     * @param tokens the ANTLR tokens representing the grammar text
     */
    public GrammarFormatter(List<Token> tokens) {
        this(tokens, false);
    }

    /**
     * Creates a formatter from a pre-tokenized ANTLR token list.
     *
     * @param tokens the ANTLR tokens representing the grammar text
     * @param addOptionsAsComment whether to inject the effective options as an {@code $antlr-format} comment
     */
    public GrammarFormatter(List<Token> tokens, boolean addOptionsAsComment) {
        this(FormatterTokenStream.fromTokens(tokens), addOptionsAsComment);
    }

    /**
     * Creates the formatter façade from an already prepared token stream abstraction.
     *
     * @param tokenStream the wrapped token stream and source text
     * @param addOptionsAsComment whether to inject the effective options as an {@code $antlr-format} comment
     */
    private GrammarFormatter(FormatterTokenStream tokenStream, boolean addOptionsAsComment) {
        this.tokenStream = tokenStream;
        this.addOptionsAsComment = addOptionsAsComment;
    }

    /**
     * Serializes a formatter options object into an {@code $antlr-format} comment block.
     *
     * @param options the options to serialize
     * @return the generated formatter directive comment
     */
    public static String convertToComment(FormattingOptions options) {
        return FormatterComments.convertToComment(options);
    }

    /**
     * Formats the configured grammar, optionally restricted to a source range.
     *
     * @param options the formatting options to apply
     * @param start the inclusive source character index to begin formatting from
     * @param stop the inclusive source character index to stop formatting at
     * @return the formatted text and the adjusted replacement range
     */
    public FormattingResult formatGrammar(FormattingOptions options, Integer start, Integer stop) {
        return new GrammarFormattingSession(tokenStream, addOptionsAsComment).format(options, start, stop);
    }

    /**
     * Formats the entire configured grammar.
     *
     * @param options the formatting options to apply
     * @return the formatted text and the adjusted replacement range
     */
    public FormattingResult formatGrammar(FormattingOptions options) {
        return formatGrammar(options, 0, Integer.MAX_VALUE);
    }
}

package ws.idle.antlr.formatter;

/** High-level entrypoint for callers that need grammar-kind-aware option selection. */
public final class AntlrFormatterService {

    /**
     * Formats a grammar using the main or lexer-specific option set selected from the supplied configuration.
     *
     * @param grammar the grammar text to format
     * @param configuration the configuration that supplies the main and optional lexer overrides
     * @param addOptionsAsComment whether the resolved options should be emitted as an {@code $antlr-format} comment
     * @param start the inclusive source character index to begin formatting from, or {@code null} for the beginning
     * @param stop the inclusive source character index to stop formatting at, or {@code null} for the end
     * @return the formatted text and the adjusted replacement range
     */
    public FormattingResult format(String grammar, FormattingConfiguration configuration, boolean addOptionsAsComment,
                                   Integer start, Integer stop) {
        boolean lexerGrammar = GrammarKindDetector.isLexerGrammar(grammar);
        FormattingOptions selected = configuration.resolveForLexerGrammar(lexerGrammar);
        GrammarFormatter formatter = new GrammarFormatter(grammar, addOptionsAsComment);
        return formatter.formatGrammar(selected, start, stop);
    }
}


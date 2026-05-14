package ws.idle.antlr.formatter;

/** High-level entrypoint for callers that need grammar-kind-aware option selection. */
public final class AntlrFormatterService {

    public FormattingResult format(String grammar, FormattingConfiguration configuration, boolean addOptionsAsComment,
                                   Integer start, Integer stop) {
        boolean lexerGrammar = GrammarKindDetector.isLexerGrammar(grammar);
        FormattingOptions selected = configuration.resolveForLexerGrammar(lexerGrammar);
        GrammarFormatter formatter = new GrammarFormatter(grammar, addOptionsAsComment);
        return formatter.formatGrammar(selected, start, stop);
    }
}


package ws.idle.antlr.formatter;

/** Configuration with main and optional lexer-only option sets. */
public final class FormattingConfiguration {

    public FormattingOptions main = new FormattingOptions();
    public FormattingOptions lexer;

    /**
     * Resolves the effective option set for the detected grammar kind.
     *
     * @param isLexerGrammar whether the grammar being formatted is a lexer grammar
     * @return the lexer-specific options when available for lexer grammars, otherwise {@link #main}
     */
    public FormattingOptions resolveForLexerGrammar(boolean isLexerGrammar) {
        if (isLexerGrammar && lexer != null) {
            return lexer;
        }
        return main;
    }
}


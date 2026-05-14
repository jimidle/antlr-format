package ws.idle.antlr.formatter;

/** Configuration with main and optional lexer-only option sets. */
public final class FormattingConfiguration {

    public FormattingOptions main = new FormattingOptions();
    public FormattingOptions lexer;

    public FormattingOptions resolveForLexerGrammar(boolean isLexerGrammar) {
        if (isLexerGrammar && lexer != null) {
            return lexer;
        }
        return main;
    }
}


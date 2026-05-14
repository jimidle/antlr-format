package ws.idle.antlr.formatter.lexer;

import org.antlr.v4.runtime.CharStream;
import org.antlr.v4.runtime.Lexer;
import org.antlr.v4.runtime.Token;

/**
 * Lexer adaptor used by the generated ANTLR lexer.
 */
public abstract class LexerAdaptor extends Lexer {

    private static final int PREQUEL_CONSTRUCT = -10;
    private static final int OPTIONS_CONSTRUCT = -11;

    private int currentRuleType = Token.INVALID_TYPE;

    /**
     * Creates the adaptor for a generated lexer instance.
     *
     * @param input the input character stream
     */
    protected LexerAdaptor(CharStream input) {
        super(input);
    }

    /** Resets the lexer and clears the tracked rule-type state. */
    @Override
    public void reset() {
        currentRuleType = Token.INVALID_TYPE;
        super.reset();
    }

    /**
     * Emits a token while maintaining the additional state needed by the ANTLR v4 lexer grammar.
     *
     * @return the emitted token
     */
    @Override
    public Token emit() {
        if ((getType() == ANTLRv4Lexer.OPTIONS || getType() == ANTLRv4Lexer.TOKENS || getType() == ANTLRv4Lexer.CHANNELS)
            && currentRuleType == Token.INVALID_TYPE) {
            currentRuleType = PREQUEL_CONSTRUCT;
        } else if (getType() == ANTLRv4Lexer.OPTIONS && currentRuleType == ANTLRv4Lexer.TOKEN_REF) {
            currentRuleType = OPTIONS_CONSTRUCT;
        } else if (getType() == ANTLRv4Lexer.RBRACE && currentRuleType == PREQUEL_CONSTRUCT) {
            currentRuleType = Token.INVALID_TYPE;
        } else if (getType() == ANTLRv4Lexer.RBRACE && currentRuleType == OPTIONS_CONSTRUCT) {
            currentRuleType = ANTLRv4Lexer.TOKEN_REF;
        } else if (getType() == ANTLRv4Lexer.AT && currentRuleType == Token.INVALID_TYPE) {
            currentRuleType = ANTLRv4Lexer.AT;
        } else if (getType() == ANTLRv4Lexer.SEMI && currentRuleType == OPTIONS_CONSTRUCT) {
            // ';' in options block, ignore.
        } else if (getType() == ANTLRv4Lexer.END_ACTION && currentRuleType == ANTLRv4Lexer.AT) {
            currentRuleType = Token.INVALID_TYPE;
        } else if (getType() == ANTLRv4Lexer.ID) {
            String firstChar = _input.getText(org.antlr.v4.runtime.misc.Interval.of(_tokenStartCharIndex, _tokenStartCharIndex));
            char c = firstChar.charAt(0);
            if (Character.isUpperCase(c)) {
                setType(ANTLRv4Lexer.TOKEN_REF);
            } else {
                setType(ANTLRv4Lexer.RULE_REF);
            }

            if (currentRuleType == Token.INVALID_TYPE) {
                currentRuleType = getType();
            }
        } else if (getType() == ANTLRv4Lexer.SEMI) {
            currentRuleType = Token.INVALID_TYPE;
        }

        return super.emit();
    }

    /** Enters the appropriate lexer mode for an argument or lexer character set. */
    protected void handleBeginArgument() {
        if (currentRuleType == ANTLRv4Lexer.TOKEN_REF) {
            pushMode(ANTLRv4Lexer.LexerCharSet);
            more();
        } else {
            pushMode(ANTLRv4Lexer.Argument);
        }
    }

    /** Leaves the current argument mode and rewrites nested content tokens when needed. */
    protected void handleEndArgument() {
        popMode();
        if (!_modeStack.isEmpty()) {
            setType(ANTLRv4Lexer.ARGUMENT_CONTENT);
        }
    }

    /** Restores action-mode state and rewrites nested action terminators when needed. */
    protected void handleEndAction() {
        int oldMode = _mode;
        int newMode = popMode();
        boolean isActionWithinAction = !_modeStack.isEmpty()
            && newMode == ANTLRv4Lexer.TargetLanguageAction
            && oldMode == newMode;

        if (isActionWithinAction) {
            setType(ANTLRv4Lexer.ACTION_CONTENT);
        }
    }
}


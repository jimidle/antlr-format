package ws.idle.antlr.formatter;

/**
 * Formatting options for the formatter API.
 * Wrapper types are used so callers can pass sparse option sets.
 */
public final class FormattingOptions {

    public Boolean disabled;
    public Boolean alignTrailingComments;
    public Boolean allowShortBlocksOnASingleLine;
    public Boolean breakBeforeBraces;
    public Integer columnLimit;
    public Integer continuationIndentWidth;
    public Integer indentWidth;
    public Boolean keepEmptyLinesAtTheStartOfBlocks;
    public Integer maxEmptyLinesToKeep;
    public Boolean reflowComments;
    public Boolean spaceBeforeAssignmentOperators;
    public Integer tabWidth;
    public Boolean useTab;
    public ColonAlignment alignColons;
    public Boolean singleLineOverrulesHangingColon;
    public Boolean allowShortRulesOnASingleLine;
    public SemicolonAlignment alignSemicolons;
    public Boolean breakBeforeParens;
    public Boolean ruleInternalsOnSingleLine;
    public Integer minEmptyLines;
    public Boolean groupedAlignments;
    public Boolean alignFirstTokens;
    public Boolean alignLexerCommands;
    public Boolean alignActions;
    public Boolean alignLabels;
    public Boolean alignTrailers;

    public static FormattingOptions defaults() {
        FormattingOptions options = new FormattingOptions();
        options.disabled = false;
        options.alignTrailingComments = false;
        options.allowShortBlocksOnASingleLine = true;
        options.breakBeforeBraces = false;
        options.columnLimit = 100;
        options.indentWidth = 4;
        options.continuationIndentWidth = 4;
        options.keepEmptyLinesAtTheStartOfBlocks = false;
        options.maxEmptyLinesToKeep = 1;
        options.reflowComments = false;
        options.spaceBeforeAssignmentOperators = true;
        options.tabWidth = 4;
        options.useTab = false;
        options.alignColons = ColonAlignment.NONE;
        options.singleLineOverrulesHangingColon = true;
        options.allowShortRulesOnASingleLine = true;
        options.alignSemicolons = SemicolonAlignment.OWN_LINE;
        options.breakBeforeParens = false;
        options.ruleInternalsOnSingleLine = false;
        options.minEmptyLines = 0;
        options.groupedAlignments = true;
        options.alignFirstTokens = false;
        options.alignLexerCommands = false;
        options.alignActions = false;
        options.alignLabels = true;
        options.alignTrailers = false;
        return options;
    }

    public FormattingOptions mergeFrom(FormattingOptions other) {
        if (other == null) {
            return this;
        }

        disabled = orElse(other.disabled, disabled);
        alignTrailingComments = orElse(other.alignTrailingComments, alignTrailingComments);
        allowShortBlocksOnASingleLine = orElse(other.allowShortBlocksOnASingleLine, allowShortBlocksOnASingleLine);
        breakBeforeBraces = orElse(other.breakBeforeBraces, breakBeforeBraces);
        columnLimit = orElse(other.columnLimit, columnLimit);
        continuationIndentWidth = orElse(other.continuationIndentWidth, continuationIndentWidth);
        indentWidth = orElse(other.indentWidth, indentWidth);
        keepEmptyLinesAtTheStartOfBlocks = orElse(other.keepEmptyLinesAtTheStartOfBlocks,
            keepEmptyLinesAtTheStartOfBlocks);
        maxEmptyLinesToKeep = orElse(other.maxEmptyLinesToKeep, maxEmptyLinesToKeep);
        reflowComments = orElse(other.reflowComments, reflowComments);
        spaceBeforeAssignmentOperators = orElse(other.spaceBeforeAssignmentOperators, spaceBeforeAssignmentOperators);
        tabWidth = orElse(other.tabWidth, tabWidth);
        useTab = orElse(other.useTab, useTab);
        alignColons = orElse(other.alignColons, alignColons);
        singleLineOverrulesHangingColon = orElse(other.singleLineOverrulesHangingColon, singleLineOverrulesHangingColon);
        allowShortRulesOnASingleLine = orElse(other.allowShortRulesOnASingleLine, allowShortRulesOnASingleLine);
        alignSemicolons = orElse(other.alignSemicolons, alignSemicolons);
        breakBeforeParens = orElse(other.breakBeforeParens, breakBeforeParens);
        ruleInternalsOnSingleLine = orElse(other.ruleInternalsOnSingleLine, ruleInternalsOnSingleLine);
        minEmptyLines = orElse(other.minEmptyLines, minEmptyLines);
        groupedAlignments = orElse(other.groupedAlignments, groupedAlignments);
        alignFirstTokens = orElse(other.alignFirstTokens, alignFirstTokens);
        alignLexerCommands = orElse(other.alignLexerCommands, alignLexerCommands);
        alignActions = orElse(other.alignActions, alignActions);
        alignLabels = orElse(other.alignLabels, alignLabels);
        alignTrailers = orElse(other.alignTrailers, alignTrailers);
        return this;
    }

    private static <T> T orElse(T lhs, T rhs) {
        return lhs != null ? lhs : rhs;
    }
}


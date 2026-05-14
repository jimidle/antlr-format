package ws.idle.antlr.formatter.cli;

import picocli.CommandLine;
import ws.idle.antlr.formatter.ColonAlignment;
import ws.idle.antlr.formatter.FormattingOptions;
import ws.idle.antlr.formatter.SemicolonAlignment;

/**
 * Picocli mixin exposing formatter options that correspond to inline {@code $antlr-format} directives.
 *
 * <p>Every option in this class maps directly to a field in {@link FormattingOptions}, except for
 * the API-only {@code disabled} flag which is intentionally omitted from inline directives and from
 * the command line interface.</p>
 */
final class CliFormattingOptions {

    @CommandLine.Option(names = "--align-trailing-comments", negatable = true,
        description = "Align trailing line comments after code.")
    private Boolean alignTrailingComments;

    @CommandLine.Option(names = "--allow-short-blocks-on-a-single-line", negatable = true,
        description = "Allow compact blocks and alternatives to remain on one line.")
    private Boolean allowShortBlocksOnASingleLine;

    @CommandLine.Option(names = "--break-before-braces", negatable = true,
        description = "Move supported block-opening braces onto the next line.")
    private Boolean breakBeforeBraces;

    @CommandLine.Option(names = "--column-limit", paramLabel = "<int>",
        description = "Set the soft wrapping column limit.")
    private Integer columnLimit;

    @CommandLine.Option(names = "--continuation-indent-width", paramLabel = "<int>",
        description = "Set the indentation width for wrapped lines.")
    private Integer continuationIndentWidth;

    @CommandLine.Option(names = "--indent-width", paramLabel = "<int>",
        description = "Set the indentation width used when tabs are disabled.")
    private Integer indentWidth;

    @CommandLine.Option(names = "--keep-empty-lines-at-the-start-of-blocks", negatable = true,
        description = "Preserve blank lines at the start of blocks.")
    private Boolean keepEmptyLinesAtTheStartOfBlocks;

    @CommandLine.Option(names = "--max-empty-lines-to-keep", paramLabel = "<int>",
        description = "Limit the number of consecutive blank lines retained by the formatter.")
    private Integer maxEmptyLinesToKeep;

    @CommandLine.Option(names = "--reflow-comments", negatable = true,
        description = "Reflow comment text to fit within the column limit.")
    private Boolean reflowComments;

    @CommandLine.Option(names = "--space-before-assignment-operators", negatable = true,
        description = "Control whether spaces are inserted before '=' and '+='.")
    private Boolean spaceBeforeAssignmentOperators;

    @CommandLine.Option(names = "--tab-width", paramLabel = "<int>",
        description = "Set the tab width used for alignment and wrapping calculations.")
    private Integer tabWidth;

    @CommandLine.Option(names = "--use-tab", negatable = true,
        description = "Use tab characters for indentation and alignment blocks.")
    private Boolean useTab;

    @CommandLine.Option(names = "--align-colons", converter = ColonAlignmentConverter.class,
        paramLabel = "<none|trailing|hanging>", description = "Set the colon alignment mode.")
    private ColonAlignment alignColons;

    @CommandLine.Option(names = "--single-line-overrules-hanging-colon", negatable = true,
        description = "Allow short rules to remain on one line despite hanging colon preferences.")
    private Boolean singleLineOverrulesHangingColon;

    @CommandLine.Option(names = "--allow-short-rules-on-a-single-line", negatable = true,
        description = "Allow short rules to remain on one line.")
    private Boolean allowShortRulesOnASingleLine;

    @CommandLine.Option(names = "--align-semicolons", converter = SemicolonAlignmentConverter.class,
        paramLabel = "<none|ownLine|hanging>", description = "Set the semicolon alignment mode.")
    private SemicolonAlignment alignSemicolons;

    @CommandLine.Option(names = "--break-before-parens", negatable = true,
        description = "Prefer a line break before parenthesized blocks.")
    private Boolean breakBeforeParens;

    @CommandLine.Option(names = "--rule-internals-on-single-line", negatable = true,
        description = "Keep eligible rule-internal clauses inline when possible.")
    private Boolean ruleInternalsOnSingleLine;

    @CommandLine.Option(names = "--min-empty-lines", paramLabel = "<int>",
        description = "Require at least this many blank lines after top-level constructs.")
    private Integer minEmptyLines;

    @CommandLine.Option(names = "--grouped-alignments", negatable = true,
        description = "Reset alignment groups across line-number gaps.")
    private Boolean groupedAlignments;

    @CommandLine.Option(names = "--align-first-tokens", negatable = true,
        description = "Align the first token after the colon in short single-line rules.")
    private Boolean alignFirstTokens;

    @CommandLine.Option(names = "--align-lexer-commands", negatable = true,
        description = "Align lexer commands introduced by '->'.")
    private Boolean alignLexerCommands;

    @CommandLine.Option(names = "--align-actions", negatable = true,
        description = "Align trailer actions.")
    private Boolean alignActions;

    @CommandLine.Option(names = "--align-labels", negatable = true,
        description = "Align multi-line rule labels introduced by '#'.")
    private Boolean alignLabels;

    @CommandLine.Option(names = "--align-trailers", negatable = true,
        description = "Enable umbrella trailer alignment.")
    private Boolean alignTrailers;

    /**
     * Converts the parsed command line options into the sparse formatter options model used by the core formatter.
     *
     * @return a sparse {@link FormattingOptions} instance containing only the explicitly supplied CLI overrides
     */
    FormattingOptions toFormattingOptions() {
        FormattingOptions options = new FormattingOptions();
        options.alignTrailingComments = alignTrailingComments;
        options.allowShortBlocksOnASingleLine = allowShortBlocksOnASingleLine;
        options.breakBeforeBraces = breakBeforeBraces;
        options.columnLimit = columnLimit;
        options.continuationIndentWidth = continuationIndentWidth;
        options.indentWidth = indentWidth;
        options.keepEmptyLinesAtTheStartOfBlocks = keepEmptyLinesAtTheStartOfBlocks;
        options.maxEmptyLinesToKeep = maxEmptyLinesToKeep;
        options.reflowComments = reflowComments;
        options.spaceBeforeAssignmentOperators = spaceBeforeAssignmentOperators;
        options.tabWidth = tabWidth;
        options.useTab = useTab;
        options.alignColons = alignColons;
        options.singleLineOverrulesHangingColon = singleLineOverrulesHangingColon;
        options.allowShortRulesOnASingleLine = allowShortRulesOnASingleLine;
        options.alignSemicolons = alignSemicolons;
        options.breakBeforeParens = breakBeforeParens;
        options.ruleInternalsOnSingleLine = ruleInternalsOnSingleLine;
        options.minEmptyLines = minEmptyLines;
        options.groupedAlignments = groupedAlignments;
        options.alignFirstTokens = alignFirstTokens;
        options.alignLexerCommands = alignLexerCommands;
        options.alignActions = alignActions;
        options.alignLabels = alignLabels;
        options.alignTrailers = alignTrailers;
        return options;
    }

    /**
     * Parses the external directive spelling of colon alignment values.
     */
    static final class ColonAlignmentConverter implements CommandLine.ITypeConverter<ColonAlignment> {

        /**
         * Converts a CLI value into the matching {@link ColonAlignment}.
         *
         * @param value the raw command line value
         * @return the parsed alignment mode
         * @throws Exception if the value is not a recognized alignment mode
         */
        @Override
        public ColonAlignment convert(String value) throws Exception {
            return switch (value) {
                case "none" -> ColonAlignment.NONE;
                case "trailing" -> ColonAlignment.TRAILING;
                case "hanging" -> ColonAlignment.HANGING;
                default -> throw new CommandLine.TypeConversionException(
                    "Expected one of: none, trailing, hanging (got '" + value + "')");
            };
        }
    }

    /**
     * Parses the external directive spelling of semicolon alignment values.
     */
    static final class SemicolonAlignmentConverter implements CommandLine.ITypeConverter<SemicolonAlignment> {

        /**
         * Converts a CLI value into the matching {@link SemicolonAlignment}.
         *
         * @param value the raw command line value
         * @return the parsed alignment mode
         * @throws Exception if the value is not a recognized alignment mode
         */
        @Override
        public SemicolonAlignment convert(String value) throws Exception {
            return switch (value) {
                case "none" -> SemicolonAlignment.NONE;
                case "ownLine" -> SemicolonAlignment.OWN_LINE;
                case "hanging" -> SemicolonAlignment.HANGING;
                default -> throw new CommandLine.TypeConversionException(
                    "Expected one of: none, ownLine, hanging (got '" + value + "')");
            };
        }
    }
}


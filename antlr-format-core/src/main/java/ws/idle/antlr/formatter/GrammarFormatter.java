package ws.idle.antlr.formatter;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.antlr.v4.runtime.CharStream;
import org.antlr.v4.runtime.CharStreams;
import org.antlr.v4.runtime.CommonToken;
import org.antlr.v4.runtime.CommonTokenStream;
import org.antlr.v4.runtime.Token;
import org.antlr.v4.runtime.misc.Interval;
import ws.idle.antlr.formatter.lexer.ANTLRv4Lexer;

/**
 * Pure Java port of the original formatter implementation.
 */
public final class GrammarFormatter {

    private static final String FORMAT_INTRODUCER = "$antlr-format";

    private static final int MARKER_UNDEFINED = 0;
    private static final int MARKER_LINE_BREAK = -2;
    private static final int MARKER_SPACE = -3;
    private static final int MARKER_TAB = -4;
    private static final int MARKER_FORMATTING_OPTIONS = -5;
    private static final int MARKER_WHITESPACE = -100;
    private static final int MARKER_COMMENT = -101;
    private static final int MARKER_WHITESPACE_ERASER = -102;
    private static final int MARKER_ERROR = -103;
    private static final int MARKER_RANGE = -100000;
    private static final int MARKER_ALIGNMENT = -200000;
    private static final int MARKER_WHITESPACE_BLOCK = -300000;

    private static final Pattern COMMENT_BODY_PATTERN = Pattern.compile("/\\*(\\s*\\*?)*(.*)\\*/", Pattern.DOTALL);
    private static final Pattern COMMAND_ENTRY_PATTERN = Pattern.compile("(\\w+)(?:(?:\\s*:)?\\s*)?(\\w+|[0-9]+)?",
        Pattern.CASE_INSENSITIVE);
    private static final Pattern LEADING_STARS = Pattern.compile("^\\s*\\*+\\s*");
    private static final Pattern TRAILING_WHITESPACE = Pattern.compile("\\s*$");

    private static final AlignmentType[] ALL_ALIGNMENTS = {
        AlignmentType.COLON,
        AlignmentType.FIRST_TOKEN,
        AlignmentType.LABEL,
        AlignmentType.ACTION,
        AlignmentType.LEXER_COMMAND,
        AlignmentType.TRAILING_COMMENT,
        AlignmentType.TRAILERS,
    };

    private FormattingOptions options;
    private final List<Token> tokens;
    private final String sourceText;
    private boolean addOptionsAsComment;

    private List<Integer> outputPipeline;
    private int currentIndentation;
    private boolean formattingDisabled;
    private int currentLine;
    private int currentColumn;
    private int singleLineBlockNesting;
    private List<int[]> ranges;
    private int currentRangeIndex;
    private int rangeStart;
    private final Map<AlignmentType, AlignmentStatus> alignments = new EnumMap<>(AlignmentType.class);
    private List<String> whitespaceList;
    private boolean containsFormattingOptions;

    private enum AlignmentType {
        COLON,
        FIRST_TOKEN,
        LABEL,
        LEXER_COMMAND,
        ACTION,
        TRAILING_COMMENT,
        TRAILERS,
    }

    private static final class AlignmentStatus {
        private int lastLine = -1;
        private final List<List<Integer>> groups = new ArrayList<>();
    }

    private record BlockInfo(boolean containsAlts, int singleLineLength) {
    }

    public GrammarFormatter(String grammar) {
        this(grammar, false);
    }

    public GrammarFormatter(String grammar, boolean addOptionsAsComment) {
        ANTLRv4Lexer lexer = new ANTLRv4Lexer(CharStreams.fromString(grammar));
        lexer.removeErrorListeners();
        CommonTokenStream tokenStream = new CommonTokenStream(lexer);
        tokenStream.fill();
        this.tokens = List.copyOf(tokenStream.getTokens());
        this.sourceText = grammar;
        this.addOptionsAsComment = addOptionsAsComment;
    }

    public GrammarFormatter(List<Token> tokens) {
        this(tokens, false);
    }

    public GrammarFormatter(List<Token> tokens, boolean addOptionsAsComment) {
        this.tokens = List.copyOf(tokens);
        this.sourceText = extractSource(tokens);
        this.addOptionsAsComment = addOptionsAsComment;
    }

    public static String convertToComment(FormattingOptions options) {
        List<String> entries = new ArrayList<>();
        append(entries, "disabled", options.disabled);
        append(entries, "alignTrailingComments", options.alignTrailingComments);
        append(entries, "allowShortBlocksOnASingleLine", options.allowShortBlocksOnASingleLine);
        append(entries, "breakBeforeBraces", options.breakBeforeBraces);
        append(entries, "columnLimit", options.columnLimit);
        append(entries, "continuationIndentWidth", options.continuationIndentWidth);
        append(entries, "indentWidth", options.indentWidth);
        append(entries, "keepEmptyLinesAtTheStartOfBlocks", options.keepEmptyLinesAtTheStartOfBlocks);
        append(entries, "maxEmptyLinesToKeep", options.maxEmptyLinesToKeep);
        append(entries, "reflowComments", options.reflowComments);
        append(entries, "spaceBeforeAssignmentOperators", options.spaceBeforeAssignmentOperators);
        append(entries, "tabWidth", options.tabWidth);
        append(entries, "useTab", options.useTab);
        if (options.alignColons != null) {
            entries.add("alignColons " + options.alignColons.externalName());
        }
        append(entries, "singleLineOverrulesHangingColon", options.singleLineOverrulesHangingColon);
        append(entries, "allowShortRulesOnASingleLine", options.allowShortRulesOnASingleLine);
        if (options.alignSemicolons != null) {
            entries.add("alignSemicolons " + options.alignSemicolons.externalName());
        }
        append(entries, "breakBeforeParens", options.breakBeforeParens);
        append(entries, "ruleInternalsOnSingleLine", options.ruleInternalsOnSingleLine);
        append(entries, "minEmptyLines", options.minEmptyLines);
        append(entries, "groupedAlignments", options.groupedAlignments);
        append(entries, "alignFirstTokens", options.alignFirstTokens);
        append(entries, "alignLexerCommands", options.alignLexerCommands);
        append(entries, "alignActions", options.alignActions);
        append(entries, "alignLabels", options.alignLabels);
        append(entries, "alignTrailers", options.alignTrailers);

        String line = "";
        List<String> lines = new ArrayList<>();
        while (true) {
            String next = entries.isEmpty() ? null : entries.remove(0);
            if (next == null) {
                if (!line.isEmpty()) {
                    lines.add("// $antlr-format " + line);
                }
                break;
            }

            if (line.length() + next.length() > 130) {
                lines.add("// $antlr-format " + line);
                line = "";
            }

            line += (line.isEmpty() ? "" : ", ") + next;
        }

        return "\n" + String.join("\n", lines) + "\n\n";
    }

    public FormattingResult formatGrammar(FormattingOptions options, Integer start, Integer stop) {
        if (tokens.isEmpty() || Boolean.TRUE.equals(options.disabled)) {
            return new FormattingResult("", -1, -1);
        }

        setDefaultOptions();
        this.options.mergeFrom(options);
        if (this.options.columnLimit <= 0) {
            this.options.columnLimit = 1_000_000_000;
        }

        outputPipeline = new ArrayList<>();
        currentIndentation = 0;
        singleLineBlockNesting = 0;
        ranges = new ArrayList<>();
        currentRangeIndex = MARKER_UNDEFINED;
        rangeStart = -1;
        alignments.clear();
        whitespaceList = new ArrayList<>();
        currentColumn = 0;
        currentLine = 1;
        formattingDisabled = false;
        containsFormattingOptions = false;

        boolean coalesceWhitespaces = false;
        boolean inBraces = false;
        boolean inMeta = false;
        boolean inRule = false;
        boolean inNamedAction = false;
        boolean inLexerCommand = false;
        boolean inCatchFinally = false;
        boolean inSingleLineRule = false;
        boolean minLineInsertionPending = false;

        int startIndex = tokenFromIndex(start == null ? 0 : start, true);
        int endIndex = tokenFromIndex(stop == null ? Integer.MAX_VALUE : stop, false);

        if (this.options.reflowComments && tokenType(startIndex) == ANTLRv4Lexer.LINE_COMMENT) {
            int runningIndex = startIndex;
            while (runningIndex > 0) {
                if (tokenText(tokens.get(runningIndex--)).contains(FORMAT_INTRODUCER)) {
                    break;
                }

                if (tokenType(runningIndex) != ANTLRv4Lexer.WS
                    || tokenLine(runningIndex) + 1 != tokenLine(runningIndex + 1)) {
                    break;
                }
                startIndex = runningIndex + 1;

                if (tokenType(--runningIndex) != ANTLRv4Lexer.LINE_COMMENT) {
                    break;
                }
            }
        }

        int targetStart = tokenStart(startIndex);
        int startRow = tokenLine(startIndex);
        int targetStop = tokenStop(endIndex);
        targetStart -= tokenColumn(startIndex);

        int run = startIndex;
        boolean done = false;
        while (run > 0 && !done) {
            switch (tokenType(run)) {
                case ANTLRv4Lexer.SEMI -> {
                    int localRun = run;
                    while (localRun-- > 0 && !done) {
                        switch (tokenType(localRun)) {
                            case ANTLRv4Lexer.OPTIONS, ANTLRv4Lexer.TOKENS, ANTLRv4Lexer.CHANNELS -> {
                                if (tokenLine(localRun) < startRow) {
                                    ++currentIndentation;
                                    inBraces = true;
                                }

                                localRun = startIndex;
                                int type;
                                do {
                                    type = tokenType(localRun);
                                    if (type != ANTLRv4Lexer.WS
                                        && type != ANTLRv4Lexer.LINE_COMMENT
                                        && type != ANTLRv4Lexer.BLOCK_COMMENT
                                        && type != ANTLRv4Lexer.DOC_COMMENT) {
                                        break;
                                    }
                                } while (localRun-- > 0);
                                coalesceWhitespaces = type != ANTLRv4Lexer.SEMI;
                                done = true;
                            }
                            case ANTLRv4Lexer.BEGIN_ACTION, ANTLRv4Lexer.END_ACTION, ANTLRv4Lexer.COLON,
                                ANTLRv4Lexer.COLONCOLON, ANTLRv4Lexer.OR -> done = true;
                            default -> {
                            }
                        }
                    }
                    done = true;
                }
                case ANTLRv4Lexer.COLON -> {
                    if (tokenLine(run) < startRow) {
                        if (!inRule) {
                            ++currentIndentation;
                            inRule = true;
                        }
                        coalesceWhitespaces = true;
                    }
                    done = true;
                }
                case ANTLRv4Lexer.AT -> {
                    startRow = tokenLine(run);
                    startIndex = run;
                    targetStart = tokenStart(run);
                    done = true;
                }
                case ANTLRv4Lexer.OPTIONS, ANTLRv4Lexer.TOKENS, ANTLRv4Lexer.CHANNELS, ANTLRv4Lexer.BEGIN_ACTION -> {
                    if (tokenLine(run) < startRow) {
                        ++currentIndentation;
                        inBraces = true;
                    }
                    done = true;
                }
                case ANTLRv4Lexer.RBRACE, ANTLRv4Lexer.END_ACTION -> done = true;
                case ANTLRv4Lexer.LPAREN -> {
                    if (tokenLine(run) < startRow) {
                        ++currentIndentation;
                    }
                    --run;
                }
                case ANTLRv4Lexer.RPAREN -> {
                    if (tokenLine(run) < startRow) {
                        --currentIndentation;
                    }
                    --run;
                }
                default -> --run;
            }
        }

        currentLine = startRow;
        pushCurrentIndentation(false);
        for (int i = startIndex; i <= endIndex; ++i) {
            Token token = tokens.get(i);

            if (token.getType() != ANTLRv4Lexer.WS && lastEntryIs(MARKER_WHITESPACE_ERASER)) {
                outputPipeline.remove(outputPipeline.size() - 1);
            }

            if (minLineInsertionPending && token.getType() != ANTLRv4Lexer.WS && token.getType() != ANTLRv4Lexer.LINE_COMMENT) {
                minLineInsertionPending = false;
                ensureMinEmptyLines();
            }

            switch (token.getType()) {
                case ANTLRv4Lexer.WS -> {
                    if (i == 0 || formattingDisabled) {
                        continue;
                    }

                    int nextType = tokens.get(i + 1).getType();
                    boolean localCommentAhead = nextType == ANTLRv4Lexer.LINE_COMMENT
                        || nextType == ANTLRv4Lexer.BLOCK_COMMENT || nextType == ANTLRv4Lexer.DOC_COMMENT;

                    if (lastEntryIs(MARKER_WHITESPACE_ERASER)) {
                        outputPipeline.remove(outputPipeline.size() - 1);
                        if (!localCommentAhead) {
                            continue;
                        }
                    }

                    String text = tokenText(token).replace("\r\n", "\n");
                    boolean hasLineBreaks = text.contains("\n");
                    if (!localCommentAhead || !hasLineBreaks) {
                        if (!hasLineBreaks || coalesceWhitespaces || singleLineBlockNesting > 0) {
                            if (!lastEntryIs(MARKER_WHITESPACE)) {
                                addSpace();
                            }
                            continue;
                        }
                    }

                    String[] parts = text.split("\\n", -1);
                    int breakCount = 0;
                    if (localCommentAhead && lastCodeTokenIs(ANTLRv4Lexer.LPAREN)
                        && !Boolean.TRUE.equals(this.options.keepEmptyLinesAtTheStartOfBlocks)) {
                        breakCount = 1;
                    } else {
                        int j = outputPipeline.size() - 1;
                        while (j >= 0) {
                            if (entryIs(j, MARKER_LINE_BREAK)) {
                                ++breakCount;
                            } else {
                                break;
                            }
                            --j;
                        }
                        breakCount = Math.max(breakCount, parts.length - 1);
                        breakCount = Math.min(breakCount, this.options.maxEmptyLinesToKeep + 1);
                    }
                    removeTrailingWhitespaces();
                    for (int j = 0; j < breakCount; j++) {
                        outputPipeline.add(MARKER_LINE_BREAK);
                    }
                    currentLine += breakCount;
                    currentColumn = 0;
                    if (i < endIndex && minLineInsertionPending) {
                        minLineInsertionPending = false;
                        ensureMinEmptyLines();
                    }
                    pushCurrentIndentation(false);
                }
                case ANTLRv4Lexer.SEMI -> {
                    removeTrailingWhitespaces();
                    if (!inSingleLineRule) {
                        singleLineBlockNesting = 0;
                    }
                    boolean canAlignSemicolon = !inMeta && (!inSingleLineRule || this.options.alignColons == ColonAlignment.HANGING
                        || this.options.alignSemicolons != SemicolonAlignment.NONE);
                    if (canAlignSemicolon && !inBraces && inRule) {
                        switch (this.options.alignSemicolons) {
                            case NONE -> {
                            }
                            case OWN_LINE -> addLineBreak(!Boolean.TRUE.equals(this.options.singleLineOverrulesHangingColon));
                            case HANGING -> {
                                addLineBreak(true);
                                pushCurrentIndentation(true);
                            }
                        }
                    }
                    add(i);
                    if (!inBraces && currentIndentation > 0) {
                        --currentIndentation;
                    }
                    singleLineBlockNesting = 0;
                    inSingleLineRule = false;
                    if (currentIndentation == 0) {
                        minLineInsertionPending = true;
                        if (inMeta) {
                            addLineBreak(false);
                        }
                    } else {
                        addLineBreak(false);
                        pushCurrentIndentation(false);
                    }
                    coalesceWhitespaces = false;
                    inLexerCommand = false;
                    inMeta = false;
                    if (!inBraces) {
                        inRule = false;
                    }
                }
                case ANTLRv4Lexer.OPTIONS, ANTLRv4Lexer.TOKENS, ANTLRv4Lexer.CHANNELS -> {
                    add(i);
                    coalesceWhitespaces = true;
                    if (!inRule) {
                        ++currentIndentation;
                    }
                    inBraces = true;
                    if (!nonBreakingTrailerAhead(i)) {
                        addLineBreak(false);
                        pushCurrentIndentation(false);
                    }
                }
                case ANTLRv4Lexer.RBRACE -> {
                    removeTrailingWhitespaces();
                    addLineBreak(false);
                    --currentIndentation;
                    if (!inRule) {
                        minLineInsertionPending = currentIndentation == 0;
                    } else {
                        pushCurrentIndentation(false);
                        ++currentIndentation;
                    }
                    add(i);
                    coalesceWhitespaces = false;
                    inBraces = false;
                }
                case ANTLRv4Lexer.BEGIN_ACTION -> {
                    if (formattingDisabled) {
                        continue;
                    }
                    if (Boolean.TRUE.equals(this.options.alignTrailers)) {
                        addAlignmentEntry(AlignmentType.TRAILERS);
                    } else if (Boolean.TRUE.equals(this.options.alignActions)) {
                        addAlignmentEntry(AlignmentType.ACTION);
                    }
                    add(i++);
                    if (inCatchFinally && !"\n".equals(tokenText(tokens.get(i)))) {
                        addLineBreak(false);
                    }
                    int actionStart = i;
                    while (i <= endIndex && tokenType(i) != Token.EOF && tokenType(i) != ANTLRv4Lexer.END_ACTION) {
                        ++i;
                    }
                    addRaw(actionStart, i - 1);
                    if (i <= endIndex) {
                        if (inCatchFinally && !"\n".equals(tokenText(tokens.get(i - 1)))) {
                            addLineBreak(false);
                        }
                        add(i);
                        addSpace();
                        minLineInsertionPending = currentIndentation == 0;
                        if (!inRule) {
                            inNamedAction = false;
                            coalesceWhitespaces = false;
                        }
                        inCatchFinally = false;
                    }
                }
                case ANTLRv4Lexer.LINE_COMMENT, ANTLRv4Lexer.BLOCK_COMMENT -> {
                    processFormattingCommands(i);
                    // fall through
                    boolean hasLineContent = lineHasLeadingNonWhitespaceContent();
                    String comment = tokenText(token);
                    if (hasLineContent) {
                        if (token.getType() == ANTLRv4Lexer.LINE_COMMENT) {
                            if (Boolean.TRUE.equals(this.options.alignTrailers)) {
                                addAlignmentEntry(AlignmentType.TRAILERS);
                            } else if (Boolean.TRUE.equals(this.options.alignTrailingComments)) {
                                addAlignmentEntry(AlignmentType.TRAILING_COMMENT);
                            }
                        }
                    } else if (!comment.contains(FORMAT_INTRODUCER)
                        && Boolean.TRUE.equals(this.options.reflowComments)
                        && token.getType() == ANTLRv4Lexer.LINE_COMMENT) {
                        while (true) {
                            Token nextToken = tokens.get(i + 1);
                            if (nextToken.getType() == Token.EOF) {
                                break;
                            }
                            String content = tokenText(nextToken);
                            if (content.split("\\n", -1).length > 2) {
                                break;
                            }
                            nextToken = tokens.get(i + 2);
                            if (nextToken.getType() != ANTLRv4Lexer.LINE_COMMENT || tokenText(nextToken).contains(FORMAT_INTRODUCER)) {
                                break;
                            }
                            comment += "\n" + tokenText(nextToken);
                            i += 2;
                            processFormattingCommands(i);
                        }
                    }

                    if (Boolean.TRUE.equals(this.options.reflowComments) && comment.contains("\n")) {
                        String formatted = reflowComment(comment, token.getType());
                        int whitespaceIndex = MARKER_WHITESPACE_BLOCK - whitespaceList.size();
                        outputPipeline.add(whitespaceIndex);
                        whitespaceList.add(formatted);
                        for (char c : formatted.toCharArray()) {
                            if (c == '\n') {
                                ++currentLine;
                            }
                        }
                        addLineBreak(false);
                        pushCurrentIndentation(false);
                    } else {
                        add(i);
                        if (token.getType() == ANTLRv4Lexer.LINE_COMMENT) {
                            if (currentIndentation > 0) {
                                addLineBreak(false);
                                pushCurrentIndentation(false);
                            }
                        } else {
                            addSpace();
                        }
                    }
                }
                case ANTLRv4Lexer.DOC_COMMENT -> {
                    boolean hasLineContent = lineHasLeadingNonWhitespaceContent();
                    String comment = tokenText(token);
                    if (Boolean.TRUE.equals(this.options.reflowComments) && comment.contains("\n")) {
                        String formatted = reflowComment(comment, token.getType());
                        int whitespaceIndex = MARKER_WHITESPACE_BLOCK - whitespaceList.size();
                        outputPipeline.add(whitespaceIndex);
                        whitespaceList.add(formatted);
                        for (char c : formatted.toCharArray()) {
                            if (c == '\n') {
                                ++currentLine;
                            }
                        }
                        addLineBreak(false);
                        pushCurrentIndentation(false);
                    } else {
                        add(i);
                        if (token.getType() == ANTLRv4Lexer.LINE_COMMENT) {
                            if (currentIndentation > 0) {
                                addLineBreak(false);
                                pushCurrentIndentation(false);
                            }
                        } else {
                            addSpace();
                        }
                    }
                }
                case ANTLRv4Lexer.ASSIGN, ANTLRv4Lexer.PLUS_ASSIGN -> {
                    if (Boolean.TRUE.equals(this.options.spaceBeforeAssignmentOperators)) {
                        if (!lastEntryIs(MARKER_WHITESPACE)) {
                            addSpace();
                        }
                        add(i);
                        addSpace();
                    } else {
                        if (lastEntryIs(MARKER_WHITESPACE)) {
                            removeLastEntry();
                        }
                        add(i);
                    }
                }
                case ANTLRv4Lexer.AT -> {
                    if (inRule) {
                        removeTrailingWhitespaces();
                        if (Boolean.TRUE.equals(this.options.ruleInternalsOnSingleLine)) {
                            addSpace();
                        } else {
                            addLineBreak(false);
                            pushCurrentIndentation(false);
                        }
                    } else {
                        inNamedAction = true;
                    }
                    add(i);
                    add(MARKER_WHITESPACE_ERASER);
                }
                case ANTLRv4Lexer.COLON -> {
                    BlockInfo blockInfo = getBlockInfo(i, Set.of(ANTLRv4Lexer.SEMI));
                    int singleLineLength = blockInfo.singleLineLength + currentColumn;
                    if (Boolean.TRUE.equals(this.options.allowShortRulesOnASingleLine)
                        && singleLineLength <= (2 * this.options.columnLimit / 3)) {
                        ++singleLineBlockNesting;
                        inSingleLineRule = true;
                    }

                    switch (this.options.alignColons) {
                        case HANGING -> {
                            removeTrailingWhitespaces();
                            boolean forceNewLine = !Boolean.TRUE.equals(this.options.singleLineOverrulesHangingColon);
                            addLineBreak(forceNewLine);
                            pushCurrentIndentation(forceNewLine);
                            add(i);
                            addSpace();
                            add(MARKER_WHITESPACE_ERASER);
                        }
                        case NONE -> {
                            removeTrailingWhitespaces();
                            add(i);
                            if (!nonBreakingTrailerAhead(i) && !inSingleLineRule) {
                                addLineBreak(false);
                                pushCurrentIndentation(false);
                            } else {
                                addSpace();
                            }
                        }
                        case TRAILING -> {
                            if (!lastRealTokenIs(ANTLRv4Lexer.LINE_COMMENT)) {
                                removeTrailingWhitespaces();
                            }
                            if (singleLineBlockNesting > 0) {
                                addAlignmentEntry(AlignmentType.COLON);
                                add(MARKER_WHITESPACE_ERASER);
                            }
                            add(i);
                            if (!nonBreakingTrailerAhead(i) && !inSingleLineRule) {
                                addLineBreak(false);
                                pushCurrentIndentation(false);
                            } else {
                                addSpace();
                            }
                        }
                    }

                    if (Boolean.TRUE.equals(this.options.alignFirstTokens) && inSingleLineRule) {
                        removeTrailingWhitespaces();
                        addAlignmentEntry(AlignmentType.FIRST_TOKEN);
                        add(MARKER_WHITESPACE_ERASER);
                    }
                }
                case ANTLRv4Lexer.COLONCOLON -> {
                    removeTrailingWhitespaces();
                    add(i);
                    add(MARKER_WHITESPACE_ERASER);
                }
                case ANTLRv4Lexer.LEXER, ANTLRv4Lexer.PARSER, ANTLRv4Lexer.GRAMMAR -> {
                    if (addOptionsAsComment && !containsFormattingOptions) {
                        add(MARKER_FORMATTING_OPTIONS);
                        addOptionsAsComment = false;
                    }
                    if (token.getType() != ANTLRv4Lexer.GRAMMAR) {
                        add(i);
                        continue;
                    }
                    // fall through
                    if (!inNamedAction && !inRule) {
                        ++currentIndentation;
                        coalesceWhitespaces = true;
                        inMeta = true;
                    }
                    add(i);
                }
                case ANTLRv4Lexer.IMPORT, ANTLRv4Lexer.MODE -> {
                    if (!inNamedAction && !inRule) {
                        ++currentIndentation;
                        coalesceWhitespaces = true;
                        inMeta = true;
                    }
                    add(i);
                }
                case ANTLRv4Lexer.FRAGMENT, ANTLRv4Lexer.PRIVATE, ANTLRv4Lexer.PROTECTED, ANTLRv4Lexer.PUBLIC,
                    ANTLRv4Lexer.TOKEN_REF, ANTLRv4Lexer.RULE_REF -> {
                    if (!inNamedAction && !inBraces && !inMeta && !inRule) {
                        inRule = true;
                        ++currentIndentation;
                        if (!outputPipeline.isEmpty() && lastEntryIs(MARKER_SPACE)) {
                            removeLastEntry();
                            addLineBreak(false);
                        }
                    }
                    coalesceWhitespaces = true;
                    add(i);
                    if (!inLexerCommand) {
                        addSpace();
                    }
                }
                case ANTLRv4Lexer.PLUS, ANTLRv4Lexer.QUESTION, ANTLRv4Lexer.STAR -> {
                    removeTrailingWhitespaces();
                    add(i);
                    addSpace();
                }
                case ANTLRv4Lexer.OR -> {
                    if (singleLineBlockNesting > 1) {
                        addSpace();
                    } else {
                        if (!inSingleLineRule) {
                            singleLineBlockNesting = 0;
                            removeTrailingTabsAndSpaces();
                            if (!outputPipeline.isEmpty() && !lastEntryIs(MARKER_LINE_BREAK)) {
                                addLineBreak(false);
                            }
                            pushCurrentIndentation(false);
                            BlockInfo info = getBlockInfo(i, Set.of(ANTLRv4Lexer.OR, ANTLRv4Lexer.SEMI));
                            if ((!info.containsAlts || Boolean.TRUE.equals(this.options.allowShortBlocksOnASingleLine))
                                && info.singleLineLength <= (this.options.columnLimit / 2 + 3)) {
                                ++singleLineBlockNesting;
                            }
                        }
                    }
                    add(i);
                    addSpace();
                }
                case ANTLRv4Lexer.LPAREN -> {
                    if (inLexerCommand) {
                        add(i);
                        continue;
                    }
                    if (singleLineBlockNesting > 0) {
                        singleLineBlockNesting += 2;
                        ++currentIndentation;
                        add(i);
                    } else {
                        if (Boolean.TRUE.equals(this.options.allowShortBlocksOnASingleLine)) {
                            BlockInfo info = getBlockInfo(i, Set.of(ANTLRv4Lexer.RPAREN));
                            int singleLineLength = info.singleLineLength + currentColumn;
                            if (singleLineLength <= (2 * this.options.columnLimit / 3)) {
                                singleLineBlockNesting += 2;
                            }
                        }
                        if (singleLineBlockNesting == 0) {
                            if (Boolean.TRUE.equals(this.options.breakBeforeParens)) {
                                removeTrailingWhitespaces();
                                addLineBreak(false);
                                pushCurrentIndentation(false);
                            }
                            add(i);
                            ++currentIndentation;
                            addLineBreak(false);
                            pushCurrentIndentation(false);
                            if (Boolean.TRUE.equals(this.options.allowShortBlocksOnASingleLine)) {
                                BlockInfo info = getBlockInfo(i, Set.of(ANTLRv4Lexer.OR, ANTLRv4Lexer.RPAREN));
                                int singleLineLength = info.singleLineLength + currentColumn;
                                if (singleLineLength <= (this.options.columnLimit / 2 + 3)) {
                                    ++singleLineBlockNesting;
                                }
                            }
                        } else {
                            add(i);
                            add(MARKER_WHITESPACE_ERASER);
                            ++currentIndentation;
                        }
                    }
                }
                case ANTLRv4Lexer.RPAREN -> {
                    if (inLexerCommand) {
                        add(i);
                        continue;
                    }
                    if (singleLineBlockNesting > 0) {
                        --singleLineBlockNesting;
                    }
                    if (currentIndentation > 0) {
                        --currentIndentation;
                    }
                    removeTrailingWhitespaces();
                    if (singleLineBlockNesting > 0) {
                        add(i);
                    } else {
                        addLineBreak(false);
                        pushCurrentIndentation(false);
                        add(i);
                    }
                    addSpace();
                    if (singleLineBlockNesting > 0) {
                        --singleLineBlockNesting;
                    }
                }
                case ANTLRv4Lexer.GT -> {
                    removeTrailingWhitespaces();
                    add(i);
                }
                case ANTLRv4Lexer.RARROW -> {
                    inLexerCommand = true;
                    if (Boolean.TRUE.equals(this.options.alignTrailers)) {
                        addAlignmentEntry(AlignmentType.TRAILERS);
                    } else if (Boolean.TRUE.equals(this.options.alignLexerCommands)) {
                        addAlignmentEntry(AlignmentType.LEXER_COMMAND);
                    } else if (!lastEntryIs(MARKER_SPACE)) {
                        addSpace();
                    }
                    add(i);
                    addSpace();
                }
                case ANTLRv4Lexer.COMMA -> {
                    removeTrailingWhitespaces();
                    add(i);
                    if (inBraces) {
                        coalesceWhitespaces = false;
                        if (!nonBreakingTrailerAhead(i)) {
                            addLineBreak(false);
                            pushCurrentIndentation(false);
                        }
                    } else {
                        addSpace();
                    }
                }
                case ANTLRv4Lexer.POUND -> {
                    boolean willUseAlignment = false;
                    if (!inSingleLineRule) {
                        if (Boolean.TRUE.equals(this.options.alignTrailers)) {
                            willUseAlignment = true;
                            addAlignmentEntry(AlignmentType.TRAILERS);
                        } else if (Boolean.TRUE.equals(this.options.alignLabels)) {
                            willUseAlignment = true;
                            addAlignmentEntry(AlignmentType.LABEL);
                        }
                    }
                    if (!willUseAlignment && !lastEntryIs(MARKER_SPACE)) {
                        addSpace();
                    }
                    add(i);
                    addSpace();
                }
                case ANTLRv4Lexer.BEGIN_ARGUMENT -> {
                    if (formattingDisabled) {
                        continue;
                    }
                    removeTrailingWhitespaces();
                    add(i++);
                    int argumentStartIndex = i;
                    while (tokenType(i) != Token.EOF && tokenType(i) != ANTLRv4Lexer.END_ARGUMENT) {
                        ++i;
                    }
                    addRaw(argumentStartIndex, i);
                }
                case ANTLRv4Lexer.CATCH, ANTLRv4Lexer.FINALLY -> {
                    inCatchFinally = true;
                    removeTrailingWhitespaces();
                    addLineBreak(false);
                    add(i);
                }
                case ANTLRv4Lexer.RETURNS, ANTLRv4Lexer.LOCALS -> {
                    removeTrailingWhitespaces();
                    if (Boolean.TRUE.equals(this.options.ruleInternalsOnSingleLine)) {
                        addSpace();
                    } else {
                        addLineBreak(false);
                        pushCurrentIndentation(false);
                    }
                    add(i);
                }
                case ANTLRv4Lexer.STRING_LITERAL -> {
                    add(i);
                    addSpace();
                }
                case Token.EOF -> {
                    removeTrailingWhitespaces();
                    addLineBreak(false);
                }
                default -> {
                    coalesceWhitespaces = true;
                    add(i);
                }
            }
        }

        if (lastEntryIs(MARKER_WHITESPACE_ERASER)) {
            removeLastEntry();
        }

        if (tokenType(endIndex) != ANTLRv4Lexer.WS) {
            if (lastEntryIs(MARKER_ALIGNMENT)) {
                removeLastEntry();
            }
            removeTrailingWhitespaces();
        }

        if (formattingDisabled && rangeStart > -1) {
            addRaw(rangeStart, endIndex);
        }

        computeAlignments();

        StringBuilder result = new StringBuilder();
        int pendingLineComment = -1;
        boolean hadErrorOnLine = false;
        for (int entry : outputPipeline) {
            switch (entry) {
                case MARKER_LINE_BREAK -> {
                    if (pendingLineComment > -1) {
                        if (!result.isEmpty()) {
                            char lastChar = result.charAt(result.length() - 1);
                            if (lastChar != ' ' && lastChar != '\t' && lastChar != '\n') {
                                result.append(' ');
                            }
                        }
                        result.append(tokenText(tokens.get(pendingLineComment)));
                        pendingLineComment = -1;
                    }
                    result.append('\n');
                    hadErrorOnLine = false;
                }
                case MARKER_SPACE -> result.append(' ');
                case MARKER_TAB -> result.append('\t');
                case MARKER_WHITESPACE_ERASER -> {
                }
                case MARKER_ERROR -> {
                    if (!hadErrorOnLine) {
                        result.append("<<Unexpected input or wrong formatter command>>");
                        hadErrorOnLine = true;
                    }
                }
                case MARKER_FORMATTING_OPTIONS -> {
                    if (!containsFormattingOptions) {
                        result.append(convertToComment(options));
                    }
                }
                default -> {
                    if (entry < 0) {
                        if (isWhitespaceBlock(entry)) {
                            result.append(whitespaceList.get(-(entry - MARKER_WHITESPACE_BLOCK)));
                        } else if (isRangeBlock(entry)) {
                            int rangeIndex = -(entry - MARKER_RANGE);
                            int[] range = ranges.get(rangeIndex);
                            result.append(sourceText, range[0], range[1] + 1);
                        }
                    } else {
                        if (tokenType(entry) == ANTLRv4Lexer.LINE_COMMENT) {
                            pendingLineComment = entry;
                        } else {
                            result.append(tokenText(tokens.get(entry)));
                        }
                    }
                }
            }
        }

        if (pendingLineComment > 0) {
            if (!result.isEmpty()) {
                char lastChar = result.charAt(result.length() - 1);
                if (lastChar != ' ' && lastChar != '\t' && lastChar != '\n') {
                    result.append(' ');
                }
            }
            result.append(tokenText(tokens.get(pendingLineComment)));
        }

        return new FormattingResult(result.toString(), targetStart, targetStop);
    }

    public FormattingResult formatGrammar(FormattingOptions options) {
        return formatGrammar(options, 0, Integer.MAX_VALUE);
    }

    private void setDefaultOptions() {
        options = FormattingOptions.defaults();
    }

    private boolean entryIs(int index, int marker) {
        if (index < 0 || index >= outputPipeline.size()) {
            return false;
        }

        int entry = outputPipeline.get(index);
        switch (marker) {
            case MARKER_WHITESPACE:
                return entry == MARKER_LINE_BREAK || entry == MARKER_SPACE || entry == MARKER_TAB;
            case MARKER_SPACE:
                return entry == MARKER_SPACE;
            case MARKER_TAB:
                return entry == MARKER_TAB;
            case MARKER_LINE_BREAK:
                return entry == MARKER_LINE_BREAK;
            case MARKER_COMMENT:
                if (entry < 0) {
                    return false;
                }
                return tokenType(entry) == ANTLRv4Lexer.BLOCK_COMMENT || tokenType(entry) == ANTLRv4Lexer.LINE_COMMENT
                    || tokenType(entry) == ANTLRv4Lexer.DOC_COMMENT;
            default:
                if (entry < 0) {
                    return entry == marker;
                }
                return tokenType(entry) == marker;
        }
    }

    private boolean lastEntryIs(int marker) {
        return entryIs(outputPipeline.size() - 1, marker);
    }

    private boolean lineHasLeadingNonWhitespaceContent() {
        int index = outputPipeline.size();
        while (--index > 0) {
            int marker = outputPipeline.get(index);
            if (marker != MARKER_SPACE && marker != MARKER_TAB) {
                break;
            }
        }
        if (index <= 0) {
            return false;
        }
        return outputPipeline.get(index) != MARKER_LINE_BREAK;
    }

    private boolean lastCodeTokenIs(int marker) {
        int i = outputPipeline.size() - 1;
        while (i >= 0) {
            if (!entryIs(i, MARKER_WHITESPACE_ERASER)
                && !entryIs(i, MARKER_WHITESPACE)
                && !entryIs(i, MARKER_LINE_BREAK)
                && !entryIs(i, MARKER_COMMENT)) {
                break;
            }
            --i;
        }
        if (i < 0 || outputPipeline.get(i) < 0) {
            return false;
        }
        return tokenType(outputPipeline.get(i)) == marker;
    }

    private boolean lastRealTokenIs(int marker) {
        int i = outputPipeline.size() - 1;
        while (i >= 0) {
            if (!entryIs(i, MARKER_WHITESPACE_ERASER)
                && !entryIs(i, MARKER_WHITESPACE)
                && !entryIs(i, MARKER_LINE_BREAK)) {
                break;
            }
            --i;
        }
        if (i < 0 || outputPipeline.get(i) < 0) {
            return false;
        }
        return tokenType(outputPipeline.get(i)) == marker;
    }

    private void removeLastEntry() {
        if (formattingDisabled || outputPipeline.isEmpty()) {
            return;
        }

        int lastEntry = outputPipeline.remove(outputPipeline.size() - 1);
        switch (lastEntry) {
            case MARKER_WHITESPACE_ERASER -> {
            }
            case MARKER_LINE_BREAK -> --currentLine;
            case MARKER_TAB -> {
                int offset = currentColumn % options.tabWidth;
                currentColumn -= (offset > 0 ? offset : options.tabWidth);
            }
            default -> {
                if (currentColumn > 0) {
                    --currentColumn;
                }
            }
        }
    }

    private void removeTrailingTabsAndSpaces() {
        if (formattingDisabled) {
            return;
        }
        while (lastEntryIs(MARKER_SPACE) || lastEntryIs(MARKER_TAB)) {
            removeLastEntry();
        }
    }

    private void removeTrailingWhitespaces() {
        if (formattingDisabled) {
            return;
        }
        while (lastEntryIs(MARKER_WHITESPACE) || lastEntryIs(MARKER_WHITESPACE_ERASER)) {
            removeLastEntry();
        }
    }

    private void pushCurrentIndentation(boolean force) {
        if (formattingDisabled || (!force && singleLineBlockNesting > 0)) {
            return;
        }
        if (Boolean.TRUE.equals(options.useTab)) {
            for (int i = 0; i < currentIndentation; i++) {
                outputPipeline.add(MARKER_TAB);
            }
            currentColumn = currentIndentation * options.tabWidth;
        } else {
            int count = currentIndentation * options.indentWidth;
            for (int i = 0; i < count; i++) {
                outputPipeline.add(MARKER_SPACE);
            }
            currentColumn = currentIndentation * options.indentWidth;
        }
    }

    private void applyLineContinuation() {
        while (lastEntryIs(MARKER_SPACE) || lastEntryIs(MARKER_TAB)) {
            removeLastEntry();
        }
        if (!lastEntryIs(MARKER_LINE_BREAK)) {
            outputPipeline.add(MARKER_LINE_BREAK);
            ++currentLine;
        }
        currentColumn = 0;
        pushCurrentIndentation(true);
        if (Boolean.TRUE.equals(options.useTab)) {
            outputPipeline.add(MARKER_TAB);
        } else {
            for (int i = 0; i < options.continuationIndentWidth; i++) {
                outputPipeline.add(MARKER_SPACE);
            }
        }
        currentColumn += options.continuationIndentWidth;
    }

    private void add(int marker) {
        if (formattingDisabled) {
            return;
        }

        if (marker == MARKER_WHITESPACE_ERASER) {
            outputPipeline.add(marker);
            return;
        }
        if (marker == MARKER_LINE_BREAK) {
            outputPipeline.add(marker);
            ++currentLine;
            currentColumn = 0;
            return;
        }

        Token token = marker >= 0 && marker < tokens.size() ? tokens.get(marker) : null;
        if (token != null) {
            switch (token.getType()) {
                case ANTLRv4Lexer.BLOCK_COMMENT, ANTLRv4Lexer.ACTION_CONTENT -> {
                    String[] parts = tokenText(token).split("\\n", -1);
                    if (parts.length == 1) {
                        currentColumn += tokenText(token).length();
                    } else {
                        currentLine += parts.length - 1;
                        currentColumn = computeLineLength(parts[parts.length - 1]);
                    }
                    outputPipeline.add(marker);
                }
                default -> {
                    int tokenLength = tokenStop(marker) - tokenStart(marker) + 1;
                    if (currentColumn + tokenLength > options.columnLimit) {
                        if (lineHasLeadingNonWhitespaceContent()) {
                            applyLineContinuation();
                        }
                    }
                    currentColumn += tokenLength;
                    outputPipeline.add(marker);
                }
            }
        } else {
            ++currentColumn;
            outputPipeline.add(marker);
        }
    }

    private int tokenFromIndex(int charIndex, boolean first) {
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

    private int computeLineLength(String text) {
        int length = 0;
        for (char ch : text.toCharArray()) {
            if (ch == '\t') {
                int offsetToNextTabStop = options.tabWidth - (currentColumn % options.tabWidth);
                length += offsetToNextTabStop;
            } else {
                ++length;
            }
        }
        return length;
    }

    private void addRaw(int start, int stop) {
        String text = sourceText.substring(tokenStart(start), tokenStop(stop) + 1);
        if (text.contains("\n")) {
            String[] parts = text.split("\\n", -1);
            currentLine += parts.length - 1;
            currentColumn = computeLineLength(parts[parts.length - 1]);
        } else {
            currentColumn += computeLineLength(text);
        }
        ranges.add(new int[] { tokenStart(start), tokenStop(stop) });
        outputPipeline.add(MARKER_RANGE - currentRangeIndex++);
    }

    private void addSpace() {
        if (!outputPipeline.isEmpty() && !lastEntryIs(MARKER_SPACE) && !lastEntryIs(ANTLRv4Lexer.LINE_COMMENT)) {
            add(MARKER_SPACE);
        }
    }

    private void addLineBreak(boolean force) {
        if (singleLineBlockNesting == 0 || force) {
            while (lastEntryIs(MARKER_SPACE) || lastEntryIs(MARKER_TAB)) {
                removeLastEntry();
            }
            add(MARKER_LINE_BREAK);
        }
    }

    private void ensureMinEmptyLines() {
        if (formattingDisabled) {
            return;
        }
        if (options.minEmptyLines > 0) {
            int lineBreakCount = Math.min(options.minEmptyLines, options.maxEmptyLinesToKeep) + 1;
            for (int i = outputPipeline.size() - 1; i > 0 && lineBreakCount > 0; --i) {
                if (entryIs(i, MARKER_LINE_BREAK)) {
                    --lineBreakCount;
                } else if (!entryIs(i, MARKER_WHITESPACE)) {
                    break;
                }
            }
            for (int i = 0; i < lineBreakCount; i++) {
                outputPipeline.add(MARKER_LINE_BREAK);
            }
            currentLine += lineBreakCount;
            if (lineBreakCount > 0) {
                currentColumn = 0;
            }
        } else if (!lastEntryIs(MARKER_LINE_BREAK)) {
            addLineBreak(false);
        }
    }

    private BlockInfo getBlockInfo(int i, Set<Integer> stoppers) {
        boolean containsAlts = false;
        int singleLineLength = 1;
        int nestingLevel = 0;
        Token token = tokens.get(i);
        if (token.getType() == ANTLRv4Lexer.COLON || token.getType() == ANTLRv4Lexer.OR) {
            ++singleLineLength;
        }

        while (++i < tokens.size()) {
            token = tokens.get(i);
            switch (token.getType()) {
                case ANTLRv4Lexer.WS -> {
                }
                case ANTLRv4Lexer.LPAREN -> {
                    ++nestingLevel;
                    ++singleLineLength;
                }
                case ANTLRv4Lexer.RPAREN -> {
                    ++singleLineLength;
                    if (nestingLevel > 0) {
                        --nestingLevel;
                    } else {
                        return finalizeBlockInfo(containsAlts, singleLineLength, i);
                    }
                }
                case ANTLRv4Lexer.SEMI -> {
                    ++singleLineLength;
                    if (stoppers.contains(ANTLRv4Lexer.SEMI)) {
                        return finalizeBlockInfo(containsAlts, singleLineLength, i);
                    }
                }
                case ANTLRv4Lexer.QUESTION, ANTLRv4Lexer.STAR, ANTLRv4Lexer.PLUS -> ++singleLineLength;
                case ANTLRv4Lexer.LINE_COMMENT -> {
                    return new BlockInfo(containsAlts, 1000000000);
                }
                case ANTLRv4Lexer.BLOCK_COMMENT, ANTLRv4Lexer.DOC_COMMENT -> {
                    if (tokenText(token).contains("\n")) {
                        return new BlockInfo(containsAlts, 1000000000);
                    }
                    singleLineLength += tokenText(token).length() + 1;
                }
                case ANTLRv4Lexer.BEGIN_ACTION, ANTLRv4Lexer.ACTION_CONTENT, ANTLRv4Lexer.END_ACTION -> {
                    if ("\n".equals(tokenText(token))) {
                        return new BlockInfo(containsAlts, 1000000000);
                    }
                    ++singleLineLength;
                }
                case ANTLRv4Lexer.OR -> {
                    if (nestingLevel == 0) {
                        if (stoppers.contains(ANTLRv4Lexer.OR)) {
                            return finalizeBlockInfo(containsAlts, singleLineLength, i);
                        }
                        containsAlts = true;
                    }
                    singleLineLength += 2;
                }
                case ANTLRv4Lexer.NOT -> ++singleLineLength;
                default -> {
                    if (token.getText() != null) {
                        singleLineLength += token.getText().length();
                    }
                    ++singleLineLength;
                }
            }
        }
        return new BlockInfo(containsAlts, singleLineLength);
    }

    private BlockInfo finalizeBlockInfo(boolean containsAlts, int singleLineLength, int i) {
        while (++i < tokens.size() && tokenType(i) == ANTLRv4Lexer.WS) {
            if (tokenText(tokens.get(i)).contains("\n")) {
                return new BlockInfo(containsAlts, singleLineLength);
            }
        }
        if (i < tokens.size() && tokenType(i) == ANTLRv4Lexer.LINE_COMMENT) {
            singleLineLength += tokenText(tokens.get(i)).length();
        }
        return new BlockInfo(containsAlts, singleLineLength);
    }

    private boolean nonBreakingTrailerAhead(int i) {
        if (tokenType(++i) == ANTLRv4Lexer.WS) {
            if (tokenText(tokens.get(i)).contains("\n")) {
                return false;
            }
            ++i;
        }
        return tokenType(i) == ANTLRv4Lexer.LINE_COMMENT
            || tokenType(i) == ANTLRv4Lexer.RARROW
            || tokenType(i) == ANTLRv4Lexer.LPAREN;
    }

    private void processFormattingCommands(int index) {
        String text = tokenText(tokens.get(index));
        if (text.startsWith("//")) {
            text = text.substring(2).trim();
        } else if (text.startsWith("/*")) {
            Matcher matcher = COMMENT_BODY_PATTERN.matcher(text);
            if (matcher.find()) {
                text = matcher.group(2).trim();
            }
        }

        if (!text.startsWith(FORMAT_INTRODUCER)) {
            return;
        }

        String[] lines = text.substring(FORMAT_INTRODUCER.length() + 1).split("\\n", -1);
        for (int i = 0; i < lines.length; i++) {
            lines[i] = TRAILING_WHITESPACE.matcher(LEADING_STARS.matcher(lines[i]).replaceFirst(""))
                .replaceFirst("");
        }

        for (String entry : String.join(",", lines).split(",")) {
            Matcher groups = COMMAND_ENTRY_PATTERN.matcher(entry.trim());
            if (!groups.matches()) {
                continue;
            }
            containsFormattingOptions = true;
            String key = groups.group(1);
            String value = groups.group(2);
            if (value == null) {
                value = "";
            }

            switch (key) {
                case "reset" -> setDefaultOptions();
                case "on", "true" -> {
                    formattingDisabled = false;
                    if (rangeStart > -1) {
                        addRaw(rangeStart, index - 1);
                    }
                }
                case "off", "false" -> {
                    formattingDisabled = true;
                    rangeStart = index;
                }
                case "alignTrailingComments", "allowShortBlocksOnASingleLine", "breakBeforeBraces",
                    "keepEmptyLinesAtTheStartOfBlocks", "reflowComments", "spaceBeforeAssignmentOperators",
                    "useTab", "allowShortRulesOnASingleLine", "singleLineOverrulesHangingColon",
                    "breakBeforeParens", "ruleInternalsOnSingleLine", "groupedAlignments", "alignFirstTokens",
                    "alignLexerCommands", "alignActions", "alignLabels", "alignTrailers" -> {
                    if ("true".equals(value) || "false".equals(value) || "on".equals(value) || "off".equals(value)) {
                        boolean boolValue = "true".equals(value) || "on".equals(value);
                        setBooleanOption(key, boolValue);
                    } else {
                        add(MARKER_ERROR);
                    }
                }
                case "columnLimit", "continuationIndentWidth", "indentWidth", "maxEmptyLinesToKeep", "tabWidth",
                    "minEmptyLines" -> {
                    try {
                        setIntOption(key, Integer.parseInt(value));
                    } catch (NumberFormatException e) {
                        add(MARKER_ERROR);
                    }
                }
                case "alignColons" -> {
                    if ("none".equals(value)) {
                        options.alignColons = ColonAlignment.NONE;
                    } else if ("trailing".equals(value)) {
                        options.alignColons = ColonAlignment.TRAILING;
                    } else if ("hanging".equals(value)) {
                        options.alignColons = ColonAlignment.HANGING;
                    } else {
                        add(MARKER_ERROR);
                    }
                }
                case "alignSemicolons" -> {
                    if ("none".equals(value)) {
                        options.alignSemicolons = SemicolonAlignment.NONE;
                    } else if ("ownLine".equals(value)) {
                        options.alignSemicolons = SemicolonAlignment.OWN_LINE;
                    } else if ("hanging".equals(value)) {
                        options.alignSemicolons = SemicolonAlignment.HANGING;
                    } else {
                        add(MARKER_ERROR);
                    }
                }
                default -> add(MARKER_ERROR);
            }
        }
    }

    private void setBooleanOption(String key, boolean value) {
        switch (key) {
            case "alignTrailingComments" -> {
                options.alignTrailingComments = value;
                resetAlignmentStatus(AlignmentType.TRAILING_COMMENT);
            }
            case "allowShortBlocksOnASingleLine" -> options.allowShortBlocksOnASingleLine = value;
            case "breakBeforeBraces" -> options.breakBeforeBraces = value;
            case "keepEmptyLinesAtTheStartOfBlocks" -> options.keepEmptyLinesAtTheStartOfBlocks = value;
            case "reflowComments" -> options.reflowComments = value;
            case "spaceBeforeAssignmentOperators" -> options.spaceBeforeAssignmentOperators = value;
            case "useTab" -> options.useTab = value;
            case "allowShortRulesOnASingleLine" -> options.allowShortRulesOnASingleLine = value;
            case "singleLineOverrulesHangingColon" -> options.singleLineOverrulesHangingColon = value;
            case "breakBeforeParens" -> options.breakBeforeParens = value;
            case "ruleInternalsOnSingleLine" -> options.ruleInternalsOnSingleLine = value;
            case "groupedAlignments" -> {
                options.groupedAlignments = value;
                resetAlignmentStatus(ALL_ALIGNMENTS);
            }
            case "alignFirstTokens" -> {
                options.alignFirstTokens = value;
                resetAlignmentStatus(AlignmentType.FIRST_TOKEN);
            }
            case "alignLexerCommands" -> {
                options.alignLexerCommands = value;
                resetAlignmentStatus(AlignmentType.LEXER_COMMAND);
            }
            case "alignActions" -> {
                options.alignActions = value;
                resetAlignmentStatus(AlignmentType.ACTION);
            }
            case "alignLabels" -> {
                options.alignLabels = value;
                resetAlignmentStatus(AlignmentType.LABEL);
            }
            case "alignTrailers" -> {
                options.alignTrailers = value;
                resetAlignmentStatus(AlignmentType.TRAILERS);
            }
            default -> {
            }
        }
    }

    private void setIntOption(String key, int value) {
        switch (key) {
            case "columnLimit" -> options.columnLimit = value;
            case "continuationIndentWidth" -> options.continuationIndentWidth = value;
            case "indentWidth" -> options.indentWidth = value;
            case "maxEmptyLinesToKeep" -> options.maxEmptyLinesToKeep = value;
            case "tabWidth" -> options.tabWidth = value;
            case "minEmptyLines" -> options.minEmptyLines = value;
            default -> add(MARKER_ERROR);
        }
    }

    private void resetAlignmentStatus(AlignmentType... types) {
        for (AlignmentType type : types) {
            AlignmentStatus status = alignments.get(type);
            if (status != null) {
                status.lastLine = -1;
            }
        }
    }

    private void addAlignmentEntry(AlignmentType type) {
        AlignmentStatus status = alignments.computeIfAbsent(type, t -> new AlignmentStatus());
        if (status.lastLine != currentLine) {
            if (lineHasLeadingNonWhitespaceContent()) {
                removeTrailingTabsAndSpaces();
            }
            boolean startNewGroup = true;
            if (status.lastLine > -1) {
                if (!Boolean.TRUE.equals(options.groupedAlignments) || status.lastLine + 1 == currentLine) {
                    startNewGroup = false;
                    status.groups.get(status.groups.size() - 1).add(outputPipeline.size());
                }
            }
            if (startNewGroup) {
                List<Integer> group = new ArrayList<>();
                group.add(outputPipeline.size());
                status.groups.add(group);
            }
            outputPipeline.add(MARKER_ALIGNMENT);
            status.lastLine = currentLine;
        }
    }

    private void computeAlignments() {
        for (AlignmentType type : ALL_ALIGNMENTS) {
            AlignmentStatus alignment = alignments.get(type);
            if (alignment == null) {
                continue;
            }
            for (List<Integer> group : alignment.groups) {
                if (group.size() == 1) {
                    int index = group.get(0);
                    if (index < outputPipeline.size()) {
                        if (entryIs(index - 1, MARKER_WHITESPACE) || entryIs(index - 1, ANTLRv4Lexer.LPAREN)) {
                            outputPipeline.set(index, MARKER_WHITESPACE_ERASER);
                        } else if (entryIs(index + 2, ANTLRv4Lexer.COLON)) {
                            outputPipeline.set(index, MARKER_WHITESPACE_ERASER);
                        } else {
                            outputPipeline.set(index, MARKER_SPACE);
                        }
                    }
                    continue;
                }

                List<Integer> columns = new ArrayList<>();
                for (int member : group) {
                    if (member < outputPipeline.size()) {
                        columns.add(columnForEntry(member));
                    }
                }
                int maxColumn = columns.stream().mapToInt(Integer::intValue).max().orElse(0);
                if (Boolean.TRUE.equals(options.useTab)) {
                    maxColumn += options.tabWidth - (maxColumn % options.tabWidth);
                } else {
                    ++maxColumn;
                }

                for (int i = 0; i < group.size(); ++i) {
                    int whitespaceIndex = MARKER_WHITESPACE_BLOCK - whitespaceList.size();
                    outputPipeline.set(group.get(i), whitespaceIndex);
                    String whitespaces;
                    if (Boolean.TRUE.equals(options.useTab)) {
                        int tabCount = (maxColumn - columns.get(i)) / options.tabWidth;
                        if ((maxColumn - columns.get(i)) % options.tabWidth != 0) {
                            ++tabCount;
                        }
                        whitespaces = "\t".repeat(tabCount);
                    } else {
                        whitespaces = " ".repeat(maxColumn - columns.get(i));
                    }
                    whitespaceList.add(whitespaces);
                }
            }
        }
    }

    private int columnForEntry(int offset) {
        int result = 0;
        int run = offset;
        while (--run > -1) {
            if (outputPipeline.get(run) == MARKER_LINE_BREAK) {
                break;
            }
        }
        StringBuilder text = new StringBuilder();
        while (++run < offset) {
            int entry = outputPipeline.get(run);
            switch (entry) {
                case MARKER_SPACE -> text.append(' ');
                case MARKER_TAB -> text.append('\t');
                case MARKER_WHITESPACE_ERASER, MARKER_ERROR -> {
                }
                default -> {
                    if (entry < 0) {
                        if (isRangeBlock(entry)) {
                            int rangeIndex = -(entry - MARKER_RANGE);
                            int[] range = ranges.get(rangeIndex);
                            text.append(sourceText, range[0], range[1] + 1);
                        } else if (isWhitespaceBlock(entry)) {
                            int whitespaceIndex = -(entry - MARKER_WHITESPACE_BLOCK);
                            text.append(whitespaceList.get(whitespaceIndex));
                        }
                    } else {
                        text.append(tokenText(tokens.get(entry)));
                    }
                }
            }
        }
        for (char c : text.toString().toCharArray()) {
            if (c == '\t') {
                result += options.tabWidth - (result % options.tabWidth);
            } else {
                ++result;
            }
        }
        return result;
    }

    private String reflowComment(String comment, int type) {
        List<String> result = new ArrayList<>();
        String lineIntroducer = type == ANTLRv4Lexer.LINE_COMMENT ? "// " : " * ";
        List<String> lines = new ArrayList<>(Arrays.asList(comment.split("\\n", -1)));

        int lineIndex = 0;
        List<String> pipeline = splitWords(lines.get(lineIndex++));
        String line;
        if (type != ANTLRv4Lexer.LINE_COMMENT) {
            if (!lines.get(1).trim().startsWith("*")) {
                lineIntroducer = " ";
            }
            String last = lines.get(lines.size() - 1).trim();
            last = last.substring(0, Math.max(0, last.length() - 2));
            if (last.isEmpty()) {
                lines.remove(lines.size() - 1);
            } else {
                lines.set(lines.size() - 1, last);
            }
        }

        boolean isFirst = false;
        if (pipeline.size() == 1) {
            result.add(pipeline.get(0));
            line = lineIntroducer;
            isFirst = true;
        } else {
            line = pipeline.get(0) + " ";
        }

        int index = 1;
        int column = computeLineLength(line);
        while (true) {
            while (index < pipeline.size()) {
                if (currentColumn + column + pipeline.get(index).length() > options.columnLimit) {
                    result.add(line.substring(0, line.length() - 1));
                    line = lineIntroducer;
                    column = computeLineLength(line);
                }
                line += pipeline.get(index++) + " ";
                column = computeLineLength(line);
            }
            if (lineIndex == lines.size()) {
                break;
            }
            pipeline = splitWords(lines.get(lineIndex++));
            index = 0;
            if (!pipeline.isEmpty()) {
                String first = pipeline.get(0);
                if (type == ANTLRv4Lexer.LINE_COMMENT) {
                    if ("//".equals(first)) {
                        pipeline = new ArrayList<>(pipeline.subList(1, pipeline.size()));
                    } else {
                        pipeline.set(0, first.substring(2));
                    }
                } else if ("*".equals(first)) {
                    pipeline = new ArrayList<>(pipeline.subList(1, pipeline.size()));
                } else if (first.startsWith("*")) {
                    pipeline.set(0, first.substring(1));
                }
            }
            if (pipeline.isEmpty()) {
                if (!isFirst) {
                    result.add(line.substring(0, line.length() - 1));
                }
                result.add(lineIntroducer);
                line = lineIntroducer;
            }
            isFirst = false;
        }

        if (!line.isEmpty()) {
            result.add(line.substring(0, line.length() - 1));
        }
        if (type != ANTLRv4Lexer.LINE_COMMENT) {
            result.add(" */");
        }
        String indentation = Boolean.TRUE.equals(options.useTab)
            ? "\t".repeat(currentIndentation)
            : " ".repeat(currentIndentation * options.indentWidth);
        return String.join("\n" + indentation, result);
    }

    private boolean isRangeBlock(int marker) {
        return marker <= MARKER_RANGE && marker > MARKER_ALIGNMENT;
    }

    private boolean isWhitespaceBlock(int marker) {
        return marker <= MARKER_WHITESPACE_BLOCK;
    }

    private static List<String> splitWords(String line) {
        return Arrays.stream(line.split("[ \\t]"))
            .filter(entry -> !entry.isEmpty())
            .collect(java.util.stream.Collectors.toCollection(ArrayList::new));
    }

    private int tokenType(int index) {
        return tokens.get(index).getType();
    }

    private int tokenLine(int index) {
        return tokens.get(index).getLine();
    }

    private int tokenColumn(int index) {
        return tokens.get(index).getCharPositionInLine();
    }

    private int tokenStart(int index) {
        return tokens.get(index).getStartIndex();
    }

    private int tokenStop(int index) {
        return tokens.get(index).getStopIndex();
    }

    private static String tokenText(Token token) {
        return token.getText() == null ? "" : token.getText();
    }

    private boolean containsFormattingOptions() {
        for (Token token : tokens) {
            if ((token.getType() == ANTLRv4Lexer.LINE_COMMENT || token.getType() == ANTLRv4Lexer.BLOCK_COMMENT
                || token.getType() == ANTLRv4Lexer.DOC_COMMENT) && tokenText(token).contains(FORMAT_INTRODUCER)) {
                return true;
            }
        }
        return false;
    }

    private static String extractSource(List<Token> tokens) {
        if (tokens.isEmpty()) {
            return "";
        }
        Token first = tokens.get(0);
        if (first instanceof CommonToken commonToken) {
            CharStream inputStream = commonToken.getInputStream();
            if (inputStream != null && inputStream.size() > 0) {
                return inputStream.getText(Interval.of(0, inputStream.size() - 1));
            }
        }
        StringBuilder builder = new StringBuilder();
        for (Token token : tokens) {
            if (token.getType() != Token.EOF) {
                builder.append(tokenText(token));
            }
        }
        return builder.toString();
    }

    private static void append(List<String> entries, String key, Object value) {
        if (value != null) {
            entries.add(key + " " + value);
        }
    }

}


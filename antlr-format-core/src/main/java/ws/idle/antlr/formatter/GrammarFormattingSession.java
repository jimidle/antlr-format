package ws.idle.antlr.formatter;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import org.antlr.v4.runtime.Token;
import ws.idle.antlr.formatter.lexer.ANTLRv4Lexer;

final class GrammarFormattingSession {

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

    private final FormatterTokenStream tokenStream;
    private final FormatterAlignments alignments = new FormatterAlignments();
    private boolean addOptionsAsComment;

    private FormattingOptions options;
    private List<Integer> outputPipeline;
    private int currentIndentation;
    private boolean formattingDisabled;
    private int currentLine;
    private int currentColumn;
    private int singleLineBlockNesting;
    private List<int[]> ranges;
    private int currentRangeIndex;
    private int rangeStart;
    private List<String> whitespaceList;
    private boolean containsFormattingOptions;

    private record BlockInfo(boolean containsAlts, int singleLineLength) {
    }

    GrammarFormattingSession(FormatterTokenStream tokenStream, boolean addOptionsAsComment) {
        this.tokenStream = tokenStream;
        this.addOptionsAsComment = addOptionsAsComment;
    }

    FormattingResult format(FormattingOptions formattingOptions, Integer start, Integer stop) {
        if (tokenStream.isEmpty() || Boolean.TRUE.equals(formattingOptions.disabled)) {
            return new FormattingResult("", -1, -1);
        }

        initializeOptions(formattingOptions);
        initializeSessionState();

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
                if (tokenText(tokenAt(runningIndex--)).contains(FORMAT_INTRODUCER)) {
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
                        ++currentIndentation;
                        inRule = true;
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
            Token token = tokenAt(i);

            if (token.getType() != ANTLRv4Lexer.WS && lastEntryIs(MARKER_WHITESPACE_ERASER)) {
                outputPipeline.removeLast();
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

                    int nextType = tokenAt(i + 1).getType();
                    boolean localCommentAhead = nextType == ANTLRv4Lexer.LINE_COMMENT
                        || nextType == ANTLRv4Lexer.BLOCK_COMMENT || nextType == ANTLRv4Lexer.DOC_COMMENT;

                    if (lastEntryIs(MARKER_WHITESPACE_ERASER)) {
                        outputPipeline.removeLast();
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
                    if (localCommentAhead && lastCodeTokenIsLeftParen()
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
                    if (requiresLineBreakAfterTrailer(i)) {
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
                        addAlignmentEntry(FormatterAlignmentType.TRAILERS);
                    } else if (Boolean.TRUE.equals(this.options.alignActions)) {
                        addAlignmentEntry(FormatterAlignmentType.ACTION);
                    }
                    add(i++);
                    if (inCatchFinally && !"\n".equals(tokenText(tokenAt(i)))) {
                        addLineBreak(false);
                    }
                    int actionStart = i;
                    while (i <= endIndex && tokenType(i) != Token.EOF && tokenType(i) != ANTLRv4Lexer.END_ACTION) {
                        ++i;
                    }
                    addRaw(actionStart, i - 1);
                    if (i <= endIndex) {
                        if (inCatchFinally && !"\n".equals(tokenText(tokenAt(i - 1)))) {
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
                    boolean hasLineContent = lineHasLeadingNonWhitespaceContent();
                    StringBuilder comment = new StringBuilder(tokenText(token));
                    if (hasLineContent) {
                        if (token.getType() == ANTLRv4Lexer.LINE_COMMENT) {
                            if (Boolean.TRUE.equals(this.options.alignTrailers)) {
                                addAlignmentEntry(FormatterAlignmentType.TRAILERS);
                            } else if (Boolean.TRUE.equals(this.options.alignTrailingComments)) {
                                addAlignmentEntry(FormatterAlignmentType.TRAILING_COMMENT);
                            }
                        }
                    } else if (!comment.toString().contains(FORMAT_INTRODUCER)
                        && Boolean.TRUE.equals(this.options.reflowComments)
                        && token.getType() == ANTLRv4Lexer.LINE_COMMENT) {
                        while (true) {
                            Token nextToken = tokenAt(i + 1);
                            if (nextToken.getType() == Token.EOF) {
                                break;
                            }
                            String content = tokenText(nextToken);
                            if (content.split("\\n", -1).length > 2) {
                                break;
                            }
                            nextToken = tokenAt(i + 2);
                            if (nextToken.getType() != ANTLRv4Lexer.LINE_COMMENT || tokenText(nextToken).contains(FORMAT_INTRODUCER)) {
                                break;
                            }
                            comment.append('\n').append(tokenText(nextToken));
                            i += 2;
                            processFormattingCommands(i);
                        }
                    }

                    String commentText = comment.toString();
                    if (Boolean.TRUE.equals(this.options.reflowComments) && commentText.contains("\n")) {
                        String formatted = reflowComment(commentText, token.getType());
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
                            if (requiresLineBreakAfterTrailer(i) && !inSingleLineRule) {
                                addLineBreak(false);
                                pushCurrentIndentation(false);
                            } else {
                                addSpace();
                            }
                        }
                        case TRAILING -> {
                            if (!lastRealTokenIsLineComment()) {
                                removeTrailingWhitespaces();
                            }
                            if (singleLineBlockNesting > 0) {
                                addAlignmentEntry(FormatterAlignmentType.COLON);
                                add(MARKER_WHITESPACE_ERASER);
                            }
                            add(i);
                            if (requiresLineBreakAfterTrailer(i) && !inSingleLineRule) {
                                addLineBreak(false);
                                pushCurrentIndentation(false);
                            } else {
                                addSpace();
                            }
                        }
                    }

                    if (Boolean.TRUE.equals(this.options.alignFirstTokens) && inSingleLineRule) {
                        removeTrailingWhitespaces();
                        addAlignmentEntry(FormatterAlignmentType.FIRST_TOKEN);
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
                    } else if (!inSingleLineRule) {
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
                        addAlignmentEntry(FormatterAlignmentType.TRAILERS);
                    } else if (Boolean.TRUE.equals(this.options.alignLexerCommands)) {
                        addAlignmentEntry(FormatterAlignmentType.LEXER_COMMAND);
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
                        if (requiresLineBreakAfterTrailer(i)) {
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
                            addAlignmentEntry(FormatterAlignmentType.TRAILERS);
                        } else if (Boolean.TRUE.equals(this.options.alignLabels)) {
                            willUseAlignment = true;
                            addAlignmentEntry(FormatterAlignmentType.LABEL);
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
                        result.append(tokenText(tokenAt(pendingLineComment)));
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
                        result.append(FormatterComments.convertToComment(options));
                    }
                }
                default -> {
                    if (entry < 0) {
                        if (isWhitespaceBlock(entry)) {
                            result.append(whitespaceList.get(-(entry - MARKER_WHITESPACE_BLOCK)));
                        } else if (isRangeBlock(entry)) {
                            int rangeIndex = -(entry - MARKER_RANGE);
                            int[] range = ranges.get(rangeIndex);
                            result.append(tokenStream.sourceText(), range[0], range[1] + 1);
                        }
                    } else if (tokenType(entry) == ANTLRv4Lexer.LINE_COMMENT) {
                        pendingLineComment = entry;
                    } else {
                        result.append(tokenText(tokenAt(entry)));
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
            result.append(tokenText(tokenAt(pendingLineComment)));
        }

        return new FormattingResult(result.toString(), targetStart, targetStop);
    }

    private void initializeOptions(FormattingOptions formattingOptions) {
        options = FormattingOptions.defaults();
        options.mergeFrom(formattingOptions);
        if (options.columnLimit <= 0) {
            options.columnLimit = 1_000_000_000;
        }
    }

    private void initializeSessionState() {
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
    }

    private boolean entryIs(int index, int marker) {
        if (index < 0 || index >= outputPipeline.size()) {
            return false;
        }

        int entry = outputPipeline.get(index);
        return switch (marker) {
            case MARKER_WHITESPACE -> entry == MARKER_LINE_BREAK || entry == MARKER_SPACE || entry == MARKER_TAB;
            case MARKER_SPACE -> entry == MARKER_SPACE;
            case MARKER_TAB -> entry == MARKER_TAB;
            case MARKER_LINE_BREAK -> entry == MARKER_LINE_BREAK;
            case MARKER_COMMENT -> entry >= 0 && (tokenType(entry) == ANTLRv4Lexer.BLOCK_COMMENT
                || tokenType(entry) == ANTLRv4Lexer.LINE_COMMENT
                || tokenType(entry) == ANTLRv4Lexer.DOC_COMMENT);
            default -> entry < 0 ? entry == marker : tokenType(entry) == marker;
        };
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
        return index > 0 && outputPipeline.get(index) != MARKER_LINE_BREAK;
    }

    private boolean lastCodeTokenIsLeftParen() {
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
        return i >= 0 && outputPipeline.get(i) >= 0 && tokenType(outputPipeline.get(i)) == ANTLRv4Lexer.LPAREN;
    }

    private boolean lastRealTokenIsLineComment() {
        int i = outputPipeline.size() - 1;
        while (i >= 0) {
            if (!entryIs(i, MARKER_WHITESPACE_ERASER)
                && !entryIs(i, MARKER_WHITESPACE)
                && !entryIs(i, MARKER_LINE_BREAK)) {
                break;
            }
            --i;
        }
        return i >= 0 && outputPipeline.get(i) >= 0 && tokenType(outputPipeline.get(i)) == ANTLRv4Lexer.LINE_COMMENT;
    }

    private void removeLastEntry() {
        if (formattingDisabled || outputPipeline.isEmpty()) {
            return;
        }

        int lastEntry = outputPipeline.removeLast();
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

        Token token = marker >= 0 && marker < tokenCount() ? tokenAt(marker) : null;
        if (token == null) {
            ++currentColumn;
            outputPipeline.add(marker);
            return;
        }

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
                if (currentColumn + tokenLength > options.columnLimit && lineHasLeadingNonWhitespaceContent()) {
                    applyLineContinuation();
                }
                currentColumn += tokenLength;
                outputPipeline.add(marker);
            }
        }
    }

    private int tokenFromIndex(int charIndex, boolean first) {
        return tokenStream.tokenIndexForCharIndex(charIndex, first);
    }

    private int computeLineLength(String text) {
        return FormatterComments.computeLineLength(text, options.tabWidth, currentColumn);
    }

    private void addRaw(int start, int stop) {
        String text = tokenStream.sourceSlice(tokenStart(start), tokenStop(stop));
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
        Token token = tokenAt(i);
        if (token.getType() == ANTLRv4Lexer.COLON || token.getType() == ANTLRv4Lexer.OR) {
            ++singleLineLength;
        }

        while (++i < tokenCount()) {
            token = tokenAt(i);
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
                    return new BlockInfo(containsAlts, 1_000_000_000);
                }
                case ANTLRv4Lexer.BLOCK_COMMENT, ANTLRv4Lexer.DOC_COMMENT -> {
                    if (tokenText(token).contains("\n")) {
                        return new BlockInfo(containsAlts, 1_000_000_000);
                    }
                    singleLineLength += tokenText(token).length() + 1;
                }
                case ANTLRv4Lexer.BEGIN_ACTION, ANTLRv4Lexer.ACTION_CONTENT, ANTLRv4Lexer.END_ACTION -> {
                    if ("\n".equals(tokenText(token))) {
                        return new BlockInfo(containsAlts, 1_000_000_000);
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
        while (++i < tokenCount() && tokenType(i) == ANTLRv4Lexer.WS) {
            if (tokenText(tokenAt(i)).contains("\n")) {
                return new BlockInfo(containsAlts, singleLineLength);
            }
        }
        if (i < tokenCount() && tokenType(i) == ANTLRv4Lexer.LINE_COMMENT) {
            singleLineLength += tokenText(tokenAt(i)).length();
        }
        return new BlockInfo(containsAlts, singleLineLength);
    }

    private boolean requiresLineBreakAfterTrailer(int i) {
        if (tokenType(++i) == ANTLRv4Lexer.WS) {
            if (tokenText(tokenAt(i)).contains("\n")) {
                return true;
            }
            ++i;
        }
        return tokenType(i) != ANTLRv4Lexer.LINE_COMMENT
            && tokenType(i) != ANTLRv4Lexer.RARROW
            && tokenType(i) != ANTLRv4Lexer.LPAREN;
    }

    private void processFormattingCommands(int index) {
        FormatterDirectiveParser.ParseResult parseResult = FormatterDirectiveParser.parse(tokenText(tokenAt(index)));
        if (!parseResult.containsFormattingOptions()) {
            return;
        }

        containsFormattingOptions = true;
        for (FormatterDirectiveParser.Directive directive : parseResult.directives()) {
            switch (directive) {
                case FormatterDirectiveParser.ResetDirective ignored -> initializeOptions(new FormattingOptions());
                case FormatterDirectiveParser.ToggleFormattingDirective toggle -> {
                    formattingDisabled = !toggle.enabled();
                    if (toggle.enabled() && rangeStart > -1) {
                        addRaw(rangeStart, index - 1);
                    }
                    if (!toggle.enabled()) {
                        rangeStart = index;
                    }
                }
                case FormatterDirectiveParser.BooleanOptionDirective booleanOption ->
                    setBooleanOption(booleanOption.key(), booleanOption.value());
                case FormatterDirectiveParser.IntOptionDirective intOption ->
                    setIntOption(intOption.key(), intOption.value());
                case FormatterDirectiveParser.ColonAlignmentDirective colonAlignment ->
                    options.alignColons = colonAlignment.value();
                case FormatterDirectiveParser.SemicolonAlignmentDirective semicolonAlignment ->
                    options.alignSemicolons = semicolonAlignment.value();
                case FormatterDirectiveParser.InvalidDirective ignored -> add(MARKER_ERROR);
            }
        }
    }

    private void setBooleanOption(String key, boolean value) {
        switch (key) {
            case "alignTrailingComments" -> {
                options.alignTrailingComments = value;
                resetAlignmentStatus(FormatterAlignmentType.TRAILING_COMMENT);
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
                resetAlignmentStatus(FormatterAlignmentType.values());
            }
            case "alignFirstTokens" -> {
                options.alignFirstTokens = value;
                resetAlignmentStatus(FormatterAlignmentType.FIRST_TOKEN);
            }
            case "alignLexerCommands" -> {
                options.alignLexerCommands = value;
                resetAlignmentStatus(FormatterAlignmentType.LEXER_COMMAND);
            }
            case "alignActions" -> {
                options.alignActions = value;
                resetAlignmentStatus(FormatterAlignmentType.ACTION);
            }
            case "alignLabels" -> {
                options.alignLabels = value;
                resetAlignmentStatus(FormatterAlignmentType.LABEL);
            }
            case "alignTrailers" -> {
                options.alignTrailers = value;
                resetAlignmentStatus(FormatterAlignmentType.TRAILERS);
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

    private void resetAlignmentStatus(FormatterAlignmentType... types) {
        alignments.reset(types);
    }

    private void addAlignmentEntry(FormatterAlignmentType type) {
        alignments.addEntry(type, currentLine, lineHasLeadingNonWhitespaceContent(),
            Boolean.TRUE.equals(options.groupedAlignments), outputPipeline, this::removeTrailingTabsAndSpaces,
            MARKER_ALIGNMENT);
    }

    private void computeAlignments() {
        alignments.compute(outputPipeline, whitespaceList, options, this::columnForEntry, this::entryIs,
            MARKER_WHITESPACE_BLOCK, MARKER_WHITESPACE, MARKER_WHITESPACE_ERASER, MARKER_SPACE,
            ANTLRv4Lexer.LPAREN, ANTLRv4Lexer.COLON);
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
                            text.append(tokenStream.sourceText(), range[0], range[1] + 1);
                        } else if (isWhitespaceBlock(entry)) {
                            int whitespaceIndex = -(entry - MARKER_WHITESPACE_BLOCK);
                            text.append(whitespaceList.get(whitespaceIndex));
                        }
                    } else {
                        text.append(tokenText(tokenAt(entry)));
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
        return FormatterComments.reflowComment(comment, type, options, currentColumn, currentIndentation);
    }

    private boolean isRangeBlock(int marker) {
        return marker <= MARKER_RANGE && marker > MARKER_ALIGNMENT;
    }

    private boolean isWhitespaceBlock(int marker) {
        return marker <= MARKER_WHITESPACE_BLOCK;
    }

    private int tokenType(int index) {
        return tokenStream.type(index);
    }

    private int tokenLine(int index) {
        return tokenStream.line(index);
    }

    private int tokenColumn(int index) {
        return tokenStream.column(index);
    }

    private int tokenStart(int index) {
        return tokenStream.start(index);
    }

    private int tokenStop(int index) {
        return tokenStream.stop(index);
    }

    private static String tokenText(Token token) {
        return FormatterTokenStream.text(token);
    }

    private Token tokenAt(int index) {
        return tokenStream.token(index);
    }

    private int tokenCount() {
        return tokenStream.size();
    }
}


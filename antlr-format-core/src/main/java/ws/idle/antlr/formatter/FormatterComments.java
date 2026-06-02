package ws.idle.antlr.formatter;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;
import ws.idle.antlr.formatter.lexer.ANTLRv4Lexer;

final class FormatterComments {

    /** Prevents instantiation of the utility class. */
    private FormatterComments() {
    }

    /**
     * Serializes formatting options into one or more {@code $antlr-format} line comments.
     *
     * @param options the options to serialize
     * @return the generated formatter comment block, including surrounding blank lines
     */
    static String convertToComment(FormattingOptions options) {
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

        List<String> lines = new ArrayList<>();
        StringBuilder line = new StringBuilder();
        for (String entry : entries) {
            int separatorLength = line.isEmpty() ? 0 : 2;
            if (!line.isEmpty() && line.length() + separatorLength + entry.length() > 130) {
                lines.add("// $antlr-format " + line);
                line.setLength(0);
            }

            if (!line.isEmpty()) {
                line.append(", ");
            }
            line.append(entry);
        }

        if (!line.isEmpty()) {
            lines.add("// $antlr-format " + line);
        }

        return "\n" + String.join("\n", lines) + "\n\n";
    }

    /**
     * Computes the rendered width of text, taking tab expansion into account.
     *
     * @param text the text to measure
     * @param tabWidth the configured tab width
     * @param currentColumn the column at which the text will start
     * @return the number of columns occupied by the text
     */
    static int computeLineLength(String text, int tabWidth, int currentColumn) {
        int length = 0;
        for (char ch : text.toCharArray()) {
            if (ch == '\t') {
                int offsetToNextTabStop = tabWidth - (currentColumn % tabWidth);
                length += offsetToNextTabStop;
            } else {
                ++length;
            }
        }
        return length;
    }

    /**
     * Reflows a line, block, or doc comment to fit within the configured column limit.
     *
     * @param comment the original comment text
     * @param type the lexer token type describing the comment kind
     * @param options the active formatting options
     * @param currentColumn the current output column before the comment is emitted
     * @param currentIndentation the current indentation depth
     * @return the reformatted comment text
     */
    static String reflowComment(String comment, int type, FormattingOptions options, int currentColumn,
                                int currentIndentation) {
        List<String> result = type == ANTLRv4Lexer.LINE_COMMENT
            ? reflowLineComment(comment, options, currentColumn)
            : reflowBlockComment(comment, options, currentColumn);
        String indentation = Boolean.TRUE.equals(options.useTab)
            ? "\t".repeat(currentIndentation)
            : " ".repeat(currentIndentation * options.indentWidth);
        return String.join("\n" + indentation, result);
    }

    private static List<String> reflowLineComment(String comment, FormattingOptions options, int currentColumn) {
        List<String> result = new ArrayList<>();
        List<String> paragraph = new ArrayList<>();
        List<String> physicalLines = splitPhysicalLines(comment);
        String firstParagraphPrefix = extractLineCommentPrefix(physicalLines.getFirst());
        boolean firstParagraph = true;
        for (String rawLine : physicalLines) {
            String firstPrefix = firstParagraph ? firstParagraphPrefix : "// ";
            String content = stripLineCommentPrefix(rawLine);
            if (appendCommentContentLine(result, paragraph, content, firstPrefix, "// ", options, currentColumn)) {
            } else {
                firstParagraph = false;
            }
        }
        flushParagraph(result, paragraph, firstParagraph ? firstParagraphPrefix : "// ", "// ", options,
            currentColumn);
        return result;
    }

    private static List<String> reflowBlockComment(String comment, FormattingOptions options, int currentColumn) {
        List<String> lines = Arrays.asList(comment.split("\\n", -1));
        String opening = extractBlockOpening(lines.getFirst());
        boolean starPrefixedLines = lines.size() > 1 && lines.get(1).trim().startsWith("*");
        String lineIntroducer = starPrefixedLines ? " * " : " ";

        List<String> result = new ArrayList<>();
        List<String> paragraph = new ArrayList<>();
        String paragraphPrefix = null;

        String firstContent = stripBlockOpeningContent(lines.getFirst());
        if (firstContent.isEmpty()) {
            result.add(opening);
        } else if (shouldPreserveLine(firstContent)) {
            result.add(opening + " " + firstContent);
        } else {
            paragraphPrefix = opening + " ";
            paragraph.addAll(splitWords(firstContent));
        }

        for (int index = 1; index < lines.size() - 1; ++index) {
            flushParagraphIfNeeded(result, paragraph, paragraphPrefix, lineIntroducer, options, currentColumn,
                lines.get(index), starPrefixedLines);
            if (paragraph.isEmpty()) {
                paragraphPrefix = null;
            }
            String content = stripBlockBodyContent(lines.get(index), starPrefixedLines);
            if (appendCommentContentLine(result, paragraph, content,
                paragraphPrefix == null ? lineIntroducer : paragraphPrefix, lineIntroducer, options, currentColumn)) {
                paragraphPrefix = paragraphPrefix == null ? lineIntroducer : paragraphPrefix;
            } else {
                paragraphPrefix = null;
            }
        }

        if (lines.size() > 1) {
            String lastContent = stripBlockClosingContent(lines.getLast(), starPrefixedLines);
            if (lastContent.isEmpty()) {
                flushParagraph(result, paragraph, paragraphPrefix == null ? lineIntroducer : paragraphPrefix,
                    lineIntroducer, options, currentColumn);
                paragraphPrefix = null;
            } else if (appendCommentContentLine(result, paragraph, lastContent,
                paragraphPrefix == null ? lineIntroducer : paragraphPrefix, lineIntroducer, options,
                currentColumn)) {
                paragraphPrefix = paragraphPrefix == null ? lineIntroducer : paragraphPrefix;
            } else {
                paragraphPrefix = null;
            }
        }

        flushParagraph(result, paragraph, paragraphPrefix == null ? lineIntroducer : paragraphPrefix, lineIntroducer,
            options, currentColumn);
        result.add(" */");
        return result;
    }

    private static void flushParagraphIfNeeded(List<String> result, List<String> paragraph, String paragraphPrefix,
                                               String lineIntroducer, FormattingOptions options, int currentColumn,
                                               String rawLine, boolean starPrefixedLines) {
        String content = stripBlockBodyContent(rawLine, starPrefixedLines);
        if (content.isEmpty() || shouldPreserveLine(content)) {
            flushParagraph(result, paragraph, paragraphPrefix == null ? lineIntroducer : paragraphPrefix,
                lineIntroducer, options, currentColumn);
        }
    }

    private static boolean appendCommentContentLine(List<String> result, List<String> paragraph, String content,
                                                    String firstPrefix, String continuationPrefix,
                                                    FormattingOptions options, int currentColumn) {
        String trimmed = content.trim();
        if (trimmed.isEmpty()) {
            flushParagraph(result, paragraph, firstPrefix, continuationPrefix, options, currentColumn);
            result.add(blankCommentLine(continuationPrefix));
            return false;
        }
        if (shouldPreserveLine(trimmed)) {
            flushParagraph(result, paragraph, firstPrefix, continuationPrefix, options, currentColumn);
            result.add(continuationPrefix + trimmed);
            return false;
        }

        paragraph.addAll(splitWords(trimmed));
        return true;
    }

    private static void flushParagraph(List<String> result, List<String> paragraph, String firstPrefix,
                                       String continuationPrefix, FormattingOptions options, int currentColumn) {
        if (paragraph.isEmpty()) {
            return;
        }

        String prefix = firstPrefix;
        StringBuilder line = new StringBuilder(prefix);
        for (String word : paragraph) {
            boolean hasContent = line.length() > prefix.length();
            int currentWidth = computeLineLength(line.toString(), options.tabWidth, currentColumn);
            if (hasContent && currentColumn + currentWidth + word.length() > options.columnLimit) {
                result.add(trimTrailingWhitespace(line.toString()));
                prefix = continuationPrefix;
                line = new StringBuilder(prefix);
            }
            line.append(word).append(' ');
        }

        result.add(trimTrailingWhitespace(line.toString()));
        paragraph.clear();
    }

    private static String stripLineCommentPrefix(String line) {
        String trimmed = line.trim();
        if (trimmed.startsWith("//")) {
            return trimmed.substring(2).trim();
        }
        return trimmed;
    }

    private static String extractLineCommentPrefix(String line) {
        String trimmed = line.trim();
        if (trimmed.startsWith("//") && trimmed.length() > 2 && !Character.isWhitespace(trimmed.charAt(2))) {
            return "//";
        }
        return "// ";
    }

    private static String extractBlockOpening(String line) {
        String trimmed = line.trim();
        if (trimmed.startsWith("/**")) {
            return "/**";
        }
        return "/*";
    }

    private static String stripBlockOpeningContent(String line) {
        String trimmed = line.trim();
        if (trimmed.startsWith("/**")) {
            return trimmed.substring(3).trim();
        }
        if (trimmed.startsWith("/*")) {
            return trimmed.substring(2).trim();
        }
        return trimmed;
    }

    private static String stripBlockBodyContent(String line, boolean starPrefixedLines) {
        String trimmed = line.trim();
        if (trimmed.startsWith("*")) {
            return trimmed.substring(1).trim();
        }
        return trimmed;
    }

    private static String stripBlockClosingContent(String line, boolean starPrefixedLines) {
        String withoutClosing = line;
        int closingIndex = withoutClosing.lastIndexOf("*/");
        if (closingIndex >= 0) {
            withoutClosing = withoutClosing.substring(0, closingIndex);
        }
        return stripBlockBodyContent(withoutClosing, starPrefixedLines);
    }

    private static String blankCommentLine(String prefix) {
        if ("// ".equals(prefix) || " * ".equals(prefix) || " ".equals(prefix)) {
            return prefix;
        }
        if (prefix.endsWith(" ")) {
            return prefix.substring(0, prefix.length() - 1);
        }
        return prefix;
    }

    private static boolean shouldPreserveLine(String line) {
        List<String> words = splitWords(line);
        if (words.size() <= 1) {
            return true;
        }

        String trimmed = line.trim();
        return trimmed.startsWith("-")
            || trimmed.startsWith("* ")
            || trimmed.startsWith("+ ")
            || trimmed.startsWith("• ")
            || trimmed.startsWith("◦ ")
            || trimmed.startsWith("‣ ")
            || trimmed.matches("\\[[ xX]\\]\\s+.*")
            || trimmed.matches("\\d+[.)]\\s+.*");
    }

    private static List<String> splitPhysicalLines(String text) {
        List<String> lines = new ArrayList<>(Arrays.asList(text.split("\\n", -1)));
        while (!lines.isEmpty() && lines.getLast().isEmpty()) {
            lines.removeLast();
        }
        if (lines.isEmpty()) {
            lines.add("");
        }
        return lines;
    }

    private static String trimTrailingWhitespace(String text) {
        int index = text.length();
        while (index > 0 && Character.isWhitespace(text.charAt(index - 1))) {
            --index;
        }
        return text.substring(0, index);
    }

    /**
     * Splits a comment line into whitespace-delimited words.
     *
     * @param line the line to split
     * @return the non-empty words from the line
     */
    private static List<String> splitWords(String line) {
        return Arrays.stream(line.split("[ \\t]"))
            .filter(entry -> !entry.isEmpty())
            .collect(Collectors.toCollection(ArrayList::new));
    }

    /**
     * Adds a key/value formatter option entry when the value is present.
     *
     * @param entries the destination list
     * @param key the option name
     * @param value the option value
     */
    private static void append(List<String> entries, String key, Object value) {
        if (value != null) {
            entries.add(key + " " + value);
        }
    }
}


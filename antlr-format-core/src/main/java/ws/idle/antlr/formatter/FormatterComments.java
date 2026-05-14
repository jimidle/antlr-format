package ws.idle.antlr.formatter;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;
import ws.idle.antlr.formatter.lexer.ANTLRv4Lexer;

final class FormatterComments {

    private FormatterComments() {
    }

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

    static String reflowComment(String comment, int type, FormattingOptions options, int currentColumn,
                                int currentIndentation) {
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
            String last = lines.getLast().trim();
            last = last.substring(0, Math.max(0, last.length() - 2));
            if (last.isEmpty()) {
                lines.removeLast();
            } else {
                lines.set(lines.size() - 1, last);
            }
        }

        boolean isFirst = false;
        if (pipeline.size() == 1) {
            result.add(pipeline.getFirst());
            line = lineIntroducer;
            isFirst = true;
        } else {
            line = pipeline.getFirst() + " ";
        }

        int index = 1;
        int column = computeLineLength(line, options.tabWidth, currentColumn);
        while (true) {
            while (index < pipeline.size()) {
                if (currentColumn + column + pipeline.get(index).length() > options.columnLimit) {
                    result.add(line.substring(0, line.length() - 1));
                    line = lineIntroducer;
                }
                line += pipeline.get(index++) + " ";
                column = computeLineLength(line, options.tabWidth, currentColumn);
            }
            if (lineIndex == lines.size()) {
                break;
            }
            pipeline = splitWords(lines.get(lineIndex++));
            index = 0;
            if (!pipeline.isEmpty()) {
                String first = pipeline.getFirst();
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

    private static List<String> splitWords(String line) {
        return Arrays.stream(line.split("[ \\t]"))
            .filter(entry -> !entry.isEmpty())
            .collect(Collectors.toCollection(ArrayList::new));
    }

    private static void append(List<String> entries, String key, Object value) {
        if (value != null) {
            entries.add(key + " " + value);
        }
    }
}


package ws.idle.antlr.formatter;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

final class FormatterDirectiveParser {

    private static final String FORMAT_INTRODUCER = "$antlr-format";
    private static final Pattern COMMENT_BODY_PATTERN = Pattern.compile("/\\*(\\s*\\*?)*(.*)\\*/", Pattern.DOTALL);
    private static final Pattern COMMAND_ENTRY_PATTERN = Pattern.compile("(\\w+)(?:(?:\\s*:)?\\s*)?(\\w+|[0-9]+)?",
        Pattern.CASE_INSENSITIVE);
    private static final Pattern LEADING_STARS = Pattern.compile("^\\s*\\*+\\s*");
    private static final Pattern TRAILING_WHITESPACE = Pattern.compile("\\s*$");

    private static final Set<String> BOOLEAN_OPTIONS = Set.of(
        "alignTrailingComments", "allowShortBlocksOnASingleLine", "breakBeforeBraces",
        "keepEmptyLinesAtTheStartOfBlocks", "reflowComments", "spaceBeforeAssignmentOperators",
        "useTab", "allowShortRulesOnASingleLine", "singleLineOverrulesHangingColon",
        "breakBeforeParens", "ruleInternalsOnSingleLine", "groupedAlignments", "alignFirstTokens",
        "alignLexerCommands", "alignActions", "alignLabels", "alignTrailers"
    );

    private static final Set<String> INT_OPTIONS = Set.of(
        "columnLimit", "continuationIndentWidth", "indentWidth", "maxEmptyLinesToKeep", "tabWidth", "minEmptyLines"
    );

    record ParseResult(boolean containsFormattingOptions, List<Directive> directives) {
        static ParseResult none() {
            return new ParseResult(false, List.of());
        }
    }

    sealed interface Directive permits ResetDirective, ToggleFormattingDirective, BooleanOptionDirective,
        IntOptionDirective, ColonAlignmentDirective, SemicolonAlignmentDirective, InvalidDirective {
    }

    record ResetDirective() implements Directive {
    }

    record ToggleFormattingDirective(boolean enabled) implements Directive {
    }

    record BooleanOptionDirective(String key, boolean value) implements Directive {
    }

    record IntOptionDirective(String key, int value) implements Directive {
    }

    record ColonAlignmentDirective(ColonAlignment value) implements Directive {
    }

    record SemicolonAlignmentDirective(SemicolonAlignment value) implements Directive {
    }

    record InvalidDirective() implements Directive {
    }

    private FormatterDirectiveParser() {
    }

    static ParseResult parse(String commentText) {
        String text = stripCommentMarkup(commentText);
        if (!text.startsWith(FORMAT_INTRODUCER)) {
            return ParseResult.none();
        }

        if (text.length() > FORMAT_INTRODUCER.length()) {
            char next = text.charAt(FORMAT_INTRODUCER.length());
            if (!Character.isWhitespace(next) && next != ',' && next != ':') {
                return ParseResult.none();
            }
        }

        String remainder = text.substring(FORMAT_INTRODUCER.length()).stripLeading();
        if (!remainder.isEmpty() && (remainder.charAt(0) == ':' || remainder.charAt(0) == ',')) {
            remainder = remainder.substring(1).stripLeading();
        }
        if (remainder.isEmpty()) {
            return new ParseResult(true, List.of());
        }

        String[] lines = remainder.split("\\n", -1);
        for (int i = 0; i < lines.length; i++) {
            lines[i] = TRAILING_WHITESPACE.matcher(LEADING_STARS.matcher(lines[i]).replaceFirst("")).replaceFirst("");
        }

        boolean containsFormattingOptions = false;
        List<Directive> directives = new ArrayList<>();
        for (String entry : String.join(",", lines).split(",")) {
            Matcher groups = COMMAND_ENTRY_PATTERN.matcher(entry.trim());
            if (!groups.matches()) {
                continue;
            }
            containsFormattingOptions = true;
            directives.add(parseDirective(groups.group(1), groups.group(2) == null ? "" : groups.group(2)));
        }

        return new ParseResult(containsFormattingOptions, List.copyOf(directives));
    }

    private static Directive parseDirective(String key, String value) {
        return switch (key) {
            case "reset" -> new ResetDirective();
            case "on", "true" -> new ToggleFormattingDirective(true);
            case "off", "false" -> new ToggleFormattingDirective(false);
            case "alignColons" -> parseColonAlignment(value);
            case "alignSemicolons" -> parseSemicolonAlignment(value);
            default -> {
                if (BOOLEAN_OPTIONS.contains(key)) {
                    yield parseBooleanOption(key, value);
                }
                if (INT_OPTIONS.contains(key)) {
                    yield parseIntOption(key, value);
                }
                yield new InvalidDirective();
            }
        };
    }

    private static Directive parseBooleanOption(String key, String value) {
        if ("true".equals(value) || "on".equals(value)) {
            return new BooleanOptionDirective(key, true);
        }
        if ("false".equals(value) || "off".equals(value)) {
            return new BooleanOptionDirective(key, false);
        }
        return new InvalidDirective();
    }

    private static Directive parseIntOption(String key, String value) {
        try {
            return new IntOptionDirective(key, Integer.parseInt(value));
        } catch (NumberFormatException e) {
            return new InvalidDirective();
        }
    }

    private static Directive parseColonAlignment(String value) {
        return switch (value) {
            case "none" -> new ColonAlignmentDirective(ColonAlignment.NONE);
            case "trailing" -> new ColonAlignmentDirective(ColonAlignment.TRAILING);
            case "hanging" -> new ColonAlignmentDirective(ColonAlignment.HANGING);
            default -> new InvalidDirective();
        };
    }

    private static Directive parseSemicolonAlignment(String value) {
        return switch (value) {
            case "none" -> new SemicolonAlignmentDirective(SemicolonAlignment.NONE);
            case "ownLine" -> new SemicolonAlignmentDirective(SemicolonAlignment.OWN_LINE);
            case "hanging" -> new SemicolonAlignmentDirective(SemicolonAlignment.HANGING);
            default -> new InvalidDirective();
        };
    }

    private static String stripCommentMarkup(String commentText) {
        if (commentText.startsWith("//")) {
            return commentText.substring(2).trim();
        }
        if (commentText.startsWith("/*")) {
            Matcher matcher = COMMENT_BODY_PATTERN.matcher(commentText);
            if (matcher.find()) {
                return matcher.group(2).trim();
            }
        }
        return commentText;
    }
}


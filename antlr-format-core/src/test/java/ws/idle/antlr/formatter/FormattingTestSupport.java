package ws.idle.antlr.formatter;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

final class FormattingTestSupport {

    static final ObjectMapper MAPPER = new ObjectMapper();

    private FormattingTestSupport() {
    }

    static String readResource(String path) throws IOException {
        try (InputStream in = FormattingTestSupport.class.getClassLoader().getResourceAsStream(path)) {
            if (in == null) {
                throw new IOException("Resource not found: " + path);
            }
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    static FormattingOptions parseOptions(JsonNode node) {
        FormattingOptions options = new FormattingOptions();
        if (node == null || node.isNull()) {
            return options;
        }

        node.properties().forEach(entry -> {
            String key = entry.getKey();
            JsonNode value = entry.getValue();
            switch (key) {
                case "disabled" -> options.disabled = value.asBoolean();
                case "alignTrailingComments" -> options.alignTrailingComments = value.asBoolean();
                case "allowShortBlocksOnASingleLine" -> options.allowShortBlocksOnASingleLine = value.asBoolean();
                case "breakBeforeBraces" -> options.breakBeforeBraces = value.asBoolean();
                case "columnLimit" -> options.columnLimit = value.asInt();
                case "continuationIndentWidth" -> options.continuationIndentWidth = value.asInt();
                case "indentWidth" -> options.indentWidth = value.asInt();
                case "keepEmptyLinesAtTheStartOfBlocks" -> options.keepEmptyLinesAtTheStartOfBlocks = value.asBoolean();
                case "maxEmptyLinesToKeep" -> options.maxEmptyLinesToKeep = value.asInt();
                case "reflowComments" -> options.reflowComments = value.asBoolean();
                case "spaceBeforeAssignmentOperators" -> options.spaceBeforeAssignmentOperators = value.asBoolean();
                case "tabWidth" -> options.tabWidth = value.asInt();
                case "useTab" -> options.useTab = value.asBoolean();
                case "alignColons" -> options.alignColons = parseColonAlignment(value.asText());
                case "singleLineOverrulesHangingColon" -> options.singleLineOverrulesHangingColon = value.asBoolean();
                case "allowShortRulesOnASingleLine" -> options.allowShortRulesOnASingleLine = value.asBoolean();
                case "alignSemicolons" -> options.alignSemicolons = parseSemicolonAlignment(value.asText());
                case "breakBeforeParens" -> options.breakBeforeParens = value.asBoolean();
                case "ruleInternalsOnSingleLine" -> options.ruleInternalsOnSingleLine = value.asBoolean();
                case "minEmptyLines" -> options.minEmptyLines = value.asInt();
                case "groupedAlignments" -> options.groupedAlignments = value.asBoolean();
                case "alignFirstTokens" -> options.alignFirstTokens = value.asBoolean();
                case "alignLexerCommands" -> options.alignLexerCommands = value.asBoolean();
                case "alignActions" -> options.alignActions = value.asBoolean();
                case "alignLabels" -> options.alignLabels = value.asBoolean();
                case "alignTrailers" -> options.alignTrailers = value.asBoolean();
                default -> {
                    // Keep parity behavior with TS tests by ignoring unknown keys here.
                }
            }
        });

        return options;
    }

    static int positionToIndex(String text, int column, int row) {
        int currentRow = 1;
        int currentColumn = 0;

        for (int i = 0; i < text.length(); i++) {
            if (row < currentRow) {
                return i - 1;
            }

            if (currentRow == row && currentColumn == column) {
                return i;
            }

            char c = text.charAt(i);
            if (c == '\n') {
                currentColumn = 0;
                currentRow++;
            } else if (c == '\t') {
                currentColumn += 4 - (currentColumn % 4);
            } else {
                currentColumn++;
            }
        }

        return text.length();
    }

    static int[] indexToPosition(String text, int index) {
        int currentRow = 1;
        int currentColumn = 0;

        for (int i = 0; i < text.length(); i++) {
            if (i == index) {
                return new int[] { currentColumn, currentRow };
            }

            char c = text.charAt(i);
            if (c == '\n') {
                currentColumn = 0;
                currentRow++;
            } else if (c == '\t') {
                currentColumn += 4 - (currentColumn % 4);
            } else {
                currentColumn++;
            }
        }

        return new int[] { currentColumn, currentRow };
    }

    private static ColonAlignment parseColonAlignment(String value) {
        return switch (value) {
            case "trailing" -> ColonAlignment.TRAILING;
            case "hanging" -> ColonAlignment.HANGING;
            default -> ColonAlignment.NONE;
        };
    }

    private static SemicolonAlignment parseSemicolonAlignment(String value) {
        return switch (value) {
            case "none" -> SemicolonAlignment.NONE;
            case "hanging" -> SemicolonAlignment.HANGING;
            default -> SemicolonAlignment.OWN_LINE;
        };
    }
}


package ws.idle.antlr.formatter;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;

import com.fasterxml.jackson.databind.JsonNode;
import java.io.IOException;
import org.junit.jupiter.api.Test;

class FormattingParityTest {

    @Test
    void withAllOptionsExceptAlignment() throws IOException {
        FormattingResult result = formatGrammarFromResource("tests/formatting/raw.g4", optionsWithReflowComments(), 0,
            (int) 1e10);

        String expected = FormattingTestSupport.readResource("tests/formatting-results/raw.g4");
        assertEquals(expected, result.text());
    }

    @Test
    void alignmentFormatting() throws IOException {
        FormattingResult result = formatGrammarFromResource("tests/formatting/alignment.g4", optionsWithReflowComments(),
            0, (int) 1e10);

        String expected = FormattingTestSupport.readResource("tests/formatting-results/alignment.g4");
        assertEquals(expected, result.text());
    }

    @Test
    void breakBeforeBracesFormatting() throws IOException {
        FormattingResult result = formatGrammarFromResource("tests/formatting/break-before-braces.g4",
            new FormattingOptions(), 0, (int) 1e10);

        String expected = FormattingTestSupport.readResource("tests/formatting-results/break-before-braces.g4");
        assertEquals(expected, result.text());
    }

    @Test
    void rangedFormatting() throws IOException {
        FormattingResult first = formatGrammarFromResource("tests/formatting/raw.g4", optionsWithReflowComments(), -10,
            -20);
        assertEquals("", first.text());
        assertEquals(0, first.targetStart());
        assertEquals(4, first.targetStop());

        String rangesText = FormattingTestSupport.readResource("tests/formatting/ranges.json");
        JsonNode ranges = FormattingTestSupport.MAPPER.readTree(rangesText);
        String source = FormattingTestSupport.readResource("tests/formatting/raw.g4");

        for (JsonNode test : ranges) {
            int startIndex = FormattingTestSupport.positionToIndex(source,
                test.path("source").path("start").path("column").asInt(),
                test.path("source").path("start").path("row").asInt());
            int stopIndex = FormattingTestSupport.positionToIndex(source,
                test.path("source").path("end").path("column").asInt(),
                test.path("source").path("end").path("row").asInt()) - 1;

            FormattingResult result = formatGrammarFromResource("tests/formatting/raw.g4", optionsWithReflowComments(),
                startIndex, stopIndex);

            int[] start = FormattingTestSupport.indexToPosition(source, result.targetStart());
            int[] stop = FormattingTestSupport.indexToPosition(source, result.targetStop() + 1);

            int[] target = new int[] {
                test.path("target").path("start").path("column").asInt(),
                test.path("target").path("start").path("row").asInt(),
                test.path("target").path("end").path("column").asInt(),
                test.path("target").path("end").path("row").asInt(),
            };

            assertArrayEquals(new int[] { target[0], target[1] }, start);
            assertArrayEquals(new int[] { target[2], target[3] }, stop);

            String expected = FormattingTestSupport.readResource(
                "tests/formatting-results/" + test.path("result").asText());
            assertEquals(expected, result.text());
        }
    }

    @Test
    void bugAntlrGrammarsV4_3862() throws IOException {
        FormattingResult result = formatGrammarFromResource("tests/formatting/bug3862.g4", new FormattingOptions(), 0,
            (int) 1e10);

        String expected = FormattingTestSupport.readResource("tests/formatting-results/bug3862.g4");
        assertEquals(expected, result.text());
    }

    @Test
    void colonsInBlocks() throws IOException {
        FormattingResult result = formatGrammarFromResource("tests/formatting/Colons.g4", new FormattingOptions(), 0,
            (int) 1e10);

        String expected = FormattingTestSupport.readResource("tests/formatting-results/colons.g4");
        assertEquals(expected, result.text());
    }

    @Test
    void ruleOptions() throws IOException {
        FormattingResult result = formatGrammarFromResource("tests/formatting/RuleOptions.g4", new FormattingOptions(), 0,
            (int) 1e10);

        String expected = FormattingTestSupport.readResource("tests/formatting-results/RuleOptions.g4");
        assertEquals(expected, result.text());
    }

    @Test
    void bug2FixedPoint() throws IOException {
        JsonNode config = FormattingTestSupport.MAPPER
            .readTree(FormattingTestSupport.readResource("tests/formatting/bug#2-config.json"));
        FormattingOptions lexer = FormattingTestSupport.parseOptions(config.path("lexer"));

        FormattingResult firstResult = formatGrammarFromResource("tests/formatting/PlSqlLexer.g4.txt", lexer, 0,
            (int) 1e10);
        String first = firstResult.text();

        String second = formatGrammar(first, lexer, 0, (int) 1e10).text();
        assertEquals(first, second);

        String third = formatGrammar(second, lexer, 0, (int) 1e10).text();
        assertEquals(second, third);

        String fourth = formatGrammar(third, lexer, 0, (int) 1e10).text();
        assertEquals(third, fourth);

        String fifth = formatGrammar(fourth, lexer, 0, (int) 1e10).text();
        assertEquals(fourth, fifth);
    }

    private static FormattingResult formatGrammarFromResource(String resourcePath, FormattingOptions options, int start,
                                                              int stop) throws IOException {
        return formatGrammar(FormattingTestSupport.readResource(resourcePath), options, start, stop);
    }

    private static FormattingResult formatGrammar(String grammar, FormattingOptions options, int start, int stop) {
        GrammarFormatter formatter = new GrammarFormatter(grammar);
        return formatter.formatGrammar(options, start, stop);
    }

    private static FormattingOptions optionsWithReflowComments() {
        FormattingOptions options = new FormattingOptions();
        options.reflowComments = true;
        return options;
    }
}


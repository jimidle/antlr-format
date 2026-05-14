package ws.idle.antlr.formatter;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import java.util.function.IntUnaryOperator;
import org.junit.jupiter.api.Test;

class FormatterAlignmentsTest {

    private static final int ALIGNMENT_MARKER = -200_000;
    private static final int WHITESPACE_BLOCK_MARKER = -300_000;
    private static final int SPACE_MARKER = -3;
    private static final int WHITESPACE_ERASER_MARKER = -102;

    @Test
    void alignmentTypesRemainInFormattingOrder() {
        assertArrayEquals(new FormatterAlignmentType[] {
            FormatterAlignmentType.COLON,
            FormatterAlignmentType.FIRST_TOKEN,
            FormatterAlignmentType.LABEL,
            FormatterAlignmentType.ACTION,
            FormatterAlignmentType.LEXER_COMMAND,
            FormatterAlignmentType.TRAILING_COMMENT,
            FormatterAlignmentType.TRAILERS,
        }, FormatterAlignmentType.values());
    }

    @Test
    void resetClearsLastLineTrackingForSelectedAlignmentTypes() {
        FormatterAlignments alignments = new FormatterAlignments();
        List<Integer> outputPipeline = new ArrayList<>();

        alignments.addEntry(FormatterAlignmentType.COLON, 7, false, true, outputPipeline, () -> {
        }, ALIGNMENT_MARKER);
        alignments.reset(FormatterAlignmentType.COLON);
        alignments.addEntry(FormatterAlignmentType.COLON, 7, false, true, outputPipeline, () -> {
        }, ALIGNMENT_MARKER);

        assertEquals(List.of(ALIGNMENT_MARKER, ALIGNMENT_MARKER), outputPipeline);
    }

    @Test
    void nonConsecutiveEntriesOnlyAlignTogetherWhenGroupingIsDisabled() {
        IntUnaryOperator columnForEntry = index -> index == 0 ? 2 : 5;

        FormatterAlignments groupedAlignments = new FormatterAlignments();
        List<Integer> groupedPipeline = new ArrayList<>();
        groupedAlignments.addEntry(FormatterAlignmentType.LABEL, 1, false, true, groupedPipeline, () -> {
        }, ALIGNMENT_MARKER);
        groupedAlignments.addEntry(FormatterAlignmentType.LABEL, 3, false, true, groupedPipeline, () -> {
        }, ALIGNMENT_MARKER);
        List<String> groupedWhitespace = new ArrayList<>();
        groupedAlignments.compute(groupedPipeline, groupedWhitespace, spacingOptions(), columnForEntry,
            FormatterAlignmentsTest::markerMatches, WHITESPACE_BLOCK_MARKER, SPACE_MARKER, WHITESPACE_ERASER_MARKER,
            SPACE_MARKER, 9_001, 9_002);

        assertEquals(List.of(SPACE_MARKER, SPACE_MARKER), groupedPipeline);
        assertTrue(groupedWhitespace.isEmpty());

        FormatterAlignments ungroupedAlignments = new FormatterAlignments();
        List<Integer> ungroupedPipeline = new ArrayList<>();
        ungroupedAlignments.addEntry(FormatterAlignmentType.LABEL, 1, false, false, ungroupedPipeline, () -> {
        }, ALIGNMENT_MARKER);
        ungroupedAlignments.addEntry(FormatterAlignmentType.LABEL, 3, false, false, ungroupedPipeline, () -> {
        }, ALIGNMENT_MARKER);
        List<String> ungroupedWhitespace = new ArrayList<>();
        ungroupedAlignments.compute(ungroupedPipeline, ungroupedWhitespace, spacingOptions(), columnForEntry,
            FormatterAlignmentsTest::markerMatches, WHITESPACE_BLOCK_MARKER, SPACE_MARKER, WHITESPACE_ERASER_MARKER,
            SPACE_MARKER, 9_001, 9_002);

        assertEquals(List.of(WHITESPACE_BLOCK_MARKER, WHITESPACE_BLOCK_MARKER - 1), ungroupedPipeline);
        assertEquals(List.of("    ", " "), ungroupedWhitespace);
    }

    @Test
    void singletonAlignmentMarkerBecomesWhitespaceEraserWhenWhitespaceAlreadyExists() {
        FormatterAlignments alignments = new FormatterAlignments();
        List<Integer> outputPipeline = new ArrayList<>(List.of(SPACE_MARKER));

        alignments.addEntry(FormatterAlignmentType.COLON, 1, false, true, outputPipeline, () -> {
        }, ALIGNMENT_MARKER);

        List<String> whitespaceList = new ArrayList<>();
        alignments.compute(outputPipeline, whitespaceList, spacingOptions(), index -> 0,
            (index, marker) -> index >= 0 && index < outputPipeline.size() && outputPipeline.get(index) == marker,
            WHITESPACE_BLOCK_MARKER, SPACE_MARKER, WHITESPACE_ERASER_MARKER, SPACE_MARKER, 9_001, 9_002);

        assertEquals(List.of(SPACE_MARKER, WHITESPACE_ERASER_MARKER), outputPipeline);
        assertTrue(whitespaceList.isEmpty());
    }

    private static boolean markerMatches(int index, int marker) {
        return false;
    }

    private static FormattingOptions spacingOptions() {
        FormattingOptions options = FormattingOptions.defaults();
        options.useTab = false;
        options.tabWidth = 4;
        return options;
    }
}


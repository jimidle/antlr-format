package ws.idle.antlr.formatter;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.function.IntUnaryOperator;

final class FormatterAlignments {

    private static final class AlignmentStatus {
        private int lastLine = -1;
        private final List<List<Integer>> groups = new ArrayList<>();
    }

    /** Matcher used to inspect the formatter output pipeline around a stored alignment marker. */
    @FunctionalInterface
    interface EntryMatcher {

        /**
         * Determines whether a pipeline entry at the given index matches the requested marker semantics.
         *
         * @param index the pipeline index to inspect
         * @param marker the marker or token type to match
         * @return {@code true} when the entry matches
         */
        boolean matches(int index, int marker);
    }

    private final Map<FormatterAlignmentType, AlignmentStatus> alignments = new EnumMap<>(FormatterAlignmentType.class);

    /** Clears all tracked alignment groups. */
    void clear() {
        alignments.clear();
    }

    /**
     * Resets the line-tracking state for the supplied alignment types.
     *
     * @param types the alignment types to reset
     */
    void reset(FormatterAlignmentType... types) {
        for (FormatterAlignmentType type : types) {
            AlignmentStatus status = alignments.get(type);
            if (status != null) {
                status.lastLine = -1;
            }
        }
    }

    /**
     * Adds an alignment marker for the current output position.
     *
     * @param type the alignment family being tracked
     * @param currentLine the current output line number
     * @param lineHasLeadingContent whether the line already contains non-whitespace content
     * @param groupedAlignments whether non-consecutive lines should stay in the same group
     * @param outputPipeline the formatter output pipeline receiving the marker
     * @param removeTrailingTabsAndSpaces callback used to strip spacing before placing the marker
     * @param alignmentMarker the marker value to insert into the output pipeline
     */
    void addEntry(FormatterAlignmentType type, int currentLine, boolean lineHasLeadingContent, boolean groupedAlignments,
                  List<Integer> outputPipeline, Runnable removeTrailingTabsAndSpaces, int alignmentMarker) {
        AlignmentStatus status = alignments.get(type);
        if (status == null) {
            status = new AlignmentStatus();
            alignments.put(type, status);
        }
        if (status.lastLine != currentLine) {
            if (lineHasLeadingContent) {
                removeTrailingTabsAndSpaces.run();
            }
            boolean startNewGroup = true;
            if (status.lastLine > -1 && (!groupedAlignments || status.lastLine + 1 == currentLine)) {
                startNewGroup = false;
                status.groups.getLast().add(outputPipeline.size());
            }
            if (startNewGroup) {
                List<Integer> group = new ArrayList<>();
                group.add(outputPipeline.size());
                status.groups.add(group);
            }
            outputPipeline.add(alignmentMarker);
            status.lastLine = currentLine;
        }
    }

    /**
     * Resolves stored alignment markers into either fixed whitespace blocks or simple spaces.
     *
     * @param outputPipeline the formatter output pipeline
     * @param whitespaceList the whitespace block table backing negative whitespace markers
     * @param options the active formatting options
     * @param columnForEntry callback that computes the rendered column for a pipeline index
     * @param entryMatcher callback used to inspect neighboring pipeline entries
     * @param whitespaceBlockMarker the base marker for whitespace blocks
     * @param whitespaceMarker the synthetic whitespace marker used during matching
     * @param whitespaceEraserMarker the marker used when inserted alignment whitespace should disappear
     * @param spaceMarker the marker used for a single space
     * @param leftParenTokenType the token type used to detect parenthesized contexts
     * @param colonTokenType the token type used to detect colon-adjacent contexts
     */
    void compute(List<Integer> outputPipeline, List<String> whitespaceList, FormattingOptions options,
                 IntUnaryOperator columnForEntry, EntryMatcher entryMatcher,
                 int whitespaceBlockMarker, int whitespaceMarker, int whitespaceEraserMarker, int spaceMarker,
                 int leftParenTokenType, int colonTokenType) {
        for (FormatterAlignmentType type : FormatterAlignmentType.values()) {
            AlignmentStatus alignment = alignments.get(type);
            if (alignment == null) {
                continue;
            }
            for (List<Integer> group : alignment.groups) {
                if (group.size() == 1) {
                    int index = group.getFirst();
                    if (index < outputPipeline.size()) {
                        if (entryMatcher.matches(index - 1, whitespaceMarker)
                            || entryMatcher.matches(index - 1, leftParenTokenType)
                            || entryMatcher.matches(index + 2, colonTokenType)) {
                            outputPipeline.set(index, whitespaceEraserMarker);
                        } else {
                            outputPipeline.set(index, spaceMarker);
                        }
                    }
                    continue;
                }

                List<Integer> columns = new ArrayList<>();
                for (int member : group) {
                    if (member < outputPipeline.size()) {
                        columns.add(columnForEntry.applyAsInt(member));
                    }
                }
                int maxColumn = columns.stream().mapToInt(Integer::intValue).max().orElse(0);
                if (Boolean.TRUE.equals(options.useTab)) {
                    maxColumn += options.tabWidth - (maxColumn % options.tabWidth);
                } else {
                    ++maxColumn;
                }

                for (int i = 0; i < group.size(); ++i) {
                    int whitespaceIndex = whitespaceBlockMarker - whitespaceList.size();
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
}


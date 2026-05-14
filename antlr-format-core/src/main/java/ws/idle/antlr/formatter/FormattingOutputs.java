package ws.idle.antlr.formatter;

/**
 * Utilities for normalizing formatter output before it is emitted to files or terminal streams.
 */
public final class FormattingOutputs {

    /** Prevents instantiation of the utility class. */
    private FormattingOutputs() {
    }

    /**
     * Normalizes formatted text for emission on the current operating system.
     * Every line break is rewritten to the host line separator and the result is guaranteed to end
     * with a trailing line separator.
     *
     * @param text the formatted text to normalize
     * @return the normalized text ready for output
     */
    public static String normalizeForOutput(String text) {
        return normalizeForOutput(text, System.lineSeparator());
    }

    /**
     * Normalizes formatted text for emission using a caller-specified line separator.
     * Every line break is rewritten to the supplied separator and the result is guaranteed to end
     * with a trailing line separator.
     *
     * @param text the formatted text to normalize
     * @param lineSeparator the line separator to use in the normalized output
     * @return the normalized text ready for output
     */
    public static String normalizeForOutput(String text, String lineSeparator) {
        String normalized = text.replace("\r\n", "\n").replace("\r", "\n").replace("\n", lineSeparator);
        if (normalized.endsWith(lineSeparator)) {
            return normalized;
        }
        return normalized + lineSeparator;
    }
}


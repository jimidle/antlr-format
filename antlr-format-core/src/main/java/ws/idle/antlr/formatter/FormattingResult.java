package ws.idle.antlr.formatter;

/** Result for format operations, including adjusted replacement range. */
public record FormattingResult(String text, int targetStart, int targetStop) {
}


package ws.idle.antlr.formatter;

/** Alignment mode for rule colons. */
public enum ColonAlignment {
    NONE("none"),
    TRAILING("trailing"),
    HANGING("hanging");

    private final String externalName;

    ColonAlignment(String externalName) {
        this.externalName = externalName;
    }

    /**
     * Returns the serialized option value used in formatter directives and configuration comments.
     *
     * @return the external name for this alignment mode
     */
    public String externalName() {
        return externalName;
    }
}


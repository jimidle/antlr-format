package ws.idle.antlr.formatter;

/** Alignment mode for rule semicolons. */
public enum SemicolonAlignment {
    NONE("none"),
    OWN_LINE("ownLine"),
    HANGING("hanging");

    private final String externalName;

    SemicolonAlignment(String externalName) {
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


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

    public String externalName() {
        return externalName;
    }
}


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

    public String externalName() {
        return externalName;
    }
}


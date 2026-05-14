package ws.idle.antlr.formatter.cli;

import java.lang.reflect.Field;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;
import picocli.CommandLine.Option;
import ws.idle.antlr.formatter.ColonAlignment;
import ws.idle.antlr.formatter.SemicolonAlignment;

/**
 * Generates shell completion scripts from the CLI option model.
 */
final class CliCompletionScripts {

    private static final String COMMAND_NAME = "antlr-format";

    /** Prevents instantiation of the utility class. */
    private CliCompletionScripts() {
    }

    /**
     * Returns the command name used by the generated wrappers and completion scripts.
     *
     * @return the CLI command name
     */
    static String commandName() {
        return COMMAND_NAME;
    }

    /**
     * Builds the Bash completion script.
     *
     * @return the generated Bash completion script content
     */
    static String bashCompletionScript() {
        List<CliOptionDefinition> options = optionDefinitions();
        String optionWords = options.stream()
            .flatMap(option -> option.names().stream())
            .collect(Collectors.joining(" "));

        StringBuilder script = new StringBuilder();
        script.append("_antlr_format() {\n");
        script.append("    local cur prev opts\n");
        script.append("    COMPREPLY=()\n");
        script.append("    cur=\"${COMP_WORDS[COMP_CWORD]}\"\n");
        script.append("    prev=\"${COMP_WORDS[COMP_CWORD-1]}\"\n");
        script.append("    opts=\"").append(optionWords).append("\"\n\n");
        script.append("    case \"${prev}\" in\n");
        for (CliOptionDefinition option : options) {
            if (!option.acceptsValue()) {
                continue;
            }
            script.append("        ").append(String.join("|", option.names())).append(")\n");
            if (!option.completionValues().isEmpty()) {
                script.append("            COMPREPLY=( $(compgen -W \"")
                    .append(String.join(" ", option.completionValues()))
                    .append("\" -- \"${cur}\") )\n");
            } else if (option.fileValue()) {
                script.append("            COMPREPLY=( $(compgen -f -- \"${cur}\") )\n");
            } else {
                script.append("            return 0\n");
                script.append("            ;;\n");
                continue;
            }
            script.append("            return 0\n");
            script.append("            ;;\n");
        }
        script.append("    esac\n\n");
        script.append("    if [[ \"${cur}\" == -* ]]; then\n");
        script.append("        COMPREPLY=( $(compgen -W \"${opts}\" -- \"${cur}\") )\n");
        script.append("    else\n");
        script.append("        COMPREPLY=( $(compgen -f -X '!*.g4' -- \"${cur}\") )\n");
        script.append("    fi\n");
        script.append("}\n\n");
        script.append("complete -F _antlr_format ").append(COMMAND_NAME).append("\n");
        return script.toString();
    }

    /**
     * Builds the Zsh completion script.
     *
     * @return the generated Zsh completion script content
     */
    static String zshCompletionScript() {
        StringBuilder script = new StringBuilder();
        script.append("#compdef ").append(COMMAND_NAME).append("\n\n");
        script.append("_arguments -s -S \\\n");
        List<CliOptionDefinition> options = optionDefinitions();
        for (int index = 0; index < options.size(); index++) {
            CliOptionDefinition option = options.get(index);
            script.append("  ").append(quoteZshChoice(option));
            script.append(index == options.size() - 1 ? " \\\n" : " \\\n");
        }
        script.append("  '*:grammar:_files -g *.g4'\n");
        return script.toString();
    }

    /**
     * Builds the Fish completion script.
     *
     * @return the generated Fish completion script content
     */
    static String fishCompletionScript() {
        StringBuilder script = new StringBuilder();
        for (CliOptionDefinition option : optionDefinitions()) {
            script.append("complete -c ").append(COMMAND_NAME).append(" -f");
            for (String name : option.names()) {
                if (name.startsWith("--")) {
                    script.append(" -l ").append(name.substring(2));
                } else if (name.startsWith("-")) {
                    script.append(" -s ").append(name.substring(1));
                }
            }
            if (option.acceptsValue()) {
                script.append(" -r");
            }
            if (!option.completionValues().isEmpty()) {
                script.append(" -a '").append(String.join(" ", option.completionValues())).append("'");
            }
            script.append(" -d '").append(escapeSingleQuotes(option.description())).append("'\n");
        }
        script.append("complete -c ").append(COMMAND_NAME)
            .append(" -f -a '(__fish_complete_suffix .g4)' -d 'ANTLR grammar file'\n");
        return script.toString();
    }

    /**
     * Returns the normalized completion definitions used for all supported shells.
     *
     * @return the option definitions for the CLI command
     */
    static List<CliOptionDefinition> optionDefinitions() {
        List<CliOptionDefinition> result = new ArrayList<>();
        result.add(new CliOptionDefinition(List.of("-h", "--help"), false, false, List.of(), "Show CLI help."));
        result.add(new CliOptionDefinition(List.of("-V", "--version"), false, false, List.of(),
            "Show CLI version information."));
        for (Class<?> holder : List.of(AntlrFormatCli.class, CliFormattingOptions.class)) {
            for (Field field : holder.getDeclaredFields()) {
                Option option = field.getAnnotation(Option.class);
                if (option != null) {
                    result.add(toDefinition(field, option));
                }
            }
        }
        return result.stream()
            .sorted(Comparator.comparing(CliCompletionScripts::sortKey))
            .toList();
    }

    private static CliOptionDefinition toDefinition(Field field, Option option) {
        List<String> names = new ArrayList<>(Arrays.asList(option.names()));
        if (option.negatable()) {
            for (String name : option.names()) {
                if (name.startsWith("--")) {
                    names.add("--no-" + name.substring(2));
                }
            }
        }

        boolean acceptsValue = !isBooleanType(field.getType());
        boolean fileValue = field.getType() == Path.class;
        String description = option.description().length == 0 ? "" : option.description()[0];
        return new CliOptionDefinition(List.copyOf(names), acceptsValue, fileValue, completionValues(field.getType()),
            description);
    }

    private static boolean isBooleanType(Class<?> type) {
        return type == Boolean.class || type == boolean.class;
    }

    private static List<String> completionValues(Class<?> type) {
        if (type == ColonAlignment.class) {
            return Arrays.stream(ColonAlignment.values()).map(ColonAlignment::externalName).toList();
        }
        if (type == SemicolonAlignment.class) {
            return Arrays.stream(SemicolonAlignment.values()).map(SemicolonAlignment::externalName).toList();
        }
        return List.of();
    }

    private static String sortKey(CliOptionDefinition option) {
        return option.names().stream().filter(name -> name.startsWith("--")).findFirst().orElse(option.names().get(0));
    }

    private static String quoteZshChoice(CliOptionDefinition option) {
        StringBuilder result = new StringBuilder();
        String names = option.names().size() == 1 ? option.names().get(0) : "{" + String.join(",", option.names()) + "}";
        result.append('\'').append(names).append('[').append(escapeZsh(option.description())).append(']');
        if (option.acceptsValue()) {
            if (!option.completionValues().isEmpty()) {
                result.append(':').append("value:(").append(String.join(" ", option.completionValues())).append(')');
            } else if (option.fileValue()) {
                result.append(":file:_files");
            } else {
                result.append(":value:");
            }
        }
        result.append('\'');
        return result.toString();
    }

    private static String escapeZsh(String text) {
        return text.replace("'", "''");
    }

    private static String escapeSingleQuotes(String text) {
        return text.replace("'", "\\'");
    }

    /**
     * Normalized option description used when generating completion scripts.
     *
     * @param names the accepted option spellings
     * @param acceptsValue whether the option consumes a following argument
     * @param fileValue whether the value should complete against filesystem paths
     * @param completionValues fixed value completions for enum-like options
     * @param description the human-readable help text
     */
    record CliOptionDefinition(List<String> names, boolean acceptsValue, boolean fileValue, List<String> completionValues,
                               String description) {
    }
}


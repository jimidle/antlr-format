# Command line interface

The `antlr-format-cli` module packages the formatter both as a runnable jar and as installable distribution archives.
It is the supported command line surface for the project.

## Build the CLI jar

From the repository root:

```bash
mvn -B --no-transfer-progress -pl antlr-format-cli -am package
```

The runnable jar is produced at:

```text
antlr-format-cli/target/antlr-format-cli-1.0.3.jar
```

The installable CLI archives are produced alongside it:

```text
antlr-format-cli/target/antlr-format-cli-1.0.3.zip
antlr-format-cli/target/antlr-format-cli-1.0.3.tar.gz
```

Each archive contains:

- `bin/antlr-format` – Unix-like wrapper script
- `bin/antlr-format.cmd` – `cmd.exe` wrapper script
- `bin/antlr-format.ps1` – PowerShell wrapper script
- `lib/antlr-format-cli-1.0.3.jar` – runnable shaded jar
- `completions/antlr-format.bash` – Bash completion
- `completions/_antlr-format` – Zsh completion
- `completions/antlr-format.fish` – Fish completion
- `homebrew/antlr-format.rb` – formula template matching the published Homebrew package

## Using the installable wrapper

After unpacking either distribution archive, run the formatter through the wrapper instead of calling `java -jar` directly:

```bash
./bin/antlr-format path/to/Grammar.g4
```

The Unix-like wrapper honors `JAVA_HOME` when present and also accepts extra JVM arguments through
`ANTLR_FORMAT_JAVA_OPTS`.

## Shell completion files

The CLI build generates completion files for Bash, Zsh, and Fish from the current option set.
This keeps the packaged completions aligned with the CLI flags that the formatter actually supports.

Typical manual install locations after unpacking are:

- Bash: copy `completions/antlr-format.bash` into your Bash completion directory and source it
- Zsh: copy `completions/_antlr-format` into a directory on `fpath`
- Fish: copy `completions/antlr-format.fish` into `~/.config/fish/completions/`

## Basic usage

Format a grammar to standard output:

```bash
java -jar antlr-format-cli/target/antlr-format-cli-1.0.3.jar path/to/Grammar.g4
```

Overwrite the input file in place:

```bash
java -jar antlr-format-cli/target/antlr-format-cli-1.0.3.jar --write path/to/Grammar.g4
```

Write to a separate output file:

```bash
java -jar antlr-format-cli/target/antlr-format-cli-1.0.3.jar \
  --output path/to/Formatted.g4 \
  path/to/Grammar.g4
```

Inject the effective formatter options as a comment when the grammar does not already contain formatter directives:

```bash
java -jar antlr-format-cli/target/antlr-format-cli-1.0.3.jar \
  --add-options \
  path/to/Grammar.g4
```

## Output behavior

The CLI normalizes emitted text before writing it to standard output or a file:

- line endings use the current operating system's line separator
- output always ends with a trailing line separator

## Top-level CLI options

| Option | Description |
| --- | --- |
| `<grammar-file>` | Input grammar file to format. |
| `-w`, `--write` | Overwrite the input file in place. |
| `-o`, `--output <file>` | Write the formatted grammar to a separate file. |
| `--encoding <charset>` | Read and write using the specified charset. Defaults to `UTF-8`. |
| `--[no-]add-options` | Inject the effective formatter options as a directive comment at the top of the output when the grammar does not already contain any formatter directives. |
| `-h`, `--help` | Show CLI help. |
| `-V`, `--version` | Show CLI version information. |

`--write` and `--output` are mutually exclusive.
If neither is specified, the CLI writes the formatted grammar to standard output.

## Formatter option flags

Every inline formatter option described in [`formatter-directives.md`](formatter-directives.md) is available as a CLI flag.
Boolean options use picocli's negatable form, so they can be turned on with `--option-name` and off with `--no-option-name`.

| Formatter option | CLI flag |
| --- | --- |
| `alignTrailingComments` | `--[no-]align-trailing-comments` |
| `allowShortBlocksOnASingleLine` | `--[no-]allow-short-blocks-on-a-single-line` |
| `breakBeforeBraces` | `--[no-]break-before-braces` |
| `columnLimit` | `--column-limit <int>` |
| `continuationIndentWidth` | `--continuation-indent-width <int>` |
| `indentWidth` | `--indent-width <int>` |
| `keepEmptyLinesAtTheStartOfBlocks` | `--[no-]keep-empty-lines-at-the-start-of-blocks` |
| `maxEmptyLinesToKeep` | `--max-empty-lines-to-keep <int>` |
| `reflowComments` | `--[no-]reflow-comments` |
| `spaceBeforeAssignmentOperators` | `--[no-]space-before-assignment-operators` |
| `tabWidth` | `--tab-width <int>` |
| `useTab` | `--[no-]use-tab` |
| `alignColons` | `--align-colons <none|trailing|hanging>` |
| `singleLineOverrulesHangingColon` | `--[no-]single-line-overrules-hanging-colon` |
| `allowShortRulesOnASingleLine` | `--[no-]allow-short-rules-on-a-single-line` |
| `alignSemicolons` | `--align-semicolons <none|ownLine|hanging>` |
| `breakBeforeParens` | `--[no-]break-before-parens` |
| `ruleInternalsOnSingleLine` | `--[no-]rule-internals-on-single-line` |
| `minEmptyLines` | `--min-empty-lines <int>` |
| `groupedAlignments` | `--[no-]grouped-alignments` |
| `alignFirstTokens` | `--[no-]align-first-tokens` |
| `alignLexerCommands` | `--[no-]align-lexer-commands` |
| `alignActions` | `--[no-]align-actions` |
| `alignLabels` | `--[no-]align-labels` |
| `alignTrailers` | `--[no-]align-trailers` |

The API-only `disabled` field is intentionally not exposed as a CLI flag because inline directives use `on` / `off` control directives instead.
The comment-reflow option is exposed as `--reflow-comments`, matching `FormattingOptions.reflowComments` and the
inline `reflowComments` directive spelling. For the detailed preservation and idempotence rules, see
[`formatter-directives.md`](formatter-directives.md#reflowcomments).

## Configuration precedence

The effective configuration precedence is:

1. built-in formatter defaults
2. CLI flags
3. inline grammar directives such as `// $antlr-format ...`

This means grammar comments always win over CLI flags.

### Important `reset` behavior

`// $antlr-format reset` resets the active option state to the formatter's built-in defaults.
It does **not** restore previously supplied CLI flags.
After a `reset` directive, formatting continues from the built-in defaults until later directives change the state again.

## Interaction with `--add-options`

`--add-options` asks the formatter to emit the effective options as a comment at the top of the output.
However, if the grammar already contains any formatter directive comment, the formatter does not inject another one.
This avoids duplicating or conflicting with inline configuration already present in the grammar.

## Related documentation

- [Project README](../README.md)
- [Homebrew packaging notes](homebrew.md)
- [Maven plugin guide](maven-plugin.md)
- [Formatter directives reference](formatter-directives.md)


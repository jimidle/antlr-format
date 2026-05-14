# Maven plugin guide

The `antlr-format-maven-plugin` module formats ANTLR grammars in place from Maven builds or direct Maven invocations.

## Basic configuration

```xml
<plugin>
  <groupId>ws.idle</groupId>
  <artifactId>antlr-format-maven-plugin</artifactId>
  <version>1.0.1</version>
  <executions>
    <execution>
      <goals>
        <goal>format</goal>
      </goals>
    </execution>
  </executions>
  <configuration>
    <sourceDirectory>${project.basedir}/src/main/antlr4</sourceDirectory>
    <addOptions>true</addOptions>
    <main>
      <reflowComments>true</reflowComments>
      <alignLabels>true</alignLabels>
      <columnLimit>120</columnLimit>
    </main>
    <lexer>
      <alignTrailers>true</alignTrailers>
      <alignLexerCommands>true</alignLexerCommands>
    </lexer>
  </configuration>
</plugin>
```

## Top-level plugin parameters

| Parameter | Type | Default | Description |
| --- | --- | --- | --- |
| `sourceDirectory` | path | `${project.basedir}/src/main/antlr4` | Root directory scanned for grammar files. |
| `includes` | list of glob strings | `**/*.g4` | Include patterns relative to `sourceDirectory`. |
| `excludes` | list of glob strings | none | Exclude patterns relative to `sourceDirectory`. |
| `skip` | boolean | `false` | Skip formatting entirely. |
| `addOptions` | boolean | `true` | Inject the effective formatter options as a directive comment when the grammar does not already contain formatter directives. |
| `dryRun` | boolean | `false` | Report files that would change without rewriting them. |
| `encoding` | charset name | `UTF-8` | Charset used to read and write grammar files. |
| `main` | `FormattingOptions` | sparse options object | Main grammar option overrides. |
| `lexer` | `FormattingOptions` | none | Optional lexer-grammar-specific option overrides. |

## Nested `main` and `lexer` option blocks

The `main` and `lexer` configuration blocks both use the `FormattingOptions` model.
That means they accept every formatter option that can be configured inline with `// $antlr-format ...`, including for example:

- `alignTrailingComments`
- `allowShortBlocksOnASingleLine`
- `breakBeforeBraces`
- `columnLimit`
- `continuationIndentWidth`
- `indentWidth`
- `keepEmptyLinesAtTheStartOfBlocks`
- `maxEmptyLinesToKeep`
- `reflowComments`
- `spaceBeforeAssignmentOperators`
- `tabWidth`
- `useTab`
- `alignColons`
- `singleLineOverrulesHangingColon`
- `allowShortRulesOnASingleLine`
- `alignSemicolons`
- `breakBeforeParens`
- `ruleInternalsOnSingleLine`
- `minEmptyLines`
- `groupedAlignments`
- `alignFirstTokens`
- `alignLexerCommands`
- `alignActions`
- `alignLabels`
- `alignTrailers`

For the meaning, defaults, and interactions of those entries, see [`formatter-directives.md`](formatter-directives.md).

The control directives `on`, `off`, and `reset` are not XML configuration fields because they are grammar-comment commands rather than option properties.
The API-only `disabled` field is also intentionally outside the inline directive vocabulary.

## Grammar-kind selection

The plugin chooses the option block as follows:

- parser, combined, and other non-lexer grammars use `main`
- lexer grammars use `lexer` when it is present
- lexer grammars fall back to `main` when `lexer` is omitted

## Configuration precedence

The effective precedence is:

1. built-in formatter defaults
2. plugin configuration from `main` or `lexer`
3. inline grammar directives such as `// $antlr-format ...`

This means grammar comments always override the plugin configuration supplied from Maven.

### Important `reset` behavior

`// $antlr-format reset` resets the active formatter state to the built-in defaults.
It does **not** restore the surrounding Maven plugin configuration.
After a `reset` directive, formatting continues from the built-in defaults until later directives change the state again.

## `addOptions` behavior

When `addOptions` is `true`, the formatter injects the effective option set as a directive comment at the top of the output.
If the grammar already contains any formatter directive comment, no additional injected comment is added.
This avoids duplicating or contradicting inline configuration already present in the grammar.

## Dry-run example

```bash
mvn -B --no-transfer-progress antlr-format:format -Dantlr-format.dryRun=true
```

## Related documentation

- [Project README](../README.md)
- [Command line interface](cli.md)
- [Formatter directives reference](formatter-directives.md)


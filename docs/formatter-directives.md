# Formatter directives reference

This document describes every directive currently recognized inside `// $antlr-format ...` and block-comment variants.
It covers:

- supported syntax
- defaults
- accepted values
- interactions between options
- known caveats in the current Java implementation

## Supported comment forms

Formatter directives can appear in line comments or block comments.

Examples:

```antlr
// $antlr-format alignLabels on, columnLimit 120
// $antlr-format off
// $antlr-format on
// $antlr-format reset
```

```antlr
/* $antlr-format alignColons trailing, alignSemicolons ownLine */
```

```antlr
/*
 * $antlr-format alignLabels on,
 * columnLimit 120,
 * alignColons trailing
 */
```

## Directive syntax

The parser accepts these patterns:

- `// $antlr-format`
- `// $antlr-format option value`
- `// $antlr-format option: value`
- `// $antlr-format: option value, otherOption otherValue`
- comma-separated entries across one or more lines

### Important syntax notes

- Use the exact option names shown in this document.
- Boolean options accept either `on` / `off` or `true` / `false`.
- In practice, directive keys and values should be treated as **case-sensitive**.
  - Prefer `on`, `off`, `true`, `false`, `none`, `trailing`, `hanging`, and `ownLine` exactly as written.
- A bare `// $antlr-format` comment is valid but does not change any settings.
- Unknown or malformed directives are treated as formatter-command errors and will surface as:

```text
<<Unexpected input or wrong formatter command>>
```

## Special directives

These directives affect formatter control flow rather than setting a single option.

### `on`

```antlr
// $antlr-format on
```

Re-enables formatting after an `off` directive.

### `off`

```antlr
// $antlr-format off
```

Disables formatting until a later `on` directive is encountered. The disabled region is preserved as raw source text.

### `reset`

```antlr
// $antlr-format reset
```

Resets the active formatter options to the built-in defaults.

## Option reference

The following table is intended to be exhaustive for inline `// $antlr-format ...` directives recognized by the formatter.
The only `FormattingOptions` field intentionally excluded from the table is `disabled`, which is API-only and documented separately in the caveats section below.

| Option | Type / accepted values | Default | Notes |
| --- | --- | --- | --- |
| `alignTrailingComments` | `on` / `off` | `false` | Aligns trailing **line comments** when they appear after content on a line. Ignored when `alignTrailers` is enabled. |
| `allowShortBlocksOnASingleLine` | `on` / `off` | `true` | Allows short parenthesized blocks and simple alternatives to remain on one line when they fit within the formatter's heuristics. |
| `breakBeforeBraces` | `on` / `off` | `false` | Moves opening braces for braced keyword blocks such as `options {` and top-level named-action blocks such as `@parser::members {` onto the next line. |
| `columnLimit` | integer | `100` | Soft line-width target used by comment reflow and single-line heuristics. Use positive values. |
| `continuationIndentWidth` | integer | `4` | Extra indentation applied when a token must wrap because the current line would exceed `columnLimit`. |
| `indentWidth` | integer | `4` | Number of spaces per indentation level when `useTab` is `off`. |
| `keepEmptyLinesAtTheStartOfBlocks` | `on` / `off` | `false` | Preserves empty lines at the beginning of blocks instead of collapsing them. |
| `maxEmptyLinesToKeep` | integer | `1` | Maximum number of consecutive empty lines preserved by the formatter. |
| `reflowComments` | `on` / `off` | `false` | Rewraps ordinary multi-line line comments and block/doc comments to fit within `columnLimit`, while preserving structural list-like lines. |
| `spaceBeforeAssignmentOperators` | `on` / `off` | `true` | Controls whether spaces are inserted before `=` and `+=`. |
| `tabWidth` | integer | `4` | Visual width used when measuring tabs for alignment and line-length calculations. |
| `useTab` | `on` / `off` | `false` | Uses tab characters for indentation and alignment blocks instead of spaces. |
| `alignColons` | `none` / `trailing` / `hanging` | `none` | Controls rule-colon placement and alignment behavior. |
| `singleLineOverrulesHangingColon` | `on` / `off` | `true` | Allows short rules to stay on one line even when `alignColons` is `hanging` or semicolons would otherwise hang. |
| `allowShortRulesOnASingleLine` | `on` / `off` | `true` | Allows short rules to remain on one line when they fit the formatter heuristics. |
| `alignSemicolons` | `none` / `ownLine` / `hanging` | `ownLine` | Controls semicolon placement at the end of rules. |
| `breakBeforeParens` | `on` / `off` | `false` | Breaks before a parenthesized block instead of keeping the opening parenthesis on the current line. |
| `ruleInternalsOnSingleLine` | `on` / `off` | `false` | Keeps certain rule-internal clauses such as `returns`, `locals`, and in-rule actions inline when possible. |
| `minEmptyLines` | integer | `0` | Minimum number of empty lines to insert after top-level constructs, capped by `maxEmptyLinesToKeep`. |
| `groupedAlignments` | `on` / `off` | `true` | When `on`, alignment groups reset across gaps in line numbering; when `off`, a wider region can align together. |
| `alignFirstTokens` | `on` / `off` | `false` | Aligns the first token after the colon in short single-line rules. |
| `alignLexerCommands` | `on` / `off` | `false` | Aligns lexer commands introduced by `->`. Ignored when `alignTrailers` is enabled. |
| `alignActions` | `on` / `off` | `false` | Aligns actions used as trailers. Ignored when `alignTrailers` is enabled. |
| `alignLabels` | `on` / `off` | `true` | Aligns labels introduced by `#` on multi-line rules. Ignored when `alignTrailers` is enabled. |
| `alignTrailers` | `on` / `off` | `false` | Master trailer-alignment switch. When enabled it takes precedence over label, action, lexer-command, and trailing-comment alignment. |

## Configuration precedence

The formatter resolves configuration in layers:

1. built-in formatter defaults
2. external options supplied by API callers, the Maven plugin, or the standalone CLI
3. inline grammar directives encountered in source order

This means inline grammar comments always override the surrounding plugin, CLI, or library configuration.

### `reset` returns to built-in defaults

`reset` does **not** restore the external configuration supplied by the plugin, CLI, or Java API.
It resets the active formatter state to the built-in defaults and formatting continues from there until later directives
change the state again.

### Same option vocabulary across grammars, CLI, and plugin config

The formatter uses the same option names across:

- inline grammar directives
- Maven plugin `<main>` / `<lexer>` configuration blocks
- standalone CLI flags documented in [`cli.md`](cli.md)

The only deliberate exception is the API-only `disabled` field discussed in the caveats section below.

## Alignment option interactions

Several alignment options overlap. The formatter applies them with explicit precedence.

### `alignTrailers` takes priority

When `alignTrailers` is `on`, it takes precedence over these more specific options:

- `alignTrailingComments`
- `alignActions`
- `alignLexerCommands`
- `alignLabels`

In other words, `alignTrailers` acts as the umbrella trailer-alignment mode.

### `alignFirstTokens` only matters for short single-line rules

`alignFirstTokens` is only applied when the formatter has already decided that a rule may stay on one line.
If a rule does not qualify as a short single-line rule, this option has no effect.

### `groupedAlignments` changes the scope of alignment groups

- `groupedAlignments on` groups adjacent lines together
- `groupedAlignments off` allows non-consecutive lines to participate in the same alignment group

This mainly affects:

- label alignment
- first-token alignment
- action alignment
- lexer-command alignment
- trailing-comment alignment
- trailer alignment

## Colon and semicolon behavior

### `alignColons`

#### `none`

```antlr
rule
    : alt1
    | alt2
    ;
```

No special colon alignment is applied.

#### `trailing`

The formatter attempts to keep colons trailing after the rule name and may align them in compatible short-rule layouts.

#### `hanging`

The formatter moves the colon onto its own indented line unless `singleLineOverrulesHangingColon` allows a short rule to remain on one line.

### `alignSemicolons`

#### `none`

The formatter does not apply special semicolon positioning.

#### `ownLine`

The semicolon is placed on its own line at the current indentation level.

#### `hanging`

The semicolon is moved to a hanging indentation position.

### `singleLineOverrulesHangingColon`

This option is most relevant when:

- `alignColons = hanging`
- or semicolons would otherwise be moved more aggressively

With the default `on`, sufficiently short rules are still allowed to remain on one line.
With `off`, hanging behavior is enforced more strictly.

## Comment-related options

### `reflowComments`

The option is named `reflowComments` everywhere the formatter exposes it:

- inline grammar directives: `// $antlr-format reflowComments on`
- Java API: `FormattingOptions.reflowComments`
- Maven plugin XML: `<reflowComments>true</reflowComments>`
- CLI flag: `--reflow-comments`

When enabled, the formatter can rewrap:

- grouped line comments
- block comments
- doc comments

In practice, reflow is paragraph-oriented. The formatter preserves comment lines that look structural rather than prose, including:

- single-word heading-like lines
- bullet lines starting with `-`, `*`, `+`, or common Unicode bullet glyphs
- checklist-style lines such as `[ ] item` or `[x] item`
- numbered list items such as `1. item` or `2) item`

Caveats:

- reflow only applies when the formatter can treat the comment as ordinary comment text
- formatter-directive comments themselves are not reflowed into different semantics
- comment wrapping is influenced by `columnLimit`, `tabWidth`, `useTab`, and current indentation
- repeated formatting is intended to be idempotent; a trailing physical newline does not cause the formatter to invent an extra trailing `//` line

### `alignTrailingComments`

This applies only to **line comments that already trail code on a line**. Standalone comments at the beginning of a line are not treated as trailing comments.

## Single-line layout heuristics

These options do not guarantee a one-line result on their own; they allow the formatter to keep compact constructs on one line when the content is short enough.

### `allowShortRulesOnASingleLine`

Allows short rules to remain on one line when the formatter estimates that the rule fits comfortably.

### `allowShortBlocksOnASingleLine`

Allows short parenthesized blocks and compact alternatives to remain on one line under the formatter's sizing heuristics.

### `columnLimit`

A smaller `columnLimit` makes the formatter more likely to expand rules, blocks, and comments across multiple lines.

## Indentation and spacing options

### `useTab`, `tabWidth`, and `indentWidth`

These options work together:

- when `useTab = off`, indentation uses spaces and `indentWidth`
- when `useTab = on`, indentation uses tabs and `tabWidth` controls visual measurements
- `tabWidth` still matters even with tabs because the formatter uses it for alignment and comment-width calculations

### `continuationIndentWidth`

This applies when long output must wrap beyond the current indentation level.

### `spaceBeforeAssignmentOperators`

Controls whether spaces appear before assignment-like operators such as `=` and `+=`.

## Empty-line handling

### `maxEmptyLinesToKeep`

Limits how many consecutive blank lines are preserved.

### `minEmptyLines`

Requests a minimum number of blank lines after top-level constructs, but the effective result is still capped by `maxEmptyLinesToKeep`.

### `keepEmptyLinesAtTheStartOfBlocks`

Preserves block-leading blank lines that would otherwise be collapsed.

## Rule-internal layout options

### `ruleInternalsOnSingleLine`

When enabled, some rule-internal clauses stay inline rather than being forced onto their own lines. This particularly affects constructs such as:

- `returns`
- `locals`
- in-rule named actions introduced with `@`

### `breakBeforeParens`

When enabled, the formatter prefers a line break before parenthesized blocks.

## Caveats and implementation notes

### `disabled` is not a recognized inline option name

The API model contains a `disabled` field, and generated option comments may serialize it. However, for inline directives inside grammars, the supported control directives are:

- `// $antlr-format off`
- `// $antlr-format on`

Do **not** rely on:

```antlr
// $antlr-format disabled true
```

Use `on` / `off` instead.

### `breakBeforeBraces` only affects block-style opening braces

`breakBeforeBraces` is implemented for brace-bearing block constructs such as:

- `options { ... }`
- `tokens { ... }`
- `channels { ... }`
- top-level named-action blocks such as `@parser::members { ... }`

It is **not** a blanket “move every `{` to a new line” rule.
In particular, inline rule actions such as:

```antlr
a: 'a' {doSomething();};
```

remain inline.

### Invalid directive entries are visible in output

Malformed or unsupported directives do not fail silently; they cause the formatter to emit an explicit error marker in the formatted output.

### Generated formatter-option comments are wrapped

When the formatter emits an options comment automatically, it wraps long directive lines at approximately 130 characters.

## Recommended usage patterns

### Temporarily disable formatting for a region

```antlr
// $antlr-format off
messyRule   :   'a'|'b'   ;
// $antlr-format on
```

### Apply a local formatting override

```antlr
// $antlr-format alignLabels off, columnLimit 120
```

### Reset back to defaults

```antlr
// $antlr-format reset
```

## Related documentation

- [Project README](../README.md)
- [Command line interface](cli.md)
- [Maven plugin guide](maven-plugin.md)
- [Contributing guide](../CONTRIBUTING.md)



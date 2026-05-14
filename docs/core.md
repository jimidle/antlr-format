# Core library guide

The `antlr-format-core` module exposes the formatter engine and supporting API for Java integrations.
It is the right dependency when you want to format ANTLR grammars from your own application, service, plugin, or test harness.

## Maven dependency

```xml
<dependency>
  <groupId>ws.idle</groupId>
  <artifactId>antlr-format-core</artifactId>
  <version>1.0.2</version>
</dependency>
```

## Main API types

The public API centers on these types:

- `GrammarFormatter` – formats grammar text directly
- `FormattingOptions` – option model matching the inline formatter directives
- `FormattingConfiguration` – main/lexer split configuration for grammar-kind-aware formatting
- `AntlrFormatterService` – higher-level entry point that chooses the appropriate option block
- `FormattingResult` – formatted text plus the affected source range

## Format a grammar string directly

```java
FormattingOptions options = new FormattingOptions();
options.reflowComments = true;
options.alignLabels = true;

GrammarFormatter formatter = new GrammarFormatter(grammarText);
FormattingResult result = formatter.formatGrammar(options);

String formatted = result.text();
```

## Format with grammar-kind-aware configuration

```java
FormattingConfiguration configuration = new FormattingConfiguration();
configuration.main = FormattingOptions.defaults();

FormattingOptions lexerOptions = new FormattingOptions();
lexerOptions.alignTrailers = true;
configuration.lexer = lexerOptions;

AntlrFormatterService service = new AntlrFormatterService();
FormattingResult result = service.format(grammarText, configuration, false, 0, Integer.MAX_VALUE);
```

## Emit formatter directives as a comment

```java
String comment = GrammarFormatter.convertToComment(FormattingOptions.defaults());
```

That allows you to serialize an option set into a `// $antlr-format ...` comment block.

## Directive and option reference

For the full directive vocabulary and option semantics, see:

- [`formatter-directives.md`](formatter-directives.md)

## Related modules

- CLI guide: [`cli.md`](cli.md)
- Maven plugin guide: [`maven-plugin.md`](maven-plugin.md)


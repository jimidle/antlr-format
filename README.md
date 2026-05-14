# antlr-format

`antlr-format` is a Java implementation of the ANTLR grammar formatter, packaged both as:

- a reusable formatter library
- a standalone runnable CLI jar
- a Maven plugin for formatting `.g4` files in place during builds or from the command line

The repository is organized as a Maven multi-module build so the formatter core can be used directly by tools and
applications while the plugin exposes the same behavior to Maven-based projects.

This rewrite is based on the original formatter project by Mike Lischke:

- original repository: <https://github.com/antlr-ng/antlr-format>
- original author: Mike Lischke

## Repository layout

- `antlr-format-core` – formatter engine, API surface, lexer support, and shared output helpers
- `antlr-format-cli` – standalone `java -jar` command line formatter
- `antlr-format-maven-plugin` – Maven goal `antlr-format:format`

The CLI module now also produces installable distribution archives containing:

- runnable wrapper scripts for Unix-like shells, Windows `cmd.exe`, and PowerShell
- generated shell completion files for Bash, Zsh, and Fish
- a Homebrew formula template that targets the distribution layout but is not published yet

## Requirements

- JDK 21 or newer
- Maven 3.9+ recommended

The build is validated in GitHub Actions with Java 21.

## Build from source

Run the full verification build:

```bash
mvn -B --no-transfer-progress verify
```

Build the Maven plugin artifact explicitly:

```bash
mvn -B --no-transfer-progress -pl antlr-format-maven-plugin -am package
```

Build the standalone CLI jar explicitly:

```bash
mvn -B --no-transfer-progress -pl antlr-format-cli -am package
```

That command also builds installable CLI distribution archives at:

```text
antlr-format-cli/target/antlr-format-cli-1.0.0.zip
antlr-format-cli/target/antlr-format-cli-1.0.0.tar.gz
```

Run only the core module tests:

```bash
mvn -B --no-transfer-progress -pl antlr-format-core test
```

## Using the formatter core API

The core module exposes a small API centered around `GrammarFormatter`, `FormattingOptions`, and
`AntlrFormatterService`.

### Format a grammar string directly

```java
FormattingOptions options = new FormattingOptions();
options.reflowComments = true;
options.alignLabels = true;

GrammarFormatter formatter = new GrammarFormatter(grammarText);
FormattingResult result = formatter.formatGrammar(options);

String formatted = result.text();
```

### Format with grammar-kind-aware configuration

`AntlrFormatterService` can automatically choose between a main option set and a lexer-specific option set:

```java
FormattingConfiguration configuration = new FormattingConfiguration();
configuration.main = FormattingOptions.defaults();

FormattingOptions lexerOptions = new FormattingOptions();
lexerOptions.alignTrailers = true;
configuration.lexer = lexerOptions;

AntlrFormatterService service = new AntlrFormatterService();
FormattingResult result = service.format(grammarText, configuration, false, 0, Integer.MAX_VALUE);
```

### Emit formatter options as a comment

You can serialize options into an `$antlr-format` comment block:

```java
String comment = GrammarFormatter.convertToComment(FormattingOptions.defaults());
```

Or ask `GrammarFormatter` / `AntlrFormatterService` to inject the effective options automatically when formatting.

## Using the standalone CLI

Build the CLI jar:

```bash
mvn -B --no-transfer-progress -pl antlr-format-cli -am package
```

Format a grammar to standard output:

```bash
java -jar antlr-format-cli/target/antlr-format-cli-1.0.0.jar path/to/Grammar.g4
```

Overwrite the input file in place:

```bash
java -jar antlr-format-cli/target/antlr-format-cli-1.0.0.jar --write path/to/Grammar.g4
```

Inject the effective formatter options as a comment when the grammar does not already contain formatter directives:

```bash
java -jar antlr-format-cli/target/antlr-format-cli-1.0.0.jar --add-options path/to/Grammar.g4
```

If you prefer an installable command instead of invoking `java -jar` directly, unpack one of the CLI distribution archives and
run the wrapper from `bin/antlr-format`.
The archive also contains shell completion files under `completions/` for Bash, Zsh, and Fish.

The CLI exposes a flag for every inline formatter option described in the directive reference, and inline grammar comments
take precedence over command line flags.

See [`docs/cli.md`](docs/cli.md) for the complete CLI option table, output modes, and precedence rules.
See [`docs/homebrew.md`](docs/homebrew.md) for the Homebrew-targeted packaging layout and formula-template notes.

## Using the Maven plugin

The Maven plugin formats grammar files from a source directory, defaulting to `src/main/antlr4`.

### Basic configuration

```xml
<plugin>
  <groupId>ws.idle</groupId>
  <artifactId>antlr-format-maven-plugin</artifactId>
  <version>1.0.0</version>
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
    </main>
  </configuration>
</plugin>
```

### Plugin options

The plugin supports these top-level parameters:

- `sourceDirectory` – root directory to scan for grammars
- `includes` – include glob patterns, defaulting to `**/*.g4`
- `excludes` – exclude glob patterns
- `skip` – skip formatting entirely
- `addOptions` – emit the effective formatter options as a directive comment
- `dryRun` – report files that would change without rewriting them
- `encoding` – source file encoding, default `UTF-8`
- `main` – main formatter option set
- `lexer` – optional lexer-specific formatter option set

The nested `main` and `lexer` blocks accept every formatter option that can be set inline with
`// $antlr-format ...`, using the same option names as the directive reference.

### Configuration precedence

Formatter behavior is resolved in this order:

1. built-in formatter defaults
2. Maven plugin configuration from `main` or `lexer`
3. inline grammar directives inside the grammar itself

That means grammar comments always override the plugin configuration.
`// $antlr-format reset` resets back to the built-in defaults, not back to the surrounding Maven configuration.

### `addOptions` behavior

When `addOptions` is enabled, the formatter injects the effective option set as a directive comment at the top of the
output only if the grammar does not already contain any formatter directives.

See [`docs/maven-plugin.md`](docs/maven-plugin.md) for the complete plugin guide.

### Dry-run example

```bash
mvn -B --no-transfer-progress antlr-format:format -Dantlr-format.dryRun=true
```

## Formatter directives inside grammars

The formatter understands `$antlr-format` directives embedded in comments. These can be used to:

- turn formatting on or off for selected regions
- reset to default options
- override individual options inline

Examples:

```antlr
// $antlr-format off
// $antlr-format on
// $antlr-format alignLabels on, columnLimit 120
// $antlr-format reset
```

For the complete directive reference, defaults, option interactions, and caveats, see
[`docs/formatter-directives.md`](docs/formatter-directives.md).

Inline formatter directives always take precedence over external configuration supplied from the Maven plugin or CLI.

## Development workflow

This repository uses a protected `main` branch and a feature-branch + pull-request workflow.

- direct pushes to `main` are blocked
- pull requests are required
- the GitHub Actions `build` check must pass before merge

See [`CONTRIBUTING.md`](CONTRIBUTING.md) for branch naming and the recommended command-line workflow.

## Continuous integration

GitHub Actions runs the following command for pushes to `main` and for pull requests targeting `main`:

```bash
mvn -B --no-transfer-progress verify
```

This keeps local verification and remote verification aligned.

## Legacy helper runner

The core module still includes a small helper runner class:

```bash
java -cp antlr-format-core/target/antlr-format-core-1.0.0.jar \
  ws.idle.antlr.formatter.FormatterRunner path/to/Grammar.g4
```

That runner is intentionally minimal and primarily useful for local experimentation.
For a supported end-user command line interface, prefer the standalone CLI module documented in [`docs/cli.md`](docs/cli.md).

## License

Add the project license information here once the repository license file is finalized.


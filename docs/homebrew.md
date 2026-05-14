# Homebrew packaging notes

The CLI is now packaged with a Homebrew-oriented distribution layout, but it is **not** published to Homebrew yet.
This document describes the artifacts produced by the build so the next PR can wire them into a release workflow.

## Build the CLI distribution archives

From the repository root:

```bash
mvn -B --no-transfer-progress -pl antlr-format-cli -am package
```

The CLI module produces these archives:

```text
antlr-format-cli/target/antlr-format-cli-1.0.0.zip
antlr-format-cli/target/antlr-format-cli-1.0.0.tar.gz
```

## Distribution layout

Each CLI archive contains the following installable layout:

```text
bin/
  antlr-format
  antlr-format.cmd
  antlr-format.ps1
lib/
  antlr-format-cli-1.0.0.jar
completions/
  antlr-format.bash
  _antlr-format
  antlr-format.fish
homebrew/
  antlr-format.rb
```

That layout is intentionally compatible with a Homebrew formula that installs the archive into `libexec/`, then:

- exposes `bin/antlr-format` as the user-facing command
- installs Bash completion from `completions/antlr-format.bash`
- installs Zsh completion from `completions/_antlr-format`
- installs Fish completion from `completions/antlr-format.fish`

## Formula template

The build includes a formula template at:

```text
antlr-format-cli/src/main/dist/homebrew/antlr-format.rb
```

The packaged copy is also embedded in each CLI distribution archive under `homebrew/antlr-format.rb`.

Before publishing, the next PR should replace these placeholders in the template:

- `__ARCHIVE_URL__`
- `__ARCHIVE_SHA256__`

## What this PR prepares

This PR intentionally stops short of publishing anything.
It prepares Homebrew publication by ensuring the CLI build already emits:

- a stable release version (`1.0.0`)
- installable wrapper scripts
- generated shell completions
- a consistent archive layout that a future formula can install without repackaging

## Next PR ideas

The follow-up Homebrew PR can focus on:

1. attaching the CLI tarball to a GitHub release
2. filling in the final `url` and `sha256` in the formula template
3. deciding whether the formula lives in this repository or a separate tap
4. optionally adding a CI job that validates the formula against the release archive


# Homebrew packaging notes

The CLI is now published through the public tap:

- tap repository: <https://github.com/jimidle/homebrew-antlr-format>
- install command: `brew install jimidle/antlr-format/antlr-format`

That tap-qualified command is already a one-command install.
You do **not** need to run `brew tap` separately unless you prefer that workflow.
However, a tap is still required for now because the formula is not in `homebrew-core`.
Homebrew's public criteria for new self-submitted formulae currently include significantly higher notability thresholds than this repository currently meets, so a core-formula PR would not be a strong submission today.
Only a future accepted `homebrew-core` formula would enable:

```bash
brew install antlr-format
```

Homebrew does not require a separate publisher-registration workflow.
In practice, publishing is done by maintaining a public tap repository that contains the formula and points at public release assets.

## Install from Homebrew

```bash
brew install jimidle/antlr-format/antlr-format
```

Or, if you prefer to add the tap first:

```bash
brew tap jimidle/antlr-format
brew install antlr-format
```

## Build the CLI distribution archives

From the repository root:

```bash
mvn -B --no-transfer-progress -pl antlr-format-cli -am package
```

The CLI module produces these archives:

```text
antlr-format-cli/target/antlr-format-cli-1.0.2.zip
antlr-format-cli/target/antlr-format-cli-1.0.2.tar.gz
```

## Distribution layout

Each CLI archive contains the following installable layout:

```text
bin/
  antlr-format
  antlr-format.cmd
  antlr-format.ps1
lib/
  antlr-format-cli-1.0.2.jar
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

When cutting a new Homebrew release, replace these placeholders in the template:

- `__ARCHIVE_URL__`
- `__ARCHIVE_SHA256__`

The checked-in template already includes:

- the public upstream repository homepage
- `license "Apache-2.0"`
- the install/test logic used by the published tap formula

## Published formula source

The tap formula downloads versioned CLI archives from GitHub releases in the main project repository.
For the `1.0.2` release, the tarball URL is:

```text
https://github.com/jimidle/antlr-format/releases/download/v1.0.2/antlr-format-cli-1.0.2.tar.gz
```

with SHA-256:

```text
03fdf17f2ba7edb2f68e710695a145096394fc475de71bbefa5c1b2346fa5335
```

## Release maintenance checklist

For future CLI releases:

1. build the CLI distribution archives from this repository
2. compute the new archive SHA-256
3. upload the archives to a release in `jimidle/antlr-format`
4. update `Formula/antlr-format.rb` in the tap repo with the new `url` and `sha256`
5. run:

   ```bash
   brew audit --strict --online jimidle/antlr-format/antlr-format
   brew install jimidle/antlr-format/antlr-format
   brew test antlr-format
   ```

6. commit and push the tap update

## What the repository already provides

This repository provides the build inputs used by the published Homebrew tap:

- a stable release version (`1.0.2`)
- installable wrapper scripts
- generated shell completions
- a consistent archive layout that the tap formula can install without repackaging


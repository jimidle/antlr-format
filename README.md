# antlr-format (Java)

Java rewrite of `antlr-format` as a Maven plugin, with the formatter logic placed in a reusable core module.

## Modules

- `antlr-format-core`: formatter API + ANTLR lexer generation.
- `antlr-format-maven-plugin`: Maven goal `antlr-format:format`.

## Quick Start

```bash
mvn -q test
mvn -q -pl antlr-format-maven-plugin -am package
```

## Contributing

This repository uses protected `main`, short-lived feature branches, and pull requests.
See [`CONTRIBUTING.md`](CONTRIBUTING.md) for the branch naming convention and CLI workflow.

Example plugin usage in a project:

```xml
<plugin>
  <groupId>ws.idle</groupId>
  <artifactId>antlr-format-maven-plugin</artifactId>
  <version>1.0.0-SNAPSHOT</version>
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
      <reflowComments>false</reflowComments>
      <alignLabels>true</alignLabels>
    </main>
  </configuration>
</plugin>
```

## Status

This is the initial migration scaffold:

- Maven multi-module build is in place.
- ANTLR lexer generation and adapter are wired.
- Public core API is implemented.
- Full TypeScript formatting pipeline parity is still in progress.


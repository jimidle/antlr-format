# Maven Central publication

This project is published to Sonatype Central under the `ws.idle` namespace.
The `ws.idle` namespace is already verified, the Maven publishing setup is active, and release `1.0.3`
is live in Central.

## Current published coordinates

The current published release is:

- `ws.idle:antlr-format-parent:1.0.3`
- `ws.idle:antlr-format-core:1.0.3`
- `ws.idle:antlr-format-maven-plugin:1.0.3`
- `ws.idle:antlr-format-cli:1.0.3`

Useful Central pages:

- parent: <https://central.sonatype.com/artifact/ws.idle/antlr-format-parent>
- core: <https://central.sonatype.com/artifact/ws.idle/antlr-format-core>
- Maven plugin: <https://central.sonatype.com/artifact/ws.idle/antlr-format-maven-plugin>
- CLI: <https://central.sonatype.com/artifact/ws.idle/antlr-format-cli>

## What the build is already configured to provide

The parent POM and published modules now provide the Maven Central prerequisites Sonatype expects:

- project URL, license, developer, SCM, and issue tracker metadata
- explicit child-artifact metadata so each Central artifact page can expose its own documentation and repository links
- attached `-sources.jar` artifacts for non-`pom` modules
- attached `-javadoc.jar` artifacts for non-`pom` modules
- a dedicated `central-publish` profile that:
  - signs artifacts with GPG
  - uses Sonatype's `central-publishing-maven-plugin`

The profile is defined in the parent `pom.xml` and applies across the full reactor.

## What still cannot be completed fully from the command line

Based on Sonatype's current Central documentation:

- initial Central account creation is handled in the Central Portal
- namespace claiming / verification is handled in the Central Portal
- user-token generation is handled from the Central Portal account page

After the account, namespace, and user token exist, publishing itself can be driven from Maven on
the command line.

For this repository, those initial portal steps have already been completed successfully. The rest of
this guide is kept as release-maintenance documentation for future publishers and future machines.

## Initial publisher registration checklist

These steps are already complete for `ws.idle`, but they are the steps you would repeat if you ever
needed to recreate publisher access from scratch.

### 1. Create or confirm a Central account

Open the Central Portal and sign in:

- <https://central.sonatype.com/>

If you are unsure whether you already have an account, try signing in first before creating a new
one.

### 2. Claim the `ws.idle` namespace

From the Central Portal:

1. open the namespace registration flow
2. add the namespace `ws.idle`
3. choose DNS-based verification
4. follow the portal's instructions for the required TXT record

Because you control `idle.ws`, DNS verification should be the right path for this namespace.

### 3. Add the DNS TXT record

Sonatype will show the exact TXT record name and value in the portal.
Those values are generated per verification attempt, so they cannot be pre-filled safely here.

Typical DNS-provider CLI flows look like this, but the exact command depends on your DNS host:

```bash
# Example only: use the actual host/value shown in the Central Portal
provider-cli dns record create \
  --type TXT \
  --name "<host shown by Sonatype>" \
  --value "<value shown by Sonatype>"
```

After the record is visible publicly, return to the portal and complete verification.

### 4. Generate a Central user token

Once the namespace is verified:

1. open the Central Portal account page
2. generate a user token
3. store the token username and password securely

## GPG signing setup

Central requires signed artifacts.
The project's `central-publish` profile expects a working local GPG setup.

### Generate a key

```bash
gpg --full-generate-key
```

### List secret keys

```bash
gpg --list-secret-keys --keyid-format=long
```

### Optional: export the public key for backup or manual distribution

```bash
gpg --armor --export <KEY_ID> > public-key.asc
```

If your local `gpg` installation already works for signing files interactively, the Maven profile
should be able to use it as-is.

## Maven `settings.xml` configuration

Sonatype's Maven plugin looks up credentials by server id.
This project uses the server id `central` by default.

Add this to `~/.m2/settings.xml` after you generate the Central user token:

```xml
<settings>
  <servers>
    <server>
      <id>central</id>
      <username><!-- Sonatype Central token username --></username>
      <password><!-- Sonatype Central token password --></password>
    </server>
  </servers>
</settings>
```

## Local dry-run style validation

This verifies the full reactor with the Central release profile enabled while skipping actual GPG
signing:

```bash
mvn -B --no-transfer-progress -Pcentral-publish -Dgpg.skip=true verify
```

If you want to rehearse a deploy-shaped build without uploading, the profile also exposes
`central.skipPublishing`.
However, Sonatype's Maven plugin still expects a `central` server entry to exist in Maven settings,
even when upload is skipped.

```bash
mvn -B --no-transfer-progress \
  -Pcentral-publish \
  -Dgpg.skip=true \
  -Dcentral.skipPublishing=true \
  deploy
```

For a throwaway local rehearsal, a temporary settings file containing a dummy `central` server is
sufficient:

```xml
<settings>
  <servers>
    <server>
      <id>central</id>
      <username>dummy</username>
      <password>dummy</password>
    </server>
  </servers>
</settings>
```

## Publishing from the command line after registration

Once all of the following are true:

- the Central account exists
- the `ws.idle` namespace is verified
- the `central` server credentials are present in `~/.m2/settings.xml`
- a working GPG key is available locally

publish with:

```bash
mvn -B --no-transfer-progress -Pcentral-publish deploy
```

For the fully automated publication flow used for release `1.0.3`, run:

```bash
mvn -B --no-transfer-progress \
  -Pcentral-publish \
  -Dcentral.auto.publish=true \
  -DwaitUntil=PUBLISHED \
  deploy
```

By default, this project leaves Sonatype publishing in manual mode after upload/validation so you
can perform a last inspection in the Central Portal.

If you later decide to make CI releases fully automatic, the parent POM property
`central.auto.publish` can be set to `true`.

## Suggested release workflow

For future Central releases, use this order:

1. verify the namespace in the Central Portal
2. generate a user token
3. confirm `gpg --list-secret-keys` shows the signing key you want to use
4. run:

```bash
mvn -B --no-transfer-progress -Pcentral-publish verify
```

5. then publish:

```bash
mvn -B --no-transfer-progress -Pcentral-publish deploy
```

6. if you are not using automatic publish, inspect the deployment in the Central Portal and publish it there

## Related documentation

- [Project README](../README.md)
- [CLI guide](cli.md)
- [Maven plugin guide](maven-plugin.md)


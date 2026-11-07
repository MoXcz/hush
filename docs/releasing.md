# Releasing

A version tag builds everything on GitHub Actions (`.github/workflows/release.yml`) and publishes a
GitHub release with:

- `hush-<version>-arm64-v8a.apk`, `-armeabi-v7a.apk`, `-x86_64.apk`: one APK per CPU type
  (almost every current phone is arm64-v8a).
- `hush-<version>-universal.apk`: works on every phone, twice the size.
- `hush-<version>-linux-x86_64.tar.gz`: the desktop app image (with its own Java runtime); unpack
  it and run `Hush/bin/Hush`.
- `SHA256SUMS`.

Before building, it runs the CI workflow (`.github/workflows/ci.yml`: `ktlintCheck check
:androidApp:assembleDebug`, the same as on every push to `main` and every pull request), so a
release with failing tests isn't published. Every APK must be signed with the key whose certificate
SHA-256 is `RELEASE_CERT_SHA256` at the top of `release.yml`, or the run fails: that's the key on
your phone, and a wrong secret would give APKs that can't update it. If you ever change the key,
change that value too.

Every file gets a build provenance attestation, so anyone can check a download was built by this
workflow from this repo:

```sh
gh attestation verify hush-1.0.0-arm64-v8a.apk -R MoXcz/hush
```

R8's `mapping.txt` is kept as the `mapping` artifact of the workflow run (90 days), for reading
stack traces from that release.

## Actions

Every third-party action is pinned to a commit SHA, with the version in a comment, so a moved tag
can't change what runs with the signing secrets. Dependabot (`.github/dependabot.yml`) opens a pull
request when an action has a new version that is at least two weeks old.

## Version codes

Each APK gets `app-versionCode × 10 + CPU digit`: universal 0, armeabi-v7a 1, arm64-v8a 2, x86_64 3
(`androidApp/build.gradle.kts`). Raise `app-versionCode` by one for every release so every APK of
the new version is newer than every APK of the old one.

## One-time setup: the `release` environment

The APKs must be signed with the same key as the app already on your phone, or Android refuses
the update. The key lives only in a protected environment, so no workflow run gets it without your
approval: a compromised action or a pushed workflow can't sign with it on its own.

1. Settings → Environments → New environment `release`. Under "Deployment protection rules" add
   yourself as a required reviewer; under "Deployment branches and tags" choose "Selected branches
   and tags" and add the tag rule `v*`.
2. Add the four environment secrets (from `keystore.properties` and the keystore file it names):

   | Secret | Value |
   | --- | --- |
   | `HUSH_KEYSTORE_BASE64` | the keystore file, base64 (`base64 -w0 <keystore file>`) |
   | `HUSH_KEYSTORE_PASSWORD` | `storePassword` |
   | `HUSH_KEY_ALIAS` | `keyAlias` |
   | `HUSH_KEY_PASSWORD` | `keyPassword` |

   With the GitHub CLI, from the repo root:

   ```sh
   base64 -w0 <keystore file> | gh secret set HUSH_KEYSTORE_BASE64 --env release
   gh secret set HUSH_KEYSTORE_PASSWORD --env release   # paste the value when asked
   gh secret set HUSH_KEY_ALIAS --env release
   gh secret set HUSH_KEY_PASSWORD --env release
   ```

Don't keep repository-level copies of these secrets: any workflow could read those without approval.

## Making a release

1. Raise `app-versionName` and `app-versionCode` in `gradle/libs.versions.toml` and commit.
2. Tag and push: `git tag v1.0.0 && git push origin main v1.0.0`. The workflow fails if the tag
   doesn't match `app-versionName`.
3. When CI and the desktop build are done, the run waits at the Android job: open it in the Actions
   tab, "Review deployments", tick `release` and approve. Only then does it get the key.
4. Check the release page once the workflow finishes.

## Licences in a release

The source for every release is the tagged commit in this repo (GPL-3.0-or-later). The desktop
tarball bundles an OpenJDK runtime (GPL-2.0 with the Classpath Exception); its source is at
https://github.com/openjdk/jdk21u.

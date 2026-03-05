# Releasing

- This document describes the process for cutting a new release of the **Kontext Kotlin SDK**.
- Follow these steps to ensure consistency across releases.
- Replace version `1.0.0` with the proper one instead.

> We use versioning without a `v` prefix (e.g. `1.0.0`, not `v1.0.0`).

---

## 1. Create a release branch and test

1. Checkout branch `main`
2. Pull the latest changes
3. Create a new branch `release/1.0.0`
4. Make sure it builds: `./gradlew :ads:build`
5. Run the full CI sequence and make sure everything is green:
   ```bash
   ./gradlew spotlessCheck detekt assembleDebug testDebug
   ```
6. Run the example app and make sure it's OK

## 2. Update the changelog

Edit `CHANGELOG.md` to include the new release notes at the top.

Standard release:
```markdown
## 1.0.0
* Add new feature.
* Fix some bug.
* Remove old feature.
```

If the release contains breaking changes, add a `### Breaking` section before the bullet points:
```markdown
## 2.0.0
### Breaking
Short description of what changed and what integrators need to do.

* Add new feature.
* Fix some bug.
```

## 3. Update the SDK version

Update the version in `gradle/libs.versions.toml`:

```toml
[versions]
sdkkotlin = "1.0.0"
```

## 4. Commit changes

```bash
git add CHANGELOG.md gradle/libs.versions.toml
git commit -m "Prepare release 1.0.0"
```

## 5. Open pull request

1. Create a PR to `main` named: "Release version 1.0.0" and use the last changelog entry as the PR description.
2. Merge the PR to `main`.

## 6. Create an annotated tag

Pushing the tag triggers the `publish_sdk.yml` GitHub Actions workflow.

```bash
git checkout main
git pull
git tag -a 1.0.0 -m "Release 1.0.0"
git push origin 1.0.0
```

## 7. Verify publish workflow

The `publish_sdk.yml` workflow runs automatically and publishes to Maven Central via:
```
./gradlew :ads:publish -PsdkVersion=1.0.0 ...
```
Check the GitHub Actions run to confirm it succeeded.

## 8. Verify

1. Check that the version is available on [Maven Central](https://central.sonatype.com/artifact/so.kontext/ads).
2. Integrate the new version into the internal testing app and confirm it builds and runs.
3. Release the internal testing app with the updated SDK version.

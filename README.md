# MTGA

Make Twitter Great Again. A native Android reader for X content served through
Nitter front ends such as xcancel.com. No account, no tracking, no ads.

## Status

Step 3.5 of 7. MTGA now reads. Follow handles locally, open one, and its recent
posts are fetched from whichever instance in the pool is healthiest, with
automatic failover. No merged timeline or offline cache yet.

| Step | Content | State |
| --- | --- | --- |
| 1 | Shell, theme, navigation, error model, CI | done |
| 2 | Instance pool, health probes, live Diagnostics | done |
| 3 | RSS source, follow handles, single account feed | done |
| 3.5 | HTML source promoted to primary, gated feed detection | done |
| 4 | Parser rewritten from Nitter templates, media, downloads | done |
| 4.5 | Room cache, merged timeline, Paging 3 | next |
| 5 | Profile header, deep pagination via cursor | |
| 6 | Full screen media viewer, video playback, threads | |
| 7 | Notifications, local search, import and export | |

## Design decisions

**HTML first, RSS second.** This reverses the original plan. RSS looked like the
cheap, stable path until testing showed the one surviving instance restricts
feeds to clients it has approved, answering 200 with well formed RSS whose only
item is a whitelist notice. Its web pages remain open, so HTML parsing is the
path that actually returns posts. RSS stays wired up behind it, because a
different instance makes it the better path again with no change above the
repository. Both sources normalise into one domain model.

**No single instance gets a veto.** A Nitter instance with a broken upstream
session returns 404 for real accounts. MTGA reports "account not found" only
when every instance that actually answered agrees, and probes the profile page
rather than a cheap endpoint, so a green light means posts came back.

**No hero behaviour on failure.** Nitter instances are under legal pressure from
X Corp and go down without warning. MTGA never shows a generic spinner. Every
failure is classified in `core/common/AppError.kt` and attributed to a layer:
your device, your network, the instance, X itself, or MTGA. The Diagnostics
screen makes that visible.

**minSdk 31.** Material You dynamic colour needs API 31. Supporting less means a
second theming path and older TLS stacks, which trades security and consistency
for a shrinking pool of devices.

**Single Gradle module, strict package layering.** Package boundaries mirror what
would otherwise be modules, and can be extracted later without moving classes.

## Building

Android Studio, or:

```
gradle :app:assembleDebug
```

CI builds a debug APK on every push and attaches it as an artifact. To get a
signed release, add repository secrets `KEYSTORE_BASE64`, `KEYSTORE_PASSWORD`,
`KEY_ALIAS` and `KEY_PASSWORD`.

The version catalogue in `gradle/libs.versions.toml` pins a few libraries
optimistically. If the first CI run fails to resolve one, the log names the exact
artifact and the fix is a one line change there.

## Credits

Thanks to the Nitter maintainers and the people running instances. Parts of the
approach are informed by [Nitterium](https://github.com/kaleedtc/Nitterium) (MIT),
a WebView based Nitter client.

MIT licensed.

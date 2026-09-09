# MTGA

Make Twitter Great Again. A native Android reader for X content served through
Nitter front ends such as xcancel.com. No account, no tracking, no ads.

## Status

Step 3 of 7. MTGA now reads. Follow handles locally, open one, and its recent
posts are fetched over RSS from whichever instance in the pool is healthiest,
with automatic failover. No merged timeline or offline cache yet.

| Step | Content | State |
| --- | --- | --- |
| 1 | Shell, theme, navigation, error model, CI | done |
| 2 | Instance pool, health probes, live Diagnostics | done |
| 3 | RSS source, follow handles, single account feed | done |
| 4 | Room cache, merged timeline, Paging 3 | next |
| 5 | HTML source, profiles, stats, deep pagination | |
| 6 | Threads, quotes, media viewer with video | |
| 7 | Notifications, local search, import and export | |

## Design decisions

**Hybrid data strategy.** RSS is the cheap refresh path, one request per handle
or one batched request per group using Nitter's comma separated multi account
URL form. HTML parsing runs only on demand, when a profile or a single post is
opened, to add stats, threads and media. Both normalise into one domain model,
so a new source plugs in without touching the UI.

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

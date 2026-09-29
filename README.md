# MTGA

Read public X (Twitter) posts on Android, with no account, no tracking and no ads.

## What you can do

- **Follow accounts** without an X account. Your list stays on your phone.
- **Read everything in one place.** Home shows all the accounts you follow, newest first. Pull down to refresh.
- **Filter Home**: media only, hide replies, hide reposts.
- **Look up any account** from the Accounts tab before deciding to follow it. Paste an x.com link, even one copied from a Google search.
- **Open a post** to read it in full, copy its text, share it or open it on X.
- **View photos and videos** full screen, zoom in, and save them to your phone.
- **Read offline.** Posts you have seen are saved on the phone and stay readable without a connection.
- **Choose your look**: light, dark, pure black, colours that follow your wallpaper, and liquid glass zones you can turn off.

## Privacy

- No account, no sign in, no ads, no analytics, no crash reporting.
- A link copied from a search engine or another site is cleaned on your phone, so it leads straight to the page. Google's own result links hide the address: for those only, the app asks Google once where the link leads, without cookies, and opens nothing there.
- Few permissions: internet access and network status, notifications only if you turn on new post alerts or automatic saving, one that lets a batch of saves show a single progress line instead of one per file, and one that asks Android, when you turn on background checks, to leave MTGA out of battery optimisation so the checks are not postponed.
- The accounts you follow and the posts you saved never leave your phone.
- Posts are read from public servers (see below). The option "Newest posts from X" in Settings reads the latest posts straight from X, which is faster but lets X see your IP address. You can turn it off.

## Where the posts come from

X no longer lets anyone read without an account. MTGA reads the same public posts through independent servers run by volunteers (Nitter servers such as xcancel.com), and directly from X for the newest posts when you allow it.

These servers sometimes slow down or go offline. When that happens, MTGA tells you which one and why, keeps showing your saved posts, and switches to another server. **Settings > Connection check** shows the state of each server.

Some servers occasionally ask for a **quick check** to confirm a real person is reading. MTGA usually does it by itself. If it can't, tap "Do the check" once and it is remembered.

## Install

Download the latest APK from the [Actions](https://github.com/213YaZ786/MTGA/actions) tab (open the most recent successful run, then the file under "Artifacts") and install it. Android 12 or newer is required.

## Having a problem?

Open **Settings > Activity log**, tap "Save to Downloads", and attach the file to an issue. It lists what the app asked each server and what came back. It contains no personal data beyond the accounts you opened.

## For developers

Kotlin and Jetpack Compose, single module. Build with Android Studio or:

```
gradle :app:assembleDebug
```

Built with AGP 9.3 (built-in Kotlin) and Gradle 9.5.1, which needs Android Studio Quail 2 or later. Library updates arrive as Dependabot pull requests. CI builds a debug APK on every push. For a signed release, add the repository secrets `KEYSTORE_BASE64`, `KEYSTORE_PASSWORD`, `KEY_ALIAS` and `KEY_PASSWORD`.

## Thanks

To the Nitter maintainers and the people who run its servers. Parts of the approach were inspired by [Nitterium](https://github.com/kaleedtc/Nitterium) (MIT).

MIT licensed.

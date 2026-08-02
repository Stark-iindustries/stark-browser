# Stark Browser 🌐

A lightweight, fast Android browser built for low-end devices (Android 8.0+, optimised for Android 11 with 3–4 GB RAM).

## Features

| Feature | Status |
|---|---|
| Normal web browsing (HTTP/HTTPS) | ✅ |
| Address bar + search | ✅ |
| Back / Forward / Reload / Stop | ✅ |
| Homepage (Stark Home) | ✅ |
| Multiple tabs | ✅ |
| Tab switcher | ✅ |
| Incognito / Private tabs | ✅ |
| Bookmarks | ✅ |
| Browsing history | ✅ |
| Downloads | ✅ |
| Find in page | ✅ |
| Share page / Copy URL | ✅ |
| Desktop site mode | ✅ |
| Text zoom / page scaling | ✅ |
| Open links in new tab | ✅ |
| File upload support | ✅ |
| Fullscreen video | ✅ |
| JavaScript support | ✅ (toggleable) |
| Cookies & storage | ✅ (toggleable) |
| Clear browsing data | ✅ |
| Content/ad blocking | ✅ (toggleable) |
| Dark / Light / System theme | ✅ |
| Permission handling (camera, mic, location) | ✅ |
| SSL error warnings | ✅ |
| Camera / microphone permissions | ✅ |
| Location permissions | ✅ |
| Add to home screen | ✅ |
| Do Not Track header | ✅ |
| Data saver (block images) | ✅ |
| Restore last closed tab | ✅ |
| Close all tabs | ✅ |
| Search engine choice | ✅ (Google, Bing, DuckDuckGo, Brave) |

## Architecture

- **Language:** Kotlin
- **Min SDK:** 26 (Android 8.0) — optimised for Android 11+
- **WebView:** System Android WebView (no bundled engine — keeps APK small)
- **Database:** Room (bookmarks, history, downloads)
- **Settings:** DataStore Preferences
- **Architecture:** Single-Activity + ViewModel + StateFlow
- **Tab memory management:** Caps live WebViews at 5; evicts oldest when exceeded, saves URL, restores on switch

## Getting the APK

### From GitHub Releases (recommended)

1. Go to [Releases](../../releases)
2. Download `StarkBrowser-vX.X.X.apk`
3. On your Android phone: **Settings → Security → Install unknown apps** → enable for your file manager
4. Tap the APK to install

### From GitHub Actions (latest build)

1. Go to [Actions](../../actions)
2. Click the latest **Build APK** workflow run
3. Download the `stark-browser-debug-*` artifact
4. Unzip and install `app-debug.apk`

### Build locally

Requirements: Android Studio / JDK 17 + Android SDK

```bash
git clone https://github.com/Stark-iindustries/stark-browser
cd stark-browser
gradle wrapper --gradle-version=8.4
./gradlew assembleDebug
# APK at: app/build/outputs/apk/debug/app-debug.apk
```

## Creating a Release (triggers APK build)

```bash
# Tag and push a release on GitHub
# Go to Releases → Draft a new release → Publish
# GitHub Actions will build and attach the APK automatically
```

## Project Structure

```
app/src/main/
├── java/com/starkbrowser/app/
│   ├── MainActivity.kt          # Main browser activity
│   ├── BrowserViewModel.kt      # State management
│   ├── browser/
│   │   ├── TabManager.kt        # WebView lifecycle + tab management
│   │   ├── StarkWebViewClient.kt
│   │   ├── StarkWebChromeClient.kt
│   │   ├── ContentBlocker.kt    # Domain-based ad/tracker blocking
│   │   └── DownloadCompleteReceiver.kt
│   ├── data/                    # Room entities and DAOs
│   ├── settings/                # DataStore + SettingsActivity
│   ├── ui/                      # TabSwitcher, Bookmarks, History, Downloads
│   └── model/BrowserTab.kt
├── assets/
│   └── blocklist.txt            # ~100 blocked ad/tracker domains
└── res/
    ├── layout/                  # All XML layouts
    ├── values/                  # Strings, colors, themes, dimens
    └── xml/                     # Network security, file provider paths
```

## Performance Design

- WebView count capped at 5 — older tabs get their URL saved and WebView destroyed
- No foreground service — proper `onPause`/`onResume`/`onLowMemory` lifecycle handling
- State saved to ViewModel on pause, restored on resume
- Content blocker uses a HashSet for O(1) domain lookup
- ProGuard enabled on release builds for smaller APK
- No heavy third-party dependencies

## License

MIT

# Umay TV Guide

A small Android app that does one job: display your existing XMLTV guide nicely.

## It already points at your live guide

`https://raw.githubusercontent.com/Matty77o/tv-iptv/main/guide.xml`

There is no IPTV player, M3U login, Xtream account or video playback.

## What the app does

- Opens directly to the TV guide.
- Loads `guide.xml` from GitHub.
- Shows channel logos when the XMLTV feed supplies them.
- Shared horizontal timeline across every channel.
- Pink NOW line on today's guide.
- Today + next five days.
- All / Kids / Turkish TV filters.
- Tap a programme for its title, time, description and progress.
- Manual refresh button.
- Dark, phone-first Material 3 interface.
- Custom Umay launcher icon included.

## Put it into your existing GitHub repository

Your current repository is `Matty77o/tv-iptv`.

Copy these two items from this package into the ROOT of that repository:

1. the entire `android` folder
2. `.github/workflows/build-android.yml`

Do not replace your existing EPG workflow. Both workflows can live together.

Your repository should end up roughly like:

```
tv-iptv/
├── guide.xml
├── build_epg.py
├── android/
│   ├── app/
│   ├── build.gradle.kts
│   ├── gradle.properties
│   └── settings.gradle.kts
└── .github/
    └── workflows/
        ├── update-epg.yml
        └── build-android.yml
```

## Build the APK

1. Commit/push the new files to GitHub.
2. Open the repository on GitHub.
3. Tap/click **Actions**.
4. Select **Build Umay TV Guide APK**.
5. Select **Run workflow**.
6. Wait for the build to turn green.
7. Open the completed run.
8. Under **Artifacts**, download **Umay-TV-Guide-APK**.
9. Unzip it. Inside is `app-debug.apk`.
10. Send/install that APK on each Android phone.

Android may ask you to permit your browser/files app to install unknown apps.
You only need that because this is your own sideloaded app rather than a Play
Store release.

## Changing the EPG URL later

Edit:

`android/app/src/main/java/com/matty77o/umaytvguide/MainActivity.kt`

At the top you will see:

```kotlin
private const val GUIDE_URL =
    "https://raw.githubusercontent.com/Matty77o/tv-iptv/main/guide.xml"
```

Change only that URL.

## Adding channels

You normally do not need to edit the Android app. If a new `<channel>` appears
in `guide.xml`, the app reads it automatically.

If you want a newly-added channel in a particular filter or position, update:
- `KidsIds`
- `TurkishIds`
- `PreferredOrder`

near the top of `MainActivity.kt`.

## Important

This is currently a debug/sideload APK. That is fine for your own phones.
A Play Store release would need a release signing key and a proper release build.

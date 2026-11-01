# Anikura TV — Native Android TV

A native Kotlin + Jetpack Compose Android TV client, created for the empty `anikura-` repository.

## Direction

This is a clean-room native implementation inspired by the navigation and product areas visible on the current Miruro website. It is **not** a WebView wrapper and does not copy the website's React source, logo, or artwork.

The app is designed around a TV remote:
- Collapsible left sidebar: Home, Discover, Trending, Schedule, Watchlist, Search, Announcements and Settings.
- Wide spotlight hero and lazy-loading horizontal anime rows.
- AniList-powered metadata, trending/popular catalogues and advanced search filters.
- Anime details, local watchlist and a native Media3 player screen.
- D-pad focus outlines, predictable Back handling and reduced idle animation.
- Minimum Android API 23; the app is Kotlin/Compose-only and does not bundle native ABI-specific libraries.

## Catalogue API

The first implementation uses the public AniList GraphQL catalogue endpoint. The client ID `37190` is kept as a configurable public app identifier; public catalogue queries do not require an OAuth token. User sign-in / AniList write access is not enabled in this starter.

## Playback boundary

AniList provides anime metadata, not video streams. The native player therefore accepts a direct HTTPS media URL and optional subtitle URL supplied by the user, for media they are authorised to play. No third-party stream scraping, provider bypass, or hidden website API is bundled.

## Build

The GitHub Actions workflow installs JDK 17 and Gradle 8.9, builds `app:assembleDebug`, and uploads the APK artifact. Open the project in Android Studio with Android SDK 35 installed.

## Reference and attribution

- Visual/feature reference: https://miruro.cx/
- Official domain index: https://www.miruro.com/
- Catalogue data: https://anilist.co/
- This repository contains an independently written Android implementation. It does not include Miruro's source code or brand assets. Miruro remains the property of its respective maintainers.

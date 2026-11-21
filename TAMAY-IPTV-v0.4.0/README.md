# TAMAY IPTV v0.3.0

Portrait-first M3U IPTV prototype update for phone testing and future Android Automotive adaptation.

## v0.3.0 changes
- Portrait-oriented adaptive layout: video area above playback controls and channel list below.
- Custom controls below the video: previous channel, next channel, subtitle toggle, channel guide, fullscreen.
- Fullscreen mode hides the source controls, status, channel list and all buttons; tapping the video area exits fullscreen.
- Channel guide opens the loaded channel list and allows direct channel selection.
- Search, category filter and favorites retained.
- M3U URL and local file loading retained, including the 128 MB URL playlist limit.
- Media3 player reports playback errors through the status line.

## Important limitations
- The channel guide in this version is a channel selector, not an EPG schedule. XMLTV/EPG is a later feature.
- Subtitle toggle enables/disables subtitle tracks supplied by the stream. It cannot create subtitles when the stream has none, and this version does not yet provide a subtitle track picker or external subtitle-file import.
- This source project is not a guarantee of installation or Play Store approval on Volvo EX30. Android Automotive video-app requirements and Volvo distribution policies must be checked on the target vehicle.

## Build
Open the `TAMAY-IPTV` folder in Android Studio, sync Gradle, then Build > Generate App Bundles or APKs > Build APK(s).
Debug APK: `app/build/outputs/apk/debug/app-debug.apk`.

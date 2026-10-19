# MovieBox static APK study → Pulse Player playback compatibility

Source inspected: user-provided MovieBox v3.0.15.0513.03 APK. This document records **architecture references**, not an endorsement or integration of its private content network.

## Verified from assets/appTab.json

The bundled navigation declares five primary tabs (Home, NovelHub, Fight Zone, Downloads, Me), a configuration version and 16 Home categories. Examples include Trending, Movie, TVshow, Animation, ShortTV_Discover, Live, Kids, Education, Football and Music. ShortTV uses `ShortTvTab`; its bundled URL field is empty. NovelHub uses a packaged mini-app.

The static APK also includes native codec/player libraries and offline mini-app packages. Their presence alone **does not** prove a URL is currently playable, licensed, or available on a given device.

## Playback routing — design target

| Source | Player | Requirement | Fallback |
| --- | --- | --- | --- |
| User-selected local video | Android Media3 / platform video | SAF read grant; supported codec | Explain unsupported codec, allow external player |
| Authorized HTTPS MP4/HLS | Media3 ExoPlayer | Verified public endpoint and permission to play | Report HTTP status, redirect/codec issue |
| YouTube | Official IFrame/embed or YouTube app | Follow platform embed restrictions | Open official YouTube app/browser |
| YouTube Music / other subscription provider | Official app/browser | User account and provider terms | External app handoff |
| Local TXT book | Native reader | SAF read grant | Clear encoding/file error |
| EPUB | Reader integration (pending) | User-owned or openly licensed book | Explain unsupported format |
| Public-domain novel catalog | Documented catalog API | Source attribution and license check | Browser/catalog page |

Do not extract/reuse private MovieBox tokens, API keys, DRM licenses, authenticated stream URLs or proprietary media assets. Do not claim its entire catalog is transferable.

## Acceptance gates

1. APK compiles on CI and debug artifact exists.
2. Import and playback tested on Android 9 TV (D-pad), Android phone, and tablet.
3. Screens fit system bars, display cutouts, landscape/portrait and font scaling.
4. Every menu action opens a working screen or explains unavailable capability.
5. Playback errors identify network, authorization, media format, and unsupported codec separately.
6. No background downloads of unauthorized or access-restricted content.

**Current PR scope:** initial Short Drama and TXT Novel hub, responsive layout, CI APK build gate. Full catalog, polished iconography, Media3 migration, EPUB, persistent history and comprehensive device testing remain pending.

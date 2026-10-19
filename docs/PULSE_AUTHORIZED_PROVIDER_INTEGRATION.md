# Pulse authorized provider integration contract

This is an independent compatibility layer, **not** a MovieBox streaming API or mirror.

## Provider setup
1. Obtain the provider's documented API and written authorization to access its catalog and media.
2. Configure a provider identifier and an explicit allowlist of HTTPS media hosts.
3. Backend checks account entitlement and geographic availability before returning playback data.
4. Map title, rating, category, episode and provider fields to Pulse's catalog model.
5. Set `authorized=true` only after successful entitlement verification; never infer authorization from a URL.
6. Supply the approved media host list to `PulsePlaybackContract.Source`.
7. Route direct MP4/HLS/DASH to Media3; route YouTube through its official embedded player; send catalog-only links to the provider.
8. Maintain separate settings for age-rating filtering; exclude Nollywood as a category label only.

## Security and change management
- No private MovieBox endpoints, API keys, DRM secrets, scraping or unauthorized mirrors.
- Enforce HTTPS, validate redirect chains, block private-network destinations and reject unexpected host changes.
- Never log credentials or signed media URLs.
- Monitor approved provider status endpoints and classify 401/403/404/429/5xx errors.
- Automatically retry temporary network failures within limits; require review for new hosts, permissions, license servers or authentication schemes.
- Validate licensing, terms, API rate limits and attribution before enabling a new provider.

## Smoke test matrix
- Local video opens and resumes; malformed and revoked URIs fail safely.
- Approved HTTPS HLS/DASH/MP4 plays on supported devices.
- Unapproved hosts, HTTP URLs, missing entitlement and unknown providers are rejected.
- YouTube URLs route to official embed, not a direct media extractor.
- Nollywood category absent; other movies are not excluded by country.
- Standard/Family Friendly preference survives app restart and applies across all feeds.
- Android 9 TV remote, phone portrait/landscape, tablet, subtitles, errors, and background lifecycle.

**Current state:** routing contract committed; no backend entitlement service, licensed content provider, UI wiring or physical-device smoke test yet.

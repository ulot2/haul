# Product

## Platform

android

## Users
The owner first: one person with an Android phone (Redmi Note 14) who sees videos on TikTok, Instagram and X and wants to keep them. Friends may install it later, so the app must explain itself.

## Product Purpose
Haul downloads a video from a link into the Gallery. Success: share a post to Haul, keep scrolling, and the video is in Movies/Haul within seconds, playable everywhere.

## Positioning
No account, no ads, no server of its own. yt-dlp runs on the phone and updates itself, so it keeps up when sites change. Its own app, with its own look (Freight, see DESIGN.md); it shares only the build and release setup with Ferry (ulot2/ferry).

## Operating Context
- Most downloads start from the share sheet ("Haul", or "Haul audio") and never open the app: a toast confirms, a notification shows progress, a second notification opens, shares or deletes the video. The Quick Settings tile downloads a copied link the same way.
- The app screen is the hold (the last 50 downloads, each with its picture), with the copied link waiting in a dock at the bottom. Options there: quality, and clipping a section. Settings: default quality, ask quality when sharing, the audio share target, Wi-Fi only, the tile, the engine.

## Capabilities and Constraints
- yt-dlp, Python and FFmpeg via youtubedl-android; arm64 only, to keep the APK small.
- Output: MP4 (H.264 + AAC), 720p option, or M4A audio; photos as the site serves them. Saved through MediaStore to Movies/Haul, Pictures/Haul, Music/Haul.
- Photos: Instagram photo entries come from yt-dlp's info JSON (largest thumbnail = the photo); TikTok slideshows from the post page, which yt-dlp fetches and prints (--dump-pages). Both show their items in a picker first; a TikTok photo post brings its sound along, in the video made of each picture or as a sound file next to them.
- One download at a time; others wait in the queue. Android 14+: a user-initiated data transfer job (waits for a connection, no daily limit). Android 10-13, or if Android refuses the job: a foreground service.
- No sign-in: private, age-restricted and some rate-limited posts fail with a clear reason.
- Updates: the app checks GitHub releases (public repo ulot2/haul) every 6 hours; yt-dlp updates daily (after the queue empties) and after a failure.

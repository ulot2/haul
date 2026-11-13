# Product

## Platform

android

## Users
The owner first: one person with an Android phone (Redmi Note 14) who sees videos on TikTok, Instagram and X and wants to keep them. Friends may install it later, so the app must explain itself.

## Product Purpose
Haul downloads a video from a link into the Gallery. Success: share a post to Haul, keep scrolling, and the video is in Movies/Haul within seconds, playable everywhere.

## Positioning
No account, no ads, no server of its own. yt-dlp runs on the phone and updates itself, so it keeps up when sites change. A sister app of Ferry (ulot2/ferry): same chart look, same build and release setup.

## Operating Context
- Most downloads start from the share sheet and never open the app: a toast confirms, a notification shows progress, a second notification opens the video.
- The app screen is for pasting a link, choosing quality, and the hold (the last 50 downloads).

## Capabilities and Constraints
- yt-dlp, Python and FFmpeg via youtubedl-android; arm64 only, to keep the APK small.
- Output: MP4 (H.264 + AAC), 720p option, or M4A audio. Saved through MediaStore to Movies/Haul, Music/Haul.
- One download at a time; others wait in the queue. Android 14+: a user-initiated data transfer job (waits for a connection, no daily limit). Android 10-13, or if Android refuses the job: a foreground service.
- No sign-in: private, age-restricted and some rate-limited posts fail with a clear reason.
- Updates: the app checks GitHub releases (public repo ulot2/haul) every 6 hours; yt-dlp updates daily (after the queue empties) and after a failure.

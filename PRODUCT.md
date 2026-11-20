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
- The app screen has four tabs (a bar in the ground's own color, no line): Download, where it opens (the wordmark over a feed of tilted cards, and one link field whose round button pastes; a link, pasted or found copied, opens HAUL THIS AS: a preview of the post, quality, clip a section, Download); Downloads, the hold (the last 200 downloads, each with its picture; a search box finds them by caption, title, creator, site, link or ticket number, together with the filters; long-press to select); Statuses (WhatsApp); and Settings. Settings: default quality, ask when sharing (HAUL THIS AS over the app: quality and Clip a section), the audio share target, Wi-Fi only, the tile, the engine.

## Capabilities and Constraints
- yt-dlp, Python and FFmpeg via youtubedl-android; one APK per CPU type, to keep each small: Haul.apk (arm64) and Haul-32bit.apk (32-bit ARM, for budget phones running a 32-bit Android).
- Output: MP4 (H.264 + AAC), 720p option, or M4A audio; photos as the site serves them. Saved through MediaStore to Movies/Haul, Pictures/Haul, Music/Haul.
- Photos: Instagram photo entries come from yt-dlp's info JSON (largest thumbnail = the photo); TikTok slideshows from the post page, which yt-dlp fetches and prints (--dump-pages). Both show their items in a picker first; a TikTok photo post brings its sound along, in the video made of each picture or as a sound file next to them.
- Each download keeps the post's caption, author and date (from yt-dlp's info JSON; from the page for TikTok photo posts), shown on its screen with Copy caption.
- A post already saved the same way (video, sound or GIF) and still in the Gallery asks first: Play/Open it, or Download again. Posts are matched by their id where the link has one (TikTok, Instagram, X, YouTube), else by the address without tracking; a TikTok short link is checked again once it is looked up.
- Clip the sound: a saved sound, video, or TikTok photo post's sound gets "Clip the sound" (start and end to 0.1 s, heard before saving). FFmpeg cuts the saved file into a new M4A (AAC) in Music/Haul through the same queue; no network, no yt-dlp.
- WhatsApp statuses (its own page, StatusesActivity): the statuses the user has opened, from WhatsApp's folder (Android/media/com.whatsapp/WhatsApp/Media/.Statuses; Business: com.whatsapp.w4b), read through a folder the user allows once in Android's picker (opened in that folder; persisted). Tap to copy one into the Gallery as a haul (link "whatsapp-status:…", no Copy link or Again); Save all. No network, no storage permission.
- The Download field collects links copied one by one (the clipboard holds one, and only the app on screen can read it): each new copied link joins the ones waiting, each post once, up to 30, and HAUL THIS AS reopens with them all; a list untouched for 30 minutes starts afresh.
- Shared posts that must be read first (TikTok short links, possible TikTok photo posts, Instagram posts) don't block the app they came from: they go into the hold at once (decide), the queue reads them on reader threads beside any download (Reading), and a draggable badge floats over the app (Badge; SYSTEM_ALERT_WINDOW, asked once, Settings › Floating badge) or the queue's notification shows it. A plain video then downloads (✓, badge fades); a post to pick from waits as CHOOSE: the badge pulses clay with a count, a high-importance notification says so, and a tap on either (or the row) opens the picker over the current app.
- Several links at once (copied or pasted as a list, or shared as text from notes): each post once, in the chosen quality, with no questions (a carousel whole, a TikTok photo post as pictures and sound); posts already saved or already waiting are left out and counted.
- One download at a time; others wait in the queue. Android 14+: a user-initiated data transfer job (waits for a connection, no daily limit). Android 10-13, or if Android refuses the job: a foreground service.
- No sign-in: private, age-restricted and some rate-limited posts fail with a clear reason.
- Updates: the app checks GitHub releases (public repo ulot2/haul) every 6 hours; yt-dlp updates daily (after the queue empties) and after a failure.

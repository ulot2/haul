# Haul

Haul downloads videos and photos from TikTok, Instagram, X, YouTube, Facebook, Reddit and about a thousand other sites, straight into your phone's Gallery.

In TikTok (or any app), tap **Share**, then **Haul**. The video downloads in the background and lands in the **Movies/Haul** album. Other ways in:

- **Haul audio** in the share sheet saves just the sound.
- Open Haul after copying a link: it waits at the bottom of the screen, one tap from downloading. **Options** picks the quality, or **Clip a section** of a long video.
- The **Quick Settings tile**: copy a link anywhere, pull down the shade, tap Haul.

When a download is done, its notification has **Share** (straight to WhatsApp, say) and **Delete**. In Haul, every download is a numbered row with its picture; tap one to play, share, copy its link, download it again or delete it.

Some posts ask first:

- An **Instagram post with several photos and videos** shows them all; tap the ones to leave out, then **Download**.
- A **TikTok photo post** shows its pictures the same way; tap the ones to leave out. Then choose how to save the rest: **Video** (the chosen pictures in turn, with the post's sound, made on the phone with FFmpeg), **Photos** (each chosen picture, and the sound next to them as a sound file in Music/Haul), or **Sound** (just the sound).

Tap a download, then **Play**, to watch or hear it in Haul's own player (photos and GIFs too; swipe through a post's files). The button at the top opens it in the Gallery instead.

**GIFs** stay GIFs: an X post that is a GIF is saved as a `.gif` in Pictures/Haul (X serves GIFs as silent videos; Haul turns them back). The **GIF** quality turns any video into a GIF, up to 30 seconds, 480 pixels wide.

To delete or share several downloads at once, long-press one in Haul, tap the others, then **Delete** or **Share**.

Downloads start faster when Haul has seen the link first: a copied link in the bottom bar, a link you are picking a quality or a section for, or a post in the picker, is read right away, and the download starts from what was read. A post's photos come down four at a time.

Tap a finished download to see what it **Took**, and where the time went: starting the engine, reading the link, getting the file, making it (a clip, a slideshow, a GIF) and saving it.

No account, no ads, and no server of its own: everything runs on the phone.

## How it works

Haul runs [yt-dlp](https://github.com/yt-dlp/yt-dlp), the open-source downloader, on the phone itself, through [youtubedl-android](https://github.com/yausername/youtubedl-android). That library bundles a small Python to run yt-dlp, and FFmpeg to join video and audio (YouTube, X and Instagram serve them separately).

```
Share sheet / pasted link  →  Haul  →  yt-dlp (on the phone)  →  site  →  Movies/Haul
```

Downloads run one at a time and keep going after you leave Haul. On Android 14 and later they run as a user-initiated download job: if you're offline, the link waits in the list and starts when the phone is back online. On older Android, a download that can't reach the site fails, and you tap it to try again.

Sites change often, and each change can break yt-dlp for a while. So Haul updates yt-dlp from GitHub once a day, after your downloads finish, and again when a download fails, then tries once more. **Settings › Engine › Update** does it by hand.

Videos come as MP4 with H.264 video and AAC audio, because those play everywhere: the Gallery, WhatsApp, an old laptop. When a site only offers another sound format, Haul converts the sound to AAC. Choose:

- **Best**: the highest resolution in H.264.
- **720p**: smaller files.
- **Audio**: just the sound, as M4A, in **Music/Haul**.

The default is in **Settings**, which can also ask each time you share. Photos (Instagram photo posts and carousels, TikTok slideshows) land in **Pictures/Haul**. **Wi‑Fi only** keeps links waiting until the phone is on Wi‑Fi (on Android 13 and older they stop with a note instead).

A clip is cut at the nearest keyframe, without re-encoding, so it is quick but may start a second or two early.

Haul saves through Android's MediaStore, so it needs no storage permission.

## Install

1. On the phone, download `Haul.apk` from the latest release on the [Releases page](../../releases/latest) and open it.
2. If Android asks, allow installs from your browser.
3. Open Haul once. The first start unpacks the engine, which takes a few seconds.

Haul shows a notice when a newer version is out.

Haul is built for 64-bit phones (arm64), which is nearly every phone from the last eight years.

## Limits

- **Private and age-restricted posts** need a signed-in account. Haul does not sign in, so it can't fetch them.
- **TikTok slideshows** are read from the post's page, which TikTok changes now and then; if one fails, the video posts still work.
- Instagram sometimes limits downloads without an account. Trying again later usually works.
- Download only what you have the right to keep: your own posts, or posts whose owners allow it.

## Build

GitHub Actions runs the unit tests and builds `Haul.apk` on every push to `main` or `dev` and on every pull request (see `.github/workflows/build.yml`). Pushing a tag like `v1.0.0` publishes a release.

To build on a computer with the Android SDK: `./gradlew testDebugUnitTest assembleDebug`. The wrapper fetches the right Gradle.

The APK is signed with a key kept in two repository secrets:

| Secret | What it holds |
|---|---|
| `HAUL_KEY` | The PKCS12 key file, base64-encoded |
| `HAUL_KEY_PASSWORD` | Its password |

Keep the same key forever: Android only installs an update that is signed with the same key as the installed app.

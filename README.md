# Haul

Haul downloads videos from TikTok, Instagram, X, YouTube, Facebook, Reddit and about a thousand other sites, straight into your phone's Gallery.

In TikTok (or any app), tap **Share**, then **Haul**. The video downloads in the background and lands in the **Movies/Haul** album. You can also open Haul and paste a link: if you just copied one, Haul fills it in for you.

No account, no ads, and no server of its own: everything runs on the phone.

## How it works

Haul runs [yt-dlp](https://github.com/yt-dlp/yt-dlp), the open-source downloader, on the phone itself, through [youtubedl-android](https://github.com/yausername/youtubedl-android). That library bundles a small Python to run yt-dlp, and FFmpeg to join video and audio (YouTube, X and Instagram serve them separately).

```
Share sheet / pasted link  →  Haul  →  yt-dlp (on the phone)  →  site  →  Movies/Haul
```

Sites change often, and each change can break yt-dlp for a while. So Haul updates yt-dlp from GitHub once a day, and again when a download fails, then tries once more. **Update engine** at the bottom of the screen does it by hand.

Videos come as MP4 with H.264 video and AAC audio, because those play everywhere: the Gallery, WhatsApp, an old laptop. Choose:

- **Best**: the highest resolution in H.264.
- **720p**: smaller files.
- **Audio**: just the sound, as M4A, in **Music/Haul**.

Haul saves through Android's MediaStore, so it needs no storage permission.

## Install

1. On the phone, download `Haul.apk` from the latest release on the [Releases page](../../releases/latest) and open it.
2. If Android asks, allow installs from your browser.
3. Open Haul once. The first start unpacks the engine, which takes a few seconds.

Haul shows a notice when a newer version is out.

Haul is built for 64-bit phones (arm64), which is nearly every phone from the last eight years.

## Limits

- **Private and age-restricted posts** need a signed-in account. Haul does not sign in, so it can't fetch them.
- **Photo posts** (Instagram carousels of photos, TikTok slideshows) are not supported yet. Videos inside carousels are.
- Instagram sometimes limits downloads without an account. Trying again later usually works.
- Download only what you have the right to keep: your own posts, or posts whose owners allow it.

## Build

GitHub Actions builds `Haul.apk` on every push (see `.github/workflows/build.yml`). Pushing a tag like `v1.0.0` publishes a release.

The APK is signed with a key kept in two repository secrets:

| Secret | What it holds |
|---|---|
| `HAUL_KEY` | The PKCS12 key file, base64-encoded |
| `HAUL_KEY_PASSWORD` | Its password |

Keep the same key forever: Android only installs an update that is signed with the same key as the installed app.

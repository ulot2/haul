# Design: Freight

Haul's own look: a cargo manifest. Every download is a numbered ticket with its picture. The mockup this was built
from is the "Haul redesign" canvas (App, System, Style boards).

## Principles
- **Clay is for action.** The one accent (`clay`, #C8775A) marks what you tap and what moves: the Download button,
  progress, switches that are on, the tile. Never decoration. Text in clay uses `clay_text`, darker on the light theme.
- **Failures are labelled, not just colored.** An outlined FAILED (or CANCELED) tag in ink, and the reason in words.
- **The picture leads.** Each row starts with the saved file's own frame (Android makes it from the Gallery); until
  then a steady tone per haul and the site's tag (TT, IG, X, YT…).
- **Dark first**, a matching light theme for the light system setting. Warm near-black, never pure black.

## Tokens (`values/colors.xml`, dark in `values-night/colors.xml`)
| Token | Dark | Light |
|---|---|---|
| ground | #121110 | #F3F0E9 |
| surface | #1C1B19 | #FFFFFF |
| raised | #2A2825 | #E8E3D9 |
| line | #36332E | #D6CFC2 |
| ink | #F4F0E8 | #161513 |
| ink_muted | #A8A298 | #5C574F |
| clay / clay_text | #C8775A / #C8775A | #C8775A / #9A4F33 |
| on_clay (text on clay) | #1E1408 | #1E1408 |

## Type (`res/font`, all under the SIL Open Font License)
- **Archivo Condensed Black / ExtraBold** (`Haul.Wordmark`, `Haul.Heading`, `Haul.Number`): the wordmark, sheet and
  screen headings in capitals, the download percent.
- **Archivo** Regular, SemiBold, ExtraBold: body, titles, buttons.
- **JetBrains Mono** (`Haul.Label`, `Haul.Mono`): the manifest facts in small capitals: NO. 0148, sizes, lengths.

## Parts (`values/styles.xml`, drawables)
- Buttons: `Haul.Button.Primary` (clay, 54dp), `Haul.Button.Outline` (line, 48dp), `Haul.IconButton` (44dp square).
- Choices: `Haul.Chip` (filters) and `Haul.Segment` (quality): ink fill when chosen (`setActivated`).
- `Haul.Card` for settings groups and notices; bottom sheets on `bg_sheet` with `Haul.Handle`.
- The dock: a floating card at the bottom of the hold with the copied link, or "Add a link".

## The logo: Pull
One out of the feed. Two dim cards of a feed, and the one Haul picked out of it: stepped aside, tilted 6° as if lifted
by a hand, with a soft shadow, in clay, carrying a ▶. The tilt is the signature; cards (not bars) keep it from reading as
a ☰ menu. Drawn in `res/drawable` from one geometry (108-unit canvas, scaled 0.76 into the adaptive icon):
- `ic_launcher` (+ `_mono` for Android 13 themed icons); `ic_launcher_audio` for "Haul audio" carries a ♪ instead.
- `ic_notify`: the white silhouette for the status bar, notifications and the Quick Settings tile.
- `ic_mark`: beside the wordmark in the app, in the theme's colors.
- Launch: on Android 12+ `avd_splash` plays it in 0.9 s (the feed rushes up and slows; the card is picked, tilts, turns
  clay, shows its ▶), then MainActivity fades into the app; on Android 10-11 `launch_bg` shows the still mark.
- Loading: `avd_loader`, the caught card stepping out of the feed and back, wherever Haul is reading a link.

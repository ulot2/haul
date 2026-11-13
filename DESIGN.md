# Design: Nautical chart (shared with Ferry)

Haul uses Ferry's design system unchanged: the same palette (`src/main/res/values/colors.xml`, night in
`values-night`), Barlow type, components and type roles (`styles.xml`, renamed `Haul.*`). See DESIGN.md in
ulot2/ferry for the full rules.

## What is Haul's own
- Header: a piece of chart (`drawable/chart.xml`), land in the top corner, and the magenta course running in to
  one ink harbor: things come in from one direction only. Wordmark "HAUL" top left; italic chart label
  "Any link, into your Gallery" bottom left.
- The list is "The hold" (italic heading, like Ferry's "Logbook"). Each row: title in Barlow Medium, then
  site · time · status in muted ink. Errors in the adrift red. A thin magenta progress bar while downloading.
- Launcher icon: the magenta course turned into an arrow, lowered into an ink tray, on the day-chart water.
- Magenta stays rare: the Download button, the course in the header, progress.

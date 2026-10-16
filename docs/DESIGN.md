# Design hand-off notes

The look is **"Drifter × Monument"** (chosen Oct 16 from the redesign canvas): Drifter's always-dark
palette and signal-tile layout, with Monument's typography. The code keeps the look separate from
how the app works, so it can keep changing.

| Token | Value | Use |
| --- | --- | --- |
| Night | `#110E24` | Background |
| Panel | `#1B1736` | Tiles, panels, dialogs |
| Line / Line strong | `#2C2650` / `#3A3368` | Borders, unlit signal segments |
| Text / Muted | `#EEE8FF` / `#A49CC8` | Text |
| Signal | `#43E5D6` | Strong signal, main buttons |
| Fading | `#FF5C9E` | Overdue |
| Soon | `#FFC65C` | Due soon |
| Josefin Sans | Light 300 to Bold 700 | Headings, names, numbers, spaced-capital labels |
| Nunito Sans | 400 to 700 | Body text |

Fonts are downloaded at build time by `scripts/fetch-fonts.sh` (SIL Open Font License).

## Where the look lives

| What | File |
| --- | --- |
| Colors, status colors, type, shapes, spacing, sizes | `app/src/main/java/com/geekfrend/intouch/ui/theme/Theme.kt` |
| Widget, window and launcher colors | `app/src/main/res/values/colors.xml` |
| Line icons | `app/src/main/res/drawable/ic_*.xml` |
| Widget layout | `app/src/main/res/layout/widget_due.xml`, `res/drawable/widget_*.xml` |
| App icon | `app/src/main/res/mipmap-anydpi-v26/ic_launcher.xml`, `res/drawable/ic_launcher_foreground.xml` |
| All wording | `app/src/main/res/values/strings.xml` |

Screens never hard-code a color, font size or spacing value; they read `MaterialTheme` and
`InTouchTheme` (status colors, spacing, sizes). Changing the theme file restyles every screen.

## How screens are built

- **Screens** (`ui/screens/`) and **components** (`ui/components/`) are stateless: they take plain data
  and callbacks. They can be redesigned, rearranged or replaced freely.
- **Routes** (`ui/InTouchNav.kt`) connect each screen to the view model. A redesigned screen only needs
  the same inputs and callbacks.
- **Logic** stays in `InTouchViewModel`, `data/Repository.kt` and `domain/`, which the design pass
  doesn't need to touch.

## Screens and their states

| Screen | States to design |
| --- | --- |
| Setup | permissions not granted / granted; notification access off (with the "Allow restricted settings" hint) / on |
| Due list | loading; empty; sections Overdue, Due soon, On track; compact cover-screen version |
| Contact detail | overdue / due soon / on track / snoozed; missing from contacts; empty and filled call log; compact version |
| Add / Edit | one number / several; preset or custom frequency |
| Dialogs | Log a call, Edit entry, Snooze, Skip, Delete, "Did you connect?", entry actions |
| Settings | detection on/off, access missing, archived list, backup |
| Widget | 0 people due ("Everyone's on track"); 1–6 rows on the home screen; 1–3 on the cover screen |

The cover screen layout switches on below 480 dp of screen height (`COMPACT_MAX_HEIGHT_DP` in
`InTouchNav.kt`).

## Row E (built in #9)

- Add / Edit: labelled number options, frequency and channel tiles, a "When did you last talk?" panel.
- Settings and Setup: panels, cyan switches, a segmented shortest-call picker, diamond status lines and a
  permission checklist with an Allow button per row.
- Log a call and Snooze are bottom sheets with big tiles.
- Widgets: home rows carry 5-segment signal bars; the cover screen has its own layout (`widget_cover_due.xml`)
  with card rows that turn pink when overdue and one icon button each. Widgets use the system sans-serif with
  letter spacing, since custom fonts in RemoteViews aren't reliable across launchers.

## Still to design

- The app icon is still a placeholder phone glyph (now cyan on night).

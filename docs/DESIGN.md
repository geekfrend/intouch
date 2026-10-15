# Design hand-off notes

v1 ships with a plain, functional Material 3 look on purpose. The code is set up so a visual design
can be applied later without touching how the app works.

## Where the look lives

| What | File |
| --- | --- |
| Colors (light and dark), status colors, type, shapes, spacing, avatar sizes | `app/src/main/java/com/geekfrend/intouch/ui/theme/Theme.kt` |
| Widget colors (light) | `app/src/main/res/values/colors.xml` |
| Widget colors (dark) | `app/src/main/res/values-night/colors.xml` |
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

## Known v1 design gaps

- Call buttons use text labels ("Call", "WhatsApp"); icons are left for the design pass.
- Typography and shapes are Material defaults.
- The app icon is a placeholder phone glyph.

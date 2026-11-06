# Color schemes

## Current application palette

The shared TV interface palette is defined in `tv-player/src/main/java/ua/any2b/tvplayer/ui/SharedUi.kt` as `AppColors`.

| Role | Color | Hex |
| --- | --- | --- |
| Main background | `BackgroundDark` | `#0A0B0E` |
| App surface | `SurfaceApp` | `#0F1115` |
| Dark surface | `SurfaceDark` | `#15171E` |
| Card | `SurfaceCard` | `#20232D` |
| Card hover | `SurfaceCardHover` | `#1E222D` |
| Focus / neon cyan | `NeonCyan` | `#80F5FF` |
| Primary purple | `PrimaryPurple` | `#A855F7` |
| Purple accent | `AccentPurple` | `#4F378B` |
| Secondary text | `TextSecondary` | `#94A3B8` |
| Muted text | `TextMuted` | `#B0B3C1` |
| Light text | `TextLight` | `#CCC2DC` |
| Card hover text | `TextCardHover` | `#B8C2D1` |
| Watched text | `TextWatched` | `#475569` |
| Error | `ErrorRed` | `#FFB4AB` |
| Skeleton | `SkeletonBg` | `#45405A` |
| Badge overlay | `BadgeOverlay` | `#DD15171E` |
| History overlay | `HistoryOverlay` | `#CC0F1115` |
| Light accent | `AccentLight` | `#D0BCFF` |
| Dark button text | `ButtonDarkText` | `#0F172A` |
| Light cyan surface | `NeonCyanLight` | `#E2E8F0` |

The legacy settings screen also declares a separate blue palette in `SettingsScreen.kt` (`#0000AA`, `#FFFFFF`, `#AAAAAA`, `#C0C0C0`).

## Proposed palette: `cyan-cyberpunk`

Saved for a later design pass. This palette is documented only and is **not applied** to the app yet.

### Base theme

| Role | Name | Hex |
| --- | --- | --- |
| Main background | Abyss Black | `#01010A` |
| Alternate background | Current dark background | `#0A0B0E` |
| Surface | Midnight Blue | `#051935` |
| Soft surface / surface variant | Deep Teal | `#093B54` |
| Divider | Ocean Steel | `#0D5B70` |
| Primary focus / interaction | Neon Aqua | `#1CDDE2` |
| Strong focus / interaction | Existing Neon Cyan | `#80F5FF` |
| Primary text | Ice White | `#E9FCFF` |
| Secondary text | Blue Gray | `#93B3BF` |

Semantic mapping: `background` → `#01010A` or `#0A0B0E`; `surface` → `#051935`; `surfaceVariant` → `#093B54`; `focus` → `#1CDDE2`; `focusStrong` → `#80F5FF`; `textPrimary` → `#E9FCFF`; `textSecondary` → `#93B3BF`.

### Evangelion accent pack

| Role | Name | Hex |
| --- | --- | --- |
| Positive / available / play accent | Acid Green | `#ADF182` |
| Soft green tint / glow | Pale Green | `#C7FBA5` |
| Secondary accent | Lavender | `#916CAD` |
| Dark accent surface / overlay | Deep Purple | `#523874` |
| Rare highlight / warning / new episode | Coral | `#DC7D68` |

### Usage direction

- Keep an approximate 80 / 15 / 5 balance: 80% dark neutral and cyan base, 15% green and purple accents, and 5% coral highlights.
- Cyan remains the main interaction language: focus outlines, selected tabs, progress bars, active buttons, and focus glow.
- Use acid green sparingly for play-now actions, success, availability, small badges, and hero light details. Pale green is for softer tints and glow.
- Use lavender for secondary chips, metadata badges, genres, and subtle overlays. Deep purple is for secondary panels, overlays, and dialogs, not primary text or focus.
- Reserve coral for occasional new-episode markers, warnings, promotional calls to action, or timeline markers.
- Keep 80–85% of the visible interface dark. Do not tint every surface teal or activate every accent color in the same component.
- Preserve readable text contrast when mapping the palette to existing semantic roles.

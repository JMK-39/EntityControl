# GUI preservation audit — 2026-10-05

Scope: read-only source/history/package inspection of EntityControl and CombatSystems. No production code edits, builds, client launches, profile changes, or pushes. This report is the only new file created by this audit. Core was inspected read-only. Findings distinguish source/package evidence from unverified runtime behavior.

## 1. Confirmed regression: CombatSystems spawner entity weights

`SpawnerEntityWeightScreen` changed from a 3D model grid to text selection rows in **d4a282694c5585a184f89e0b92a15cb221cf2d95** (`feat: update combat editors and translations`, 2026-09-25). `git log --all -S 'List<SelectionItem>' -- <screen>` identifies this commit. Its diff removes `COLS`, `CELL_SIZE`, `GRID_*`, and `GridScrollController`, adds `ScrollableSelectionList`/`SelectionItem`, and moves entity visualization to one right-hand preview. This change precedes the multiversion work and is not required by Minecraft 1.21 mechanics.

The latest grid baseline is the parent of d4a2826; inspected **b0a3135** still has the grid. Initial **02b57f1** and **432f35d** also have it. Later **229ad30**, **c47cdb7**, and clean worktree **ee2ad6e** retain the text rows. `D:/IDEAWork/备份干净完整源码/CombatSystems` already contains text rows, so that backup cannot supply the original grid baseline.

| Aspect | Before d4a2826 | ee2ad6e current source |
|---|---|---|
| Canvas | 640 × 360, `useCanvas(..., 6)` | Shared `CsPage` body/header layout |
| Entity collection | 4 columns × 4 visible rows; 72-pixel cells | `KineticSelectionList`: entity display name, weight, ID tooltip |
| Grid bounds | x=12, y=46, width=288, height=288 | Left body panel, search row above text rows |
| Scrollbar | x=304, y=46, width=4, height=288 | Selection-list scrolling |
| Right panel | x=316, width=312; panel y=18, height=336 | Width=236; panel inner padding=6, gap=4 |
| Model rendering | Every visible card; checkerboard; enabled indicator, weight badge, selected/hover outline; failure marker `?` | Single selected model; preview height=104 |
| Weight input | x=434, y=124, width=120 | Form label width=96; compact number field |
| Selected actions | Enable and reset: x=324/476, y=158, width=144 each | Form toggle/reset below selected model/name/ID/chance |
| Apply/defaults/cancel | Visible buttons x=324/424/524, y=326, width=96 | Apply in header; defaults in More menu; inherited Back |
| Pointer behavior | Primary click selects a card; wheel scrolls grid; Ctrl+wheel zooms hovered card; draggable scrollbar | Row selection; Ctrl+wheel registered only over selected right preview |

The entry remains world-generation configuration key **`spawner.entity_weights`**. Current `WorldGenerationConfigScreen.java:234–248` builds its open button and calls `openChild(new SpawnerEntityWeightScreen(currentValue, defaultValue, callback))`; callback updates the parent screen's draft `values`. Apply serializes positive weights through that callback and navigates back. Empty selection and excessive total weight are rejected. Search still supports name/ID/pinyin and `@namespace`; enabled entries sort first. Enabling uses default weight or 25; selected reset and all-default reset retain their existing semantics.

Minimal restoration: use the last pre-d4a2826 geometry and interactions as the visual contract, but implement them through the current Core page/widget/render APIs. Restore the 4×4 model grid, card weight/selection decoration, per-card zoom/scroll behavior, original right-side field placement, and visible bottom actions. Retain current parsing, draft callback, validation, and bounded text rendering. Do not revert entire old classes or old Core imports. Adding only model cards inside the current left panel would restore previews but would leave entry/action placement and input geometry changed.

CombatSystems original checkout is dirty; no local changes were edited. Its clean worktree ee2ad6e remains Forge 1.20.1 only (`build.gradle`, no enabled 1.21.1/26.1.2 nodes). No CombatSystems 1.21.1 GUI parity claim can be made from this source alone.

## 2. EntityControl modifier: current source and installed 1.21.1 package still use 3D

Main and preservation worktree start at **eba9185**. `EntityModifierScreen.java:356–359` supplies actual cached entities via `cards.entities(...)`, then lays out `EntityCardGrid`; line 466 renders cards. `EntityCardGrid.java:142–150` invokes `KineticEntityPreview.render` for actual entities or entity type IDs.

The generated `versions/1.21.1-neoforge/build/generated/stonecutter/main/java/...` sources have the same calls. Migration **15506e8** changes attribute Holder access and undead tags in `EntityModifierScreen`, not grid/list geometry. Text-bounds commit **aa80adf** does not edit `EntityModifierScreen` or `EntityCardGrid`. API adaptation **0334465** introduces the shared card helper; earlier modifier implementations, including **94f74cc** and initial **5d2855b**, also draw model grids. No `SelectionItem` replacement was found in this screen's inspected history.

Read-only `javap -p -c` of **D:/NEWMODS/entitycontrol-neoforge-1.21.1-26.10.4.jar** confirms `EntityCardGrid(40)`, `cards.entities`, layout/render calls, and both Entity/String overloads of `KineticEntityPreview.render`. The actual profile package is byte-identical:

- Profile: `F:/game/异界战斗幻想/.minecraft/versions/1.21.1-NeoForge_21.1.252/mods/entitycontrol-neoforge-1.21.1-26.10.4.jar`
- Both SHA256: `BA52A6B4D144489EB5B1739E854B76496285528C2941819B8112529BE23245A5`

The existing `.gradle/gui-long-text-20261004/0-01-attributes-zombie-start.png` is reported separately by the coordinating audit as showing models. This audit did not rerun the game.

The helper has a text-card fallback only when neither the supplied entity nor a type ID exists. Modifier keys originate from the successfully created cached living entities, and `byId` supplies those instances. A pure-ID view is therefore **not established as a source replacement in this modifier screen**. The exact user-visible entry/profile/screenshot still needs identification before proposing a replacement fix.

## 3. Other entity selectors/cards inspected

- CombatSystems GunMob rule cards (`GunMobScreen.java:366–369`) use `types(id -> id)`; loaded cards (`:280–281`) also render types. Their Add action (`:584`) calls Core `KineticSelectors.openEntitySelector`. These model grids were introduced in **c47cdb7** and are retained by ee2ad6e and the backup.
- CombatSystems faction `RulesTab` uses model cards for entity rules. `SelectionItem` rows are explicitly restricted to **structure mode** (`:331–357`); the default entity rule is intentionally a fallback text card. Entity types use `cards.types(...)` (`:97`). This is not a global model-to-ID conversion.
- Faction `NearbyTab` sets `list = null` and lays out model cards (`:220–221`). `refreshList` updates card keys and returns when `list == null` (`:314–317`); its remaining `SelectionItem` row code is inactive in this layout. Players use player name/faction rows plus a selected player preview; that is a different entry.
- EntityControl break-spawn `BlockEntityPoolScreen.java:110` opens Core's entity selector.
- The installed Core 1.21.1 package's `internal.client.selector.EntitySelectorScreen` bytecode has `EntityPreviewRenderer`, checkerboard rendering, `renderCanvas`, and preview wheel handling. No evidence found that this package's entity selector was replaced with pure ID buttons.

## 4. Known package mismatch and remaining uncertainty

The 1.21.1 profile contains **no CombatSystems JAR**. It contains EntityControl and a Core JAR named 26.10.4. Same-named Core packages differ:

| Location | Timestamp / size | SHA256 |
|---|---|---|
| 1.21.1 profile Core | 2026-10-04 13:42:45 / 2,126,207 bytes | `F8C93B1477F836EC7AE6B65BB7F06465BCFC752516DF404BA689A02815E04758` |
| D:/NEWMODS Core | 2026-10-04 14:02:42 / 2,126,514 bytes | `96873F49E662F17D454600BD55B5851E8942FD93791572213236FEBFF9D82B06` |

This mismatch is verified, but its behavioral effect is unverified. Both selector bytecode and modifier package retain 3D paths. No profile JAR was replaced. The inspected 26.1.2 profile contains none of CombatSystems, EntityControl, or Core under those name filters. EntityControl 26.1.2 is a reserved, disabled node; parity is not runtime-verified there.

The quick potion-selector scan found no Minecraft effect/potion selector in the inspected CombatSystems, MobAscension, or AdventureSystems screen/panel sources. CombatSystems `gear/EffectPoolScreen` selects equipment **name effects**, not potion effects. This was a limited scan, not a completeness audit of all repositories or all generated nodes; approved potion icons/translation-ID behavior should remain intact.

Priority: (1) restore the confirmed CombatSystems spawner grid/action geometry from the pre-d4a2826 baseline; (2) identify the exact pure-ID modifier view before changing its already-present 3D source; (3) reconcile source/build/profile provenance before future runtime comparison; (4) compare shared geometry and version-mechanic branches across enabled nodes, then separately validate 26.1.2 once enabled. Dummy texture findings are handled by the coordinating audit.

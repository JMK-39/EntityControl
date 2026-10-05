# GUI preservation and real modpack verification

User requirements: keep the original layouts, entry points, interactions and 3D previews visually consistent across supported Minecraft versions. Restore the dummy's vanilla inventory background. Add leading potion icons to existing effect rows without redesigning controls. Build release artifacts into `D:/NEWMODS` and verify using the complete installed `OTHERWORLD CLASH` modpack at its configured 8 GiB.

## Execution ledger

- [x] Compare actual shipped EntityControl selector bytecode and source with history; 3D cards remain present. Runtime cause remains unconfirmed.
- [x] Identify dummy background regression: commit `53a5a60` removed the original vanilla inventory overlay.
- [x] Execute a failing draw-contract check, restore the original background composition, rerun it. Correct texture alignment with existing inventory/hotbar slots.
- [x] Review KineticArmory effect-row icons independently; retain row/button geometry and behavior.
- [x] Build each enabled Forge/NeoForge node using the matching local Core, output to `D:/NEWMODS`.
- [x] Back up replaced modpack artifacts/options, deploy matching Forge artifacts and a temporary validation fixture to the complete pack.
- [x] Launch the existing pack from its existing command with 8 GiB; inspect actual dummy, 3D entity cards and potion rows in English/Chinese. Initial Forge run: 36 captures, zero fixture failures; fixture removed and options restored.
- [x] Verify NeoForge appearance using the installed 1.21.1 profile, without mixing Forge and NeoForge artifacts. Initial 36 captures; final rebuilt recipe-only run: 16 captures, zero failures; fixture removed and options restored.
- [x] Review changes, update bilingual CHANGELOGs and commit locally under XYAT. No pushes/releases.

26.1.2 is not enabled in EntityControl/KineticArmory at this baseline. Do not claim validation on that version or migrate it incidentally. Core remains read-only; document autocomplete icon API limitations instead of replacing its controls.

## Extended restoration verification

- [x] Restore CombatSystems spawner grid, original controls and per-card interactions. Seven grid/search regression tests and complete 78-test build pass.
- [x] Remove ContentStudio custom overlays over vanilla recipe backgrounds; use vanilla slot textures in its browser. Snapshot of the maintainer's 26.1.2 changes matches their later committed source; all three nodes build. Keep only our three GUI/changelog files in our commit.
- [x] Use vanilla slot textures in TACZWorkshop without moving material/result/workbench controls.
- [x] Build CombatSystems, TACZWorkshop and MobAscension actual Forge release artifacts into D:/NEWMODS. Release archives contain no fixture classes.
- [x] Deploy hash-verified final ContentStudio/CombatSystems/MobAscension artifacts into complete OTHERWORLD CLASH; run nine cases in English/Chinese at both window sizes. 36 captures, zero fixture failures. Inspect recipe backgrounds and Combat/TACZ screenshots.
- [x] Correct MobAscension's English effect names (Core's general search dictionaries intentionally provide only IDs in English), rebuild and verify its final picker/list without changing layout. Final Forge run: 16 captures, zero failures; English picker shows effect icons, names and IDs.
- [x] Verify ContentStudio's recipe-browser vanilla slots in both installed profiles: four Neo captures and four cases included in final Forge run, English/Chinese and both window sizes.
- [x] Remove final temporary fixture; restore original options and log-cleaner configuration after the owned client exits. Restored files match backup SHA256. Startup retry temporarily disables log deduplication; diagnostic report: ../../core-log-filter-startup-deadlock-20261005.md.

Same-version filenames proved insufficient to identify the first ContentStudio package: the initially installed Neo JAR still contained an old slot-overlay call. The final rebuilt JAR was checked by bytecode, deployment SHA256 and new screenshots. Do not infer a Gradle defect from this provenance mismatch.

## Tooltip follow-up

- [x] Audit addon tooltip submission: TACZ has eight fixed-width formatted requests bypassing Core screen fitting; other reviewed addon business requests use raw text/item tooltip APIs.
- [x] Preserve styled logical lines, ordering and widths while switching these eight paths to Core's raw Component API. Renderer regression: RED 11 content branches, GREEN 19 cases; independent review found no blocking issues.
- [x] Rebuild the final TACZ release into D:/NEWMODS; language keys, refmap/manifest, zero bundled fixture classes and release-Core references verified.
- [x] Install and launch the complete pack with original 8GiB settings. Final fixture: 32 captures, zero failures; all eight paths in English/Chinese at both sizes; inspect screenshots and recorded actual wrap widths.
- [x] Exit owned clients normally, remove fixture and restore original options/log configuration with exact hashes. Refresh nine installed release hashes. No Core changes, pushes or releases.

# SpeedyTools ("Build Faster Mod") — Tool Reference

This folder documents how each of the 9 tools added by this mod actually behaves,
verified against the current source (not just the in-game tooltips, which in a
couple of places are stale — noted inline where that's the case).

All 9 items live in their own creative tab ("Build Faster") and have no crafting
recipe — there is no survival-mode way to obtain them other than creative
inventory / `/give`. See `common/items/RegistryForItems.java`.

## The tools

| Doc | In-game name | Class(es) |
| --- | --- | --- |
| [wand-of-destructive-linear-conjuration.md](wand-of-destructive-linear-conjuration.md) | Wand of Destructive Linear Conjuration | `ItemSpeedyWandStrong` / `SpeedyToolWandStrong` |
| [wand-of-non-destructive-linear-conjuration.md](wand-of-non-destructive-linear-conjuration.md) | Wand of Non-destructive Linear Conjuration | `ItemSpeedyWandWeak` / `SpeedyToolWandWeak` |
| [orb-of-transmutation.md](orb-of-transmutation.md) | Orb of Transmutation | `ItemSpeedyOrb` / `SpeedyToolOrb` + `SpeedyToolComplexOrb` |
| [enchanted-sceptre-of-contour-extrusion.md](enchanted-sceptre-of-contour-extrusion.md) | Enchanted Sceptre of Contour Extrusion | `ItemSpeedySceptre` / `SpeedyToolSceptre` + `SpeedyToolComplexSceptre` |
| [arcane-claw-of-boundary-creation.md](arcane-claw-of-boundary-creation.md) | Arcane Claw of Boundary Creation | `ItemSpeedyBoundary` / `SpeedyToolBoundary` |
| [staff-of-duplication.md](staff-of-duplication.md) | Staff of Duplication | `ItemComplexCopy` / `SpeedyToolComplexCopy` |
| [staff-of-relocation.md](staff-of-relocation.md) | Staff of Relocation | `ItemComplexMove` / `SpeedyToolComplexMove` |
| [staff-of-destruction.md](staff-of-destruction.md) | Staff of Destruction | `ItemComplexDelete` / `SpeedyToolComplexDelete` |

There's also an **"In-game tester"** item (`ItemSpeedyTester` / `SpeedyToolTester`) —
a developer/debug tool, not a build tool. It's only registered when
`SpeedyToolsOptions.getTesterToolsEnabled()` is true, which is gated on the JVM
system property `speedytoolsmod.debug` (`common/SpeedyToolsOptions.java:16`,
`clientside/CombinedClientProxy.java:172`) — off by default, not documented here.

## Two tool families

Every tool falls into one of two interaction models, both driven by
`clientside.tools.ActiveTool`, which forwards per-tick/per-frame/input events to
whichever `SpeedyTool` is registered for the currently-held item
(`clientside/tools/ActiveTool.java`):

### "Simple" tools — instant, single input
`SpeedyToolSimple` subclasses (the wands, and the Orb/Sceptre while in **finite**
placement mode). One `right-click` = act immediately; one `left-click` = undo the
last action immediately. No selection step, no hold-to-confirm.

- **Left-click (down)** → instant undo of your last placement (`SpeedyToolSimple.java:60-63`).
- **Right-click (down)** → instantly place/act on whatever blocks the tool currently
  has selected under the cursor (`SpeedyToolSimple.java:64-71`).
- **Mouse wheel** → change the placement count (how many blocks are affected per
  click), 1–64, wrapping around; scrolling past 64 switches the tool into
  **infinite mode** (`ItemSpeedyTool.setPlacementCount`,
  `common/items/ItemSpeedyTool.java:74-100`). For the Orb and Sceptre, entering
  infinite mode also switches the tool from its simple (instant) behaviour to its
  complex (selection + hold-to-confirm) behaviour — see
  `SpeedyToolSimpleAndComplex.java`. The wands are `FINITE_ONLY` and never make
  this switch.
- **Undo/redo history**: a small number of fixed "simple" undo slots per player,
  see `serverside.worldmanipulation.WorldHistory`.

### "Complex" tools — selection, then hold-to-confirm
`SpeedyToolComplex` subclasses (Staff of Duplication/Relocation/Destruction, and
the Orb/Sceptre while in **infinite** mode). These make a (potentially large, up
to 256×256×256) selection first, let you drag/rotate/flip it, then require a
2-second hold to actually commit the action — full state machine and click
semantics documented in `clientside/tools/SpeedyToolComplex.java:32-155`.

Universal controls while a complex tool is equipped:

- **Short right-click** (≤ 500 ms) with no selection yet → create a selection
  from whatever's under the cursor (see "Selection shape" below).
- While a selection exists:
  - **Short left-click** (≤ 500 ms) → discard/uncreate the current selection.
  - **Short right-click, no CTRL** → toggle grab: pick up the selection so it
    follows you around, click again to drop it at the new spot.
  - **CTRL + short right-click** → flip the selection left-right (mirrors on
    whichever horizontal axis matches your facing direction,
    `SpeedyToolComplex.java:596-608`).
  - **CTRL + mouse wheel** → rotate the selection 90° per click.
  - **Long right-click hold** (≥ 2 s) → commit the action (copy/move/delete/fill)
    at the selection's current position.
  - **Long left-click hold** (≥ 2 s), with or without a selection → undo the
    previous action.
- **Any left-click while an action/backup/undo is in progress** → abort it
  (`SpeedyToolComplex.java:176-184`).
- The 500 ms / 2000 ms thresholds are `SpeedyToolsOptionsClient.getShortClickMaxDurationNS()`
  / `getLongClickMinDurationNS()` (`clientside/SpeedyToolsOptionsClient.java:12-15`) —
  not currently exposed in the mod options GUI.
- **Selection shape** depends on whether an Arcane Claw boundary field is active
  (see that tool's doc) and on the specific tool's `getFillMatcherForSelectionCreation()`:
  - Default (`SpeedyToolComplex.getFillMatcherForSelectionCreation`,
    used as-is by Copy/Move/Delete): flood-fill of every connected non-air block
    starting at the block you clicked (a "blob" that follows contiguous
    structure), clipped to the boundary field if one is active, or a full box if
    you click directly on/inside an active boundary field with no more specific
    match.
  - Orb overrides it to flood-fill only blocks matching the exact block+metadata
    you clicked on (`SpeedyToolComplexOrb.java:82-88`).
  - Sceptre overrides it to follow a contour/surface instead of flooding a solid
    blob (`SpeedyToolComplexSceptre.java`).
- When enabled in the mod's Config screen, clone-tool actions trigger a full
  world-save backup (throttled to once per 5 minutes). Automatic backups default
  to disabled on integrated servers, where existing saved preferences are preserved.
  Dedicated servers always disable these backups regardless of config — see the main
  project `CLAUDE.md` and `serverside/backup/MinecraftSaveFolderBackups.java`.

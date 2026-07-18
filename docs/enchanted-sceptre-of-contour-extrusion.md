# Enchanted Sceptre of Contour Extrusion

- **Item class**: `common.items.ItemSpeedySceptre` (unlocalized name `simplesceptre`)
- **Tool class**: `clientside.tools.SpeedyToolSimpleAndComplex` wrapping
  `SpeedyToolSceptre` (simple) + `SpeedyToolComplexSceptre` (complex) — see
  `clientside/CombinedClientProxy.java:113-132`
- **Interaction model**: **both** — same simple/complex mode switch as the Orb
  (see `orb-of-transmutation.md` and `docs/README.md`)
- **Placement count mode**: `BOTH` (`ItemSpeedySceptre.java:9`)

## What it does

Follows the **contour of a surface** (like the top of a zigzagging wall or an
uneven terrace) rather than flood-filling a solid blob, and either:

- **adds a layer** on top of the contour (extrudes it "taller"), or
- **removes the top layer** of the contour (shaves it down),

depending on what's in the hotbar slot immediately to the right of the Sceptre:

- If that slot holds a real block → **additive**: selects the non-solid
  (air/etc.) blocks sitting directly on top of the contour, then fills them
  with that block — the wall/terrace grows by one layer.
- If that slot is empty (air) → **subtractive**: selects the solid blocks that
  form the top layer of the contour itself, for deletion — the wall/terrace
  shrinks by one layer.

This additive/subtractive check is `currentBlockToPlace.block != Blocks.air`,
computed fresh every frame from the hotbar
(`SpeedyToolSceptre.java:56-58`, `SpeedyToolComplexSceptre.java:112-119`).

## Simple mode (finite placement count, 1–63)

Instant right-click/left-click model (see `docs/README.md`):

- **Right-click**: instantly extrude/shave up to *placement count* blocks along
  the contour starting at the cursor
  (`SpeedyToolSceptre.selectContourBlocks`, `clientside/tools/SpeedyToolSceptre.java:87-107`).
- **Left-click**: instantly undoes the last extrusion/removal.
- **CTRL (held)**: allow the contour-following to step diagonally.
- **Mouse wheel**: change placement count 1–64; scrolling to "infinite" switches
  to complex mode.

## Complex mode (placement count scrolled to infinite/∞)

Full selection + hold-to-confirm model (see `docs/README.md`). Same
non-moveable-selection / wheel-changes-count differences as the Orb's complex
mode (`SpeedyToolComplexSceptre.java:58-71`):

- **Selection is not draggable** (`selectionIsMoveable()` → `false`).
- **CTRL + mouse wheel changes the replacement block count**, not rotation
  (`mouseWheelChangesCount()` → `true`).
- Selection uses `FillMatcher.ContourFollower` instead of a flood-fill blob
  (`getFillMatcherForSelectionCreation`, `SpeedyToolComplexSceptre.java:81-90`),
  and the tool's `FillAlgorithmSettings.Propagation` is set to `CONTOUR`
  (`SpeedyToolComplexSceptre.java:38-40`), so the whole selection pipeline
  (unbounded up to 256×256×256, clippable to an Arcane Claw boundary field)
  follows the surface instead of spreading through solid volume.
- Selection auto-cancels after the action completes.
- Cursor icon while a selection exists: `CursorType.CONTOUR`.

## Sounds

- Place: `SoundEffectNames.SCEPTRE_PLACE`
- Undo: `SoundEffectNames.SCEPTRE_UNPLACE`

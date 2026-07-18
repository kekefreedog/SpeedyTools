# Staff of Duplication

- **Item class**: `common.items.ItemComplexCopy` (unlocalized name `complexcopy`)
- **Tool class**: `clientside.tools.SpeedyToolComplexCopy` extends `SpeedyToolComplex`
- **Interaction model**: "complex" only — always selection + hold-to-confirm,
  see `docs/README.md`. `ItemComplexBase`'s placement mode is `INFINITE_ONLY`
  (`common/items/ItemComplexBase.java:11`), so there's no simple/instant mode at
  all for this tool.
- **Server action**: `serverside.actions.AsynchronousActionCopy`
  (`SpeedyToolServerActions.java:168-169`)

## What it does

Selects a region (a flood-filled blob of connected non-air blocks by default,
or a box if bound by an Arcane Claw boundary field — see `docs/README.md`),
then **copies** that region's blocks/tile entities to wherever you drop the
(possibly dragged/rotated/flipped) selection. Reads the source once at
selection time; **the source is left untouched** — nothing is erased.

## Controls (shared complex-tool model, see `docs/README.md`)

1. Short right-click on a block → create the selection (up to 256×256×256).
2. With a selection: short right-click (no CTRL) toggles "grab" (drag the
   selection with you); CTRL + short right-click flips it left-right; CTRL +
   mouse wheel rotates it 90° per click.
3. Long right-click hold (≥2s) → **stamps the copy** at the selection's current
   position/orientation.
4. Long left-click hold (≥2s) → undo the last copy.
5. Short left-click → discard the selection instead of copying.

## Repeat-stamping

`cancelSelectionAfterAction()` returns `false`
(`SpeedyToolComplexCopy.java:38-41`) — **the selection is kept after a
successful copy**, not cleared. This means you can commit (long right-hold),
then drag the same selection somewhere else and commit again, stamping
multiple copies of the same captured structure without re-selecting it each
time.

## Sounds & rendering

- Cursor icon while selecting/holding: `CursorRenderInfo.CursorType.COPY`.
- Selection highlight colour: light green (`Colour.LIGHTGREEN_40`).
- Uses the shared complex-tool sound set: boundary hum, "ring" spin-up/down for
  charging an action, and the selection-generation sound while the
  server/client is computing a large selection.

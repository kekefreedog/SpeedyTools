# Staff of Relocation

- **Item class**: `common.items.ItemComplexMove` (unlocalized name `complexmove`)
- **Tool class**: `clientside.tools.SpeedyToolComplexMove` extends `SpeedyToolComplex`
- **Interaction model**: "complex" only — always selection + hold-to-confirm,
  see `docs/README.md`. `INFINITE_ONLY` placement mode, no simple mode.
- **Server action**: `serverside.actions.AsynchronousActionMove`
  (`SpeedyToolServerActions.java:173-174`)

## What it does

Selects a region (flood-filled blob of connected non-air blocks by default, or
a box if bound by an Arcane Claw boundary field — see `docs/README.md`), then
**cuts** it: reads the source, erases the source position (fills it with air),
and writes the same content at wherever you drop the (possibly
dragged/rotated/flipped) selection — an actual move, not a copy
(`AsynchronousActionMove.java:47-80`: reads, then separately reads-with-air to
erase the source, then writes at the destination).

## Controls (shared complex-tool model, see `docs/README.md`)

1. Short right-click on a block → create the selection (up to 256×256×256).
2. With a selection: short right-click (no CTRL) toggles "grab" (drag the
   selection with you); CTRL + short right-click flips it left-right; CTRL +
   mouse wheel rotates it 90° per click.
3. Long right-click hold (≥2s) → **erases the source and writes the content at
   the current position**.
4. Long left-click hold (≥2s) → undo the last move (restores the source,
   removes the moved copy).
5. Short left-click → discard the selection instead of moving it.

## Selection is one-shot

Unlike the Staff of Duplication, `cancelSelectionAfterAction()` returns `true`
(`SpeedyToolComplexMove.java:38-41`) — **the selection is cleared once the move
completes**, since the source no longer exists in its original form. You have
to make a new selection for each move.

## Sounds & rendering

- Cursor icon while selecting/holding: `CursorRenderInfo.CursorType.MOVE`.
- Selection highlight colour: light blue (`Colour.LIGHTBLUE_40`).

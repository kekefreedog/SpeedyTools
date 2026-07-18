# Staff of Destruction

- **Item class**: `common.items.ItemComplexDelete` (unlocalized name `complexdelete`)
- **Tool class**: `clientside.tools.SpeedyToolComplexDelete` extends `SpeedyToolComplex`
- **Interaction model**: "complex" only — always selection + hold-to-confirm,
  see `docs/README.md`. `INFINITE_ONLY` placement mode, no simple mode.
- **Server action**: `serverside.actions.AsynchronousActionFill`, filling with
  air (`SpeedyToolServerActions.java:170-172`) — same mechanism the Orb/Sceptre
  use to fill with a real block, just with `Blocks.air`.

## What it does

Selects a region (flood-filled blob of connected non-air blocks by default, or
a box if bound by an Arcane Claw boundary field — see `docs/README.md`), then
on commit **overwrites every block in the selection's current footprint with
air** — deleting it.

## Controls (shared complex-tool model, see `docs/README.md`)

1. Short right-click on a block → create the selection (up to 256×256×256).
2. With a selection: short right-click (no CTRL) toggles "grab" (drag the
   selection with you); CTRL + short right-click flips it left-right; CTRL +
   mouse wheel rotates it 90° per click.
3. Long right-click hold (≥2s) → **replaces every block in the selection's
   current footprint with air**.
4. Long left-click hold (≥2s) → undo the last deletion (restores the erased
   blocks).
5. Short left-click → discard the selection instead of deleting it.

## Selection is a reusable stencil

`cancelSelectionAfterAction()` returns `false`
(`SpeedyToolComplexDelete.java:38-41`) — like the Staff of Duplication, **the
selection survives a successful deletion**. Because the tool captures the
*shape* of what you selected (not just "delete this one spot"), you can drag
that same shape somewhere else after deleting and commit again, punching out
the same footprint in a new location — effectively a reusable stencil/cookie
cutter for deletion, not a one-off action.

## Sounds & rendering

- Cursor icon while selecting/holding: `CursorRenderInfo.CursorType.DELETE`.
- Selection highlight colour: light red (`Colour.LIGHTRED_40`).

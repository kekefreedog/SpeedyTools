# Wand of Non-destructive Linear Conjuration

- **Item class**: `common.items.ItemSpeedyWandWeak` (unlocalized name `simplewandweak`)
- **Tool class**: `clientside.tools.SpeedyToolWandWeak` extends `SpeedyToolSimple`
- **Interaction model**: "simple" — instant single-click actions, no selection/hold step (see `docs/README.md`)
- **Placement count mode**: `FINITE_ONLY` (`ItemSpeedyWandWeak.java:9`) — same as the strong wand, never switches to a complex/selection tool.

## What it does

The gentler sibling of the Wand of Destructive Linear Conjuration: places a
straight line of blocks starting from wherever you're looking, but **stops as
soon as it hits an existing solid block** instead of plowing through it — so it
won't destroy anything already there.

The block placed is whatever's in the hotbar slot immediately to the right of
the wand (same mechanism as the strong wand,
`clientside/tools/SpeedyToolSimple.java:101-106`).

## Controls

- **Right-click**: instantly place a line of blocks, stopping at the first solid
  obstruction.
- **Left-click**: instantly undo your last placement with this wand.
- **Mouse wheel**: change how many blocks are placed in the line, 1–64, wrapping
  around (never switches to infinite/selection mode — `FINITE_ONLY`).
- **CTRL (held)**: allow the line to step diagonally.

Same caveat as the strong wand applies to its tooltip: the wheel changes count
regardless of CTRL; CTRL only affects diagonal stepping.

## Selection mechanics

`selectBlocks()` is overridden to call
`selectLineOfBlocks(..., BlockMultiSelector.CollisionOptions.STOP_WHEN_SOLID_BLOCK_REACHED, ...)`
(`SpeedyToolWandWeak.java:47-58`) — the only functional difference from the
strong wand is this one collision option.

## Sounds

- Place: `SoundEffectNames.WEAKWAND_PLACE`
- Undo: `SoundEffectNames.WEAKWAND_UNPLACE`

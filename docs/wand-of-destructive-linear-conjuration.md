# Wand of Destructive Linear Conjuration

- **Item class**: `common.items.ItemSpeedyWandStrong` (unlocalized name `simplewandstrong`)
- **Tool class**: `clientside.tools.SpeedyToolWandStrong` extends `SpeedyToolSimple`
- **Interaction model**: "simple" — instant single-click actions, no selection/hold step (see `docs/README.md`)
- **Placement count mode**: `FINITE_ONLY` (`ItemSpeedyWandStrong.java:9`) — always a fixed count, never switches to the complex/selection tool like the Orb or Sceptre do.

## What it does

Places a straight line of blocks starting from wherever you're looking, in the
direction you're facing, **plowing straight through any solid blocks in its
path** rather than stopping at them.

The block placed is whatever's in the hotbar slot immediately to the right of
the wand (`SpeedyToolSimple.updateForThisFrame`,
`clientside/tools/SpeedyToolSimple.java:101-106`) — the wand itself is never
consumed or damaged (`setMaxDamage(-1)`, `common/items/ItemSpeedyTool.java:23`).

## Controls

- **Right-click**: instantly place a line of blocks (`SpeedyToolSimple.java:64-71`).
- **Left-click**: instantly undo your last placement with this wand
  (`SpeedyToolSimple.java:60-63`) — pulled from the per-player "simple" undo
  stack (`serverside.worldmanipulation.WorldHistory`).
- **Mouse wheel**: change how many blocks are placed in the line, 1–64
  (`ItemSpeedyTool.setPlacementCount`). Because this tool is `FINITE_ONLY`,
  scrolling past 64 wraps back around to 1 rather than switching to an
  "infinite"/selection mode.
- **CTRL (held)**: allow the line to step diagonally as it follows your look
  direction, instead of only axis-aligned steps (`controlKeyIsDown` passed into
  `selectLineOfBlocks`, `SpeedyToolSimple.java:200-212`).

The in-game tooltip (`ItemSpeedyWandStrong.addInformation`) also lists "Control +
mouse wheel: change count" — in the actual code the wheel changes count
regardless of whether CTRL is held; CTRL only affects diagonal stepping. Treat
the tooltip's wheel/CTRL pairing as stale wording, not a real requirement.

## Selection mechanics

`selectBlocks()` is overridden to call
`selectLineOfBlocks(..., BlockMultiSelector.CollisionOptions.CONTINUE_THROUGH_SOLID_BLOCKS, ...)`
(`SpeedyToolWandStrong.java:33-45`) — this is the "destructive" half of the name:
unlike the weak wand, the line selection doesn't stop when it hits a solid
block, it keeps going (destroying/replacing what's there) until it reaches the
placement count or the maximum line length.

## Sounds

- Place: `SoundEffectNames.STRONGWAND_PLACE`
- Undo: `SoundEffectNames.STRONGWAND_UNPLACE`

# Orb of Transmutation

- **Item class**: `common.items.ItemSpeedyOrb` (unlocalized name `simpleorb`)
- **Tool class**: `clientside.tools.SpeedyToolSimpleAndComplex` wrapping
  `SpeedyToolOrb` (simple) + `SpeedyToolComplexOrb` (complex) — see
  `clientside/CombinedClientProxy.java:90-111`
- **Interaction model**: **both** — starts in "simple" mode, switches to
  "complex" (selection + hold-to-confirm) when scrolled into infinite mode
- **Placement count mode**: `BOTH` (`ItemSpeedyOrb.java:9`)

This is the only tool (besides the Sceptre) that actually changes its whole
interaction model depending on the placement count. `SpeedyToolSimpleAndComplex`
checks `parentItem.isInfiniteMode(currentToolItemStack)` after every input and
swaps which inner tool is active (`clientside/tools/SpeedyToolSimpleAndComplex.java:43-66`).

## What it does

Replaces one connected "blob" of matching blocks with a different block —
flood-fills outward from the block you're looking at, selecting every
directly-connected block of **the same block type + metadata**, then replaces
the whole blob with the block from the hotbar slot immediately to the right of
the Orb.

## Simple mode (finite placement count, 1–63)

Uses `SpeedyToolSimple`'s instant right-click/left-click model (see
`docs/README.md`), with the selection algorithm overridden:

- **Right-click**: instantly flood-fills and replaces up to *placement count*
  matching, connected blocks starting at the cursor
  (`SpeedyToolOrb.selectFillBlocks`, `clientside/tools/SpeedyToolOrb.java:78-101`).
  The match is exact block+metadata (`FillMatcher.OnlySpecifiedBlock`) — it
  won't spread into a different block type even if otherwise "the same"
  visually (e.g. different wood variants).
- **Left-click**: instantly undoes the last replacement.
- **CTRL (held)**: allow the flood-fill to spread diagonally, not just
  axis-aligned.
- **Mouse wheel**: change placement count 1–64; scrolling to the "infinite"
  step switches the tool into complex mode (see below).

## Complex mode (placement count scrolled to infinite/∞)

Switches to the full `SpeedyToolComplex` selection + hold-to-confirm model (see
`docs/README.md` for the shared controls: short right-click to select, CTRL to
mirror, CTRL+wheel to rotate, long right-hold to commit, long left-hold to
undo). Differences from the Staff tools:

- **Selection is not moveable/draggable** (`selectionIsMoveable()` returns
  `false`, `SpeedyToolComplexOrb.java:59-63`) — you can't grab and reposition an
  Orb selection like you can with the Staffs.
- **CTRL + mouse wheel changes the replacement block count instead of rotating**
  (`mouseWheelChangesCount()` returns `true`, `SpeedyToolComplexOrb.java:68-71`) —
  there's nothing to rotate since the selection isn't moved.
- The selection is still flood-fill of exact block+metadata matches
  (`getFillMatcherForSelectionCreation`, `SpeedyToolComplexOrb.java:82-88`), just
  now unbounded by the small 64-block cap and able to use the full selection
  pipeline (up to 256×256×256, server-assisted for unloaded chunks, clipped to
  an active Arcane Claw boundary field if any — see `docs/README.md`).
- Selection auto-cancels after the action completes
  (`cancelSelectionAfterAction()` returns `true`).
- The cursor icon while a selection exists is the "replace" icon
  (`RenderCursorStatus.CursorRenderInfo.CursorType.REPLACE`).

## Sounds

- Place: `SoundEffectNames.ORB_PLACE`
- Undo: `SoundEffectNames.ORB_UNPLACE`

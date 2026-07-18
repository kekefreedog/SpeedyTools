# Arcane Claw of Boundary Creation

- **Item class**: `common.items.ItemSpeedyBoundary` (unlocalized name `complexboundary`)
- **Tool class**: `clientside.tools.SpeedyToolBoundary` extends `SpeedyToolComplexBase`
  (not a full `SpeedyToolSimple`/`SpeedyToolComplex` — it has its own,
  simpler input handling)
- **Placement count mode**: N/A — max stack size 1, no placement count concept.

## What it does

Doesn't move blocks itself. Instead it defines an optional axis-aligned
bounding box ("boundary field") that **constrains the selection algorithms of
every other complex tool** (the Staffs, and the Orb/Sceptre in their complex
mode) — see the "Selection shape" note in `docs/README.md` and
`SpeedyToolComplex.java:449-456` / `615-628`, which check
`speedyToolBoundary.getBoundaryCorners()` whenever building a selection.

Maximum box size in any dimension is 256 blocks
(`SELECTION_MAX_XSIZE`/`Y`/`Z`SIZE in `clientside/tools/SpeedyToolComplexBase.java:113-115`,
tied to `common.selections.VoxelSelection.MAX_X_SIZE` etc.) — this is also the
hard ceiling for any voxel selection in the mod.

## Controls (`SpeedyToolBoundary.processUserInput`, `clientside/tools/SpeedyToolBoundary.java:96-116`)

1. **First right-click**: places the first corner marker at the block under
   your cursor.
2. **Second right-click**: places the second corner marker, completing the box.
   If the two points would make a box bigger than 256 in any axis, the first
   corner is automatically clamped/moved inward rather than rejecting the click
   (`addCornerPointWithMaxSize`, `SpeedyToolBoundary.java:406-414`).
3. **Once both corners are placed, right-click again toggles "grab"**:
   - First press: grabs whichever face of the box you're looking at
     (`boundaryGrabActivated = true`), remembers your eye position as the grab
     point.
   - Second press: drops it — the grabbed face is dragged by however far
     you've moved since grabbing (along a single axis, whichever face you
     grabbed), then the box corners are recomputed and the grab released
     (`doRightClick`, `SpeedyToolBoundary.java:120-158`). The box won't be
     dragged smaller than 1 block, and won't exceed the 256-block cap
     (`getGrabDraggedBoundaryField`, `SpeedyToolBoundary.java:326-367`).
4. **Left-click** (at any point): clears both corners and removes the boundary
   field entirely (`SpeedyToolBoundary.java:99-104`).

There's no hold-to-confirm step and no undo/redo history for the boundary field
itself — placing/moving/clearing it is all instant, and it isn't part of the
world-backup/undo system (it's client-side bookkeeping only, never sent to the
server as a world edit).

## Hotbar icon feedback

The item's displayed model changes to reflect state
(`SpeedyToolBoundary.updateForThisFrame`, `SpeedyToolBoundary.java:172-193`, via
`ItemSpeedyBoundary.IconNames`): `NONE_PLACED` → `ONE_PLACED` → `TWO_PLACED`, or
`GRABBING` while a face is actively being dragged.

## Sounds

Distinct sounds for each step: `BOUNDARY_PLACE_1ST`, `BOUNDARY_PLACE_2ND`,
`BOUNDARY_GRAB`, `BOUNDARY_UNGRAB`, `BOUNDARY_UNPLACE` (clearing), plus a
continuous ambient hum (`SoundEffectBoundaryHum`) whose volume/pan is driven by
your distance to the nearest face of the field while the tool is equipped.

# Structure Template tool

Added in SpeedyTools 4.3.0; World/Computer libraries added in 4.4.0.
Structure Capture, the creative catalogue and miniature thumbnails added in 4.5.0.
Duplication-style controls added in 4.6.0. Version 4.6.1 restores simple colour
miniatures and introduces a refined 3D Structure Capture wand.
For Minecraft 1.8 / Forge 11.14.3.1450.
Install the same SpeedyTools version on the server and every client. Older clients
are rejected at the version handshake. IMSM is not required.

## Controls

Obtain **Structure Template** from the **Build Faster** creative tab, or use
`/give <player> speedytoolsmod:structuretemplate` as an operator.

Hold the tool and press **B**, right-click air, or sneak-right-click a block to
open the library. B/R/V/C can be rebound in Minecraft's Controls menu.

1. Choose **Select corners**, then right-click two opposite blocks. Alternatively,
   select a boundary with the existing Arcane Claw and choose **Use boundary**.
2. The minimum corner is the default anchor. **Set anchor** lets you right-click
   another block inside the selection (for example, an entrance doorstep).
3. Enter a name in the right-hand field and choose **Save new**. New templates
   are private. A repeated name creates a distinct template with its own ID.
4. Select an existing library entry and choose **Overwrite** to replace that
   specific template with the current selection. Review its name in the
   confirmation dialog. Cancel leaves the existing template untouched.
5. Select a template and choose **Equip**. Right-click a block face to align the
   saved anchor with the adjacent block. A blue voxel ghost and gold anchor
   appear. **R** rotates 90 degrees; **V** mirrors; **C** cancels the preview.
6. Short right-click toggles grabbing the preview. While grabbed, walk or fly to
   move it by the same distance, like the Staff of Duplication; short right-click
   again fixes its position. Hold right-click for **two seconds**, then release to
   build. The duplication tool's cursor animation shows the charge and server build
   progress. Releasing an incomplete charge does not build. The server checks the
   preview revision and target before changing blocks. Each placement consumes its
   preview ticket; load/review the next preview to stamp another copy.
7. **Undo build** reverses your latest complex SpeedyTools operation in this
   dimension, including operations performed with the other staffs. It uses the
   existing per-player undo history, which is not persisted across restarts.

**Left-click** or **C** cancels the selection/preview and an unsubmitted charge.
It does not delete the saved template or remove already-built blocks; use library
Delete or Undo build for those operations. Changing tools or opening a menu cancels
a charge. A hold started before the preview is ready cannot place it.

Mirroring defaults to **V**, leaving **M** available for VoxelMap. On first use in
this release, an existing template-mirror binding on M migrates to V; other custom
bindings are preserved. Keys remain rebindable in Controls.

**Air: ignore** preserves destination blocks wherever the template contains air.
**Air: replace** includes air cells and clears those destination blocks. The
preview's bounding outline changes colour for replacement mode.

The inventory-style grid has 24 slots per page. Enter a search term in the
left-hand field and press Enter. Filters are **Mine**, **Shared**, **All**, and
**Trash**. Hover a slot for the template name, X/Y/Z size, anchor, owner, and
revision. Item tooltips show storage, size and anchor; hold Shift for owner,
revision and controls. Long titles are shortened and tooltip lines wrap to fit. Hover the
status line to read a message that does not fit on one line.

## Capture tool and creative inventory

**Structure Capture** is a separate tool in the **Build Faster** creative tab
(`/give <player> speedytoolsmod:structurecapture`). Its custom green-and-cyan
selection staff icon matches the existing magical tools. Right-click the first corner,
then the opposite corner. The first click marks a corner; the second draws the
boundary and automatically opens the save menu. Choose World or Computer, enter
a name, and Save new or Overwrite. Set anchor is available before saving. C clears
the selection; the next block click starts a new boundary. B or sneak-right-click
opens the library without restarting capture. Equipping a saved template from the
capture tool changes the held item into a Structure Template blueprint.

The Build Faster tab also lists accessible **World** and **Computer** templates
as named blueprint items after the ordinary tools. Take a blueprint into your
hotbar to use it. World blueprints use the existing preview/confirmation workflow;
a Computer blueprint's first block click requests import and equips the new World
copy. Server import and placement permissions still apply. The tab fetches server
entries in small pages and refreshes while the creative inventory is open; large
libraries can take a few seconds to arrive. Refreshes may reset the tab's scroll.

Blueprints use automatically generated, cached **8x8x8 voxel miniatures** in the
creative inventory, library and hotbar. They preserve the structure's proportions
and use block map colours. They are lightweight approximations rather than textured
screenshots; stairs, thin details and custom block colours may be simplified.
Air-only templates and unavailable blocks use the paper fallback. Existing templates
get thumbnails lazily when browsed, and model caches rebuild after resource reloads.
World/Computer source labels remain in blueprint names and tooltips. Inventory
thumbnails have no letter badges or pastel backgrounds.

The capture wand has a slim dark 3D grip, lime bands, a cyan rectangular selection
frame and a small floating core. Inventory, first-person, third-person, dropped-item
and item-frame poses are configured separately. Its generated icon remains the
inventory/hotbar icon and particle texture. Held and world views use the cuboid
model. Its grip and frame parts meet without overlapping faces.

## World and Computer libraries

Use the **World** and **Computer** buttons to switch the inventory grid's source.
**Save new**, **Overwrite**, **Rename**, **Delete**, **Restore**, **Previous** and
**Purge** act on the selected library.

- **World** stores templates in the current server/world. Use **Share / Private**
  to let other players on that server browse, copy and build your template.
  **Copy to Computer** downloads the selected accessible template as a new copy.
- **Computer** stores templates in `<Minecraft game directory>/speedytools-templates/`.
  They remain available when you switch singleplayer worlds or multiplayer servers
  using that same Minecraft installation. Separate launcher instances with different
  game directories have separate libraries. The filters here are **All** and **Trash**.
  Saving a selection here asks the server to capture it, then downloads it directly;
  it does not leave an extra template in World.
- **Copy to World** uploads a computer template as a new private template owned by
  the importing player. **Import + Equip** also equips that new world copy for the
  normal server preview and placement workflow. Use **Share / Private** in World
  to share the imported copy. These actions create independent copies, not synced
  references: editing or deleting one does not change the other.

Imports follow the destination server's block availability, size limits, ownership,
quota and placement rules. Multiplayer imports require operator permission by
default; a server owner can change `operatorOnlyImports` for trusted builders.
Computer imports can also be disabled entirely. Singleplayer imports follow the
usual creative/operator building restriction. Uploading never places blocks by
itself, and templates containing command blocks still require operator permission.

Transfers preserve block metadata, anchor and tile NBT (including inventories).
They use ordered 16 KiB chunks, an 8 MiB compressed limit, a 32 MiB NBT read budget,
and a two-minute timeout. Keep the tool held and stay in the same world while a
transfer completes. Failed/incomplete transfers do not replace a saved template.
The Computer library allows 2048 entries including Trash.

## Managing templates

- **Rename** changes the selected template's name using the right-hand field.
- **Share / Private** controls whether other players can browse and place it.
  Sharing does not give them edit or deletion rights.
- **Delete** asks for confirmation and moves the template into Trash. Structures
  already built in the world remain unchanged. Items referring to the deleted
  ID become unavailable when held.
- **Restore** in Trash reactivates that ID, including previously issued items.
- **Previous** restores the immediately preceding revision under the same ID.
  There is one recovery revision, updated by every edit, not unlimited history.
- **Purge** in Trash permanently removes the template and its recovery revision,
  after confirmation. This frees a quota slot. Trash otherwise counts toward quotas.

Overwrites keep the ID and increment the revision. Held blueprint metadata
refreshes from the server; future placements resolve the latest revision.
An edit invalidates existing previews. Stale edits are rejected rather than
overwriting someone else's newer work. Owners and operators manage templates;
other users can only view/equip shared entries.

## Server configuration and storage

`config/speedytools-templates.cfg` contains:

| Option | Default | Meaning |
| --- | --- | --- |
| `enabled` | `true` | Enable template operations |
| `creativeOrOperatorOnly` | `true` | Require creative mode or operator status for mutations/building/undo |
| `maxBlocks` | `65536` | Selection volume including air; configurable up to 262144 |
| `maxPerPlayer` | `128` | Templates per owner, including Trash |
| `maxTotal` | `2048` | Templates in this server world, including Trash |
| `allowComputerImports` | `true` | Allow uploads from Computer libraries |
| `operatorOnlyImports` | `true` | Restrict multiplayer imports to operators; imported tile data can contain commands and inventories |
| `captureDistance` | `256` | Maximum distance from player to each capture corner |

Each axis is limited to **254 blocks**, reserving the existing engine's one-block
undo border on both sides. Builds must fit within world height and the world
border. The destination plus the undo border must be loaded and editable.
Placement targets must be within 64 blocks of the player. Builds do not consume
survival materials, including when the operator disables the creative restriction.
Command-block templates require operator permission.

Files live in `<world>/speedytools-templates/`, shared across that world's
dimensions. Each UUID has an NBT file and, after editing, a `.previous` recovery
file. Temporary writes are validated and atomically moved into place. Template
names are never used as filesystem paths. Storage uses block registry names so
numeric IDs can change after restart. Missing blocks cause an explicit failure.
Deleting the mod or moving to a different world does not automatically migrate
the library; back up this directory with the world.

The server captures blocks, enforces ownership, handles storage, and applies
changes. Browsing and previews receive summaries and bounded chunks without
tile-entity NBT. Explicit Computer exports also transfer the full saved tile data. Capture, permission checks, placement, and undo advance through server ticks;
disk serialization and palette conversion are bounded by the configured limits.
One template world operation runs at a time and excludes other SpeedyTools world
operations while active. Once world writing has begun, it completes even if the
requesting player disconnects. Pending preflight/capture is discarded if the player
leaves the world. The server finishes started writes before shutdown saves.

## Compatibility boundaries

- Blocks, metadata and tile entities (including inventories and sign text) are
  saved. Mobs, item frames, paintings and scheduled block ticks are not included
  in the persisted template format.
- The ghost shows occupied voxels, not textured block models. Partial blocks such
  as stairs/fences appear as cubes. Placement uses the actual saved blocks.
- Straight, curved and ascending rails follow template rotation and mirroring,
  including powered, detector and activator rails.
- Rotation uses SpeedyTools' existing block rotation helper. Custom mod blocks or
  tile entities with private orientation data may need specific adapters.
- Vanilla edit permissions, spawn protection and world bounds are checked.
  `TemplatePlacementEvent` is posted on `MinecraftForge.EVENT_BUS` before placement
  and before undo, including the undo border. Protection mods can cancel the event.
  Direct writes do not emit individual vanilla place/break events; arbitrary
  third-party claim protection requires an integration with this event.
- IMSM import/export is deferred; this release captures structures already in the
  world and has no dependency on the IMSM JAR.

## Validation

Use the project's documented JDK 8 Docker environment:

```sh
docker compose run --rm dev -lc './gradlew templateTest build'
```

`templateTest` is an explicit regression task, separate from the existing disabled
legacy test task. It covers rotated/mirrored anchors (including mixed odd/even
dimensions and distant coordinates), size bounds, storage/restart round trips,
stale revision rejection, recovery, deletion/restoration/purge, ownership
immutability, invalid anchors, and malformed packet lengths/protocols. Registry
tests also cover block metadata, independent tile NBT, air placement masks, and
missing block rejection, using Forge's launch classloader for vanilla bootstrap.
Transfer tests cover multi-packet round trips, size/decompression limits, bad
offsets, replays, incomplete streams, and dropping nonportable permission fields.

Before installing on a production server, run a dedicated Forge 1.8 server with
two matching clients against a disposable world and verify:

1. Capture a small asymmetric house with a stair, sign and filled chest. Save,
   restart, equip, rotate/mirror around an off-centre anchor, build, and undo.
2. Verify air-ignore and air-replace with blocks inside the destination footprint.
3. Test private visibility, shared placement, owner-only edits, and operator edits.
   Try the management operations from a non-operator survival player.
4. Open the same revision on both clients, overwrite on one, and verify the other's
   stale overwrite and stale placement fail without changing files or world blocks.
5. Delete/restore with a blueprint already held by the other player; then purge
   the trashed template and confirm the item stays unavailable after restart.
6. Verify dimension changes, server reconnects, unloaded chunks, world-height
   limits, spawn protection, and a cancelled `TemplatePlacementEvent`.
7. Start a larger build, disconnect the initiating player, and verify completion
   and correct server shutdown/restart behaviour. Check dedicated-server logs for
   client-class-loading errors and confirm old mod versions cannot connect.

8. Save directly to Computer, restart the client, join another world/server, and
   use Import + Equip. Verify dimensions, anchor, chest contents, preview and undo.
9. Export a shared World template to Computer; overwrite/delete either copy and
   confirm the other stays unchanged. Exercise Computer recovery and Trash.
10. Test disabled imports, non-operator imports, missing mod blocks, server quotas,
    and disconnecting or putting away the tool during both transfer directions.

11. In the Build Faster creative tab, use Structure Capture to select two corners
    and save to each library. Confirm the catalogue updates, thumbnails appear,
    dragging blueprints works, and an imported Computer blueprint builds normally.
    Test tab scrolling, resource reload, joining another server, and private/deleted
    entries becoming inaccessible without client restart.

12. Grab/release a preview while walking and flying, then charge for two seconds
    and release. Check the duplication cursor and server completion feedback.
    Verify left-click cancellation, incomplete holds, changing tools while charging,
    late preview downloads, and cancelling without breaking the targeted block.
13. Check simple colour miniatures in the creative tab, library and hotbar.
    Inspect the capture wand in first/third person, dropped and in an item frame.
    Confirm V mirrors while M remains available to
    VoxelMap, including when upgrading an existing options file.

Compilation, automated tests, and the two-client smoke test must pass before
treating a newly built JAR as release-ready.

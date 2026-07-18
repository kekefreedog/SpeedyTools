# SpeedyTools ("Build Faster Mod") — Reference

Forge Minecraft mod, cloned from `TheGreyGhost/SpeedyTools` (GitHub),
originally built against vanilla Minecraft 1.8 and migrated (branch
`forge-1.8.9-migration`) to compile against real Minecraft **1.8.9**
(`Forge 1.8.9-11.15.1.2318-1.8.9`) after a player crash on 1.8.9 revealed the mod
wasn't actually binary-compatible with it — see "Forge/Minecraft version" below.
Mod id `speedytoolsmod`, display name "Build Faster Mod", version `4.1.1` (see
`SpeedyToolsMod.java`). `mcmod.info`'s `version`/`mcversion` fields use
`${version}`/`${mcversion}` Gradle template placeholders, expanded by
`build.gradle`'s `processResources` block (`filesMatching('mcmod.info') { expand
'version':project.version, 'mcversion':mcversionDeclared }`) into the real values
at build time — confirmed by inspecting the packaged jar's `mcmod.info` after a
build. (Earlier revisions of this file had the placeholders replaced with
literal stale values `2.1.0`/`1.7.10`, which silently broke the expand —
Gradle's `expand()` only substitutes `${...}` tokens, so a literal string
doesn't get touched. If `mcmod.info` in a built jar ever shows a stale version
again, check that the placeholders in `src/main/resources/mcmod.info` weren't
reverted.) Note `mcmod.info`'s `mcversion` is purely cosmetic display text in
the Mods list — it has nothing to do with whether FML actually lets the mod
load; see the `acceptedMinecraftVersions` bullet below for the real gate.

To bump the version, use `scripts/bump-version.sh <new-version>` (e.g.
`scripts/bump-version.sh 4.1.1`) rather than editing files by hand — it updates
`build.gradle` (`version = "X.Y.Z"`) and `SpeedyToolsMod.java` (both the `@Mod`
annotation's `version` attribute and the `VERSION` constant) together, and
deliberately leaves `mcmod.info` alone since its placeholders are
version-agnostic (see above). Only bump the version when there's a new
git pull/release to ship — not on every intermediate build while iterating.

Gives players in-game tools to select/copy/move/delete/fill large voxel regions
with undo support and a server-side backup system, without needing WorldEdit.

## Dev environment / build workflow

Docker-based, JDK 8 (mandatory — this era of Forge/ForgeGradle won't run on newer
JDKs). Files: `Dockerfile`, `docker-compose.yml`.

```
docker compose build
docker compose run --rm dev -lc "./gradlew setupDevWorkspace"   # one-time / after build.gradle changes
docker compose run --rm dev -lc "./gradlew build"                 # -> build/libs/speedytoolsmod-4.1.1.jar
```

`docker-compose.yml` mounts the project root at `/workspace` and keeps a named
`gradle-cache` volume at `/root/.gradle` so Gradle/Forge/MCP downloads persist
across container runs. Build output lands in `build/libs/` on the **host**
filesystem too (bind mount), not just inside the container.

### Forge/Minecraft/ForgeGradle version (migrated to real 1.8.9)

- **Forge is `1.8.9-11.15.1.2318-1.8.9`, built via `ForgeGradle:2.1-SNAPSHOT`**
  (`net.minecraftforge.gradle.forge` plugin id — the short `'forge'` alias no
  longer resolves under this FG version). This replaced the original
  `1.8-11.14.3.1450` / `ForgeGradle:1.2-SNAPSHOT` pin. Reason: a player running
  real Minecraft 1.8.9 got `NoSuchMethodError:
  net.minecraft.client.renderer.WorldRenderer.func_178970_b()V` the moment a
  tool was equipped — **Mojang genuinely replaced `WorldRenderer`'s public API**
  between vanilla 1.8 and 1.8.9 (old immediate-mode style: `startDrawingQuads()`
  / `startDrawing(mode)` / `addVertex(x,y,z)` / `addVertexWithUV(x,y,z,u,v)` →
  new builder style: `begin(mode, vertexFormat)` /
  `pos(x,y,z).tex(u,v).endVertex()` / `finishDrawing()`; confirmed by
  disassembling both the 1.8 and 1.8.9 `WorldRenderer.class` directly). This
  wasn't a mapping-string problem — the wrong-but-differently-wrong mapping and
  the correct 1.8.9-specific mapping produced byte-identical compile errors,
  which is what proved the API itself changed, not just its name. `Tessellator`
  itself is unaffected (`getWorldRenderer()`/`draw()` kept their signatures).
  Files touched to port the calls: `clientside/rendering/RendererHotbarCurrentItem.java`,
  `clientside/rendering/RenderCursorStatus.java`,
  `clientside/rendering/SelectionBoxRenderer.java`,
  `clientside/selections/BlockVoxelMultiSelectorRenderer.java`,
  `clientside/selections/SelectionBlockTextures.java`. If you add new
  low-level GL rendering code, use the `begin`/`pos`/`tex`/`color`/`endVertex`
  API, not the old one (won't even compile against the old API anymore).
- A few unrelated SRG identifiers used directly in source (not MCP names) also
  drifted between 1.8 and 1.8.9 and needed updating in
  `serverside/worldmanipulation/WorldFragment.java`:
  `EntityHanging.func_174857_n()` → `getHangingPosition()`,
  `EntityHanging.field_174860_b` → `facingDirection`,
  `WorldServer.func_180497_b(...)` → `scheduleBlockUpdate(...)`. Also
  `CommandBroadcast.execute(MinecraftServer, String[])` →
  `processCommand(ICommandSender, String[])` (`MinecraftServer` implements
  `ICommandSender`) in `serverside/backup/MinecraftSaveFolderBackups.java`, and
  a dead `net.minecraft.command.IEntitySelector` import (class removed,
  replaced by `net.minecraft.util.EntitySelectors`) in
  `serverside/ingametester/WorldSelectionUndoTest.java`.
- **MCP mappings: `stable_22`** (`build.gradle` → `minecraft.mappings`) — this
  is `de.oceanlabs.mcp:mcp_stable` version `22-1.8.9`, i.e. purpose-built for
  1.8.9 (confirmed via `mcp_stable`'s `maven-metadata.xml`, not guessed from a
  forum post — community reports about which stable/snapshot mapping works for
  1.8.9 are unreliable/contradictory, verify empirically if this ever needs
  changing again).
- `minecraft { }` block: `assetDir` doesn't exist on FG 2.x's extension anymore
  — use `runDir` instead (where `runClient`/`runServer` would execute; this
  project doesn't run those in-container, see below).
- **`afterEvaluate` Mojang-URL-override block was removed** (it existed under
  FG 1.2 to redirect `downloadClient`/`downloadServer`/`getVersionJson`/
  `getAssetsIndex` away from Forge's dead `s3.amazonaws.com` URLs, using a
  `net.minecraftforge.gradle.delayed.DelayedString` class that doesn't exist
  in FG 2.1 — package was restructured). Confirmed FG 2.1 already points at
  live Mojang endpoints on its own; no replacement override needed.
- **Gradle wrapper bumped to Gradle 2.12** (from 2.0) — FG 2.1-SNAPSHOT
  requires at least Gradle 2.3 and refused to evaluate `build.gradle` at all
  under 2.0 (`"ForgeGradle 2.0 requires Gradle 2.3 or above"`). No Docker image
  change needed (still JDK 8).
- **`processResources` uses `filesMatching('mcmod.info') { expand ... }`, not
  two separate `from()` blocks with include/exclude.** The old two-`from()`
  pattern (one `include 'mcmod.info'` + expand, one `exclude 'mcmod.info'` for
  everything else) silently stopped honoring the exclude under FG 2.1/Gradle
  2.12, so `mcmod.info` got packaged twice (one expanded, one raw) — harmless
  for the plain `jar` task, but FG's `reobfJar` task is stricter and failed
  outright with `ZipException: duplicate entry: mcmod.info`. `filesMatching`
  copies from a single `from()` source, so there's nothing to duplicate.
- **Individual sound-asset downloads from `resources.download.minecraft.net`
  return HTTP 400** during `setupDevWorkspace`'s `getAssets` task. This is
  non-fatal — `getAssets` reports errors per-asset but does not fail the build,
  and it's irrelevant to compiling. It only matters if you try to actually run
  the client in-container (`runClient`) and want proper block/sound assets.
  Not yet fixed — flag if this becomes a blocker. (Still present post-migration;
  unrelated to the Forge/FG version.)
- **No LiteLoader anywhere in this project.** It's Forge-only; don't add a
  LiteLoader dependency/plugin unless explicitly asked for new work.
- **`mcmod.info`'s `mcversion` is cosmetic display text only — it does NOT
  control FML's actual load-time compatibility gate.** That gate is driven
  entirely by `@Mod(acceptedMinecraftVersions=...)` in `SpeedyToolsMod.java`
  (verified by disassembling Forge's `FMLModContainer.bindMetadata`: if that
  annotation attribute is blank, FML falls back to the exact Minecraft version
  the Forge build itself targets, formatted as an exact-match-only range —
  e.g. `[1.8,1.8]` for the original `1.8-11.14.3.1450` pin — regardless of
  whatever `mcmod.info` says). Currently set to `acceptedMinecraftVersions=
  "[1.8,1.8.9]"`, matching `build.gradle`'s `mcversionDeclared`. Keep the two in
  sync if this ever changes.

## Source architecture

Root package `speedytools`. Split into `clientside`, `serverside`, `common`
(shared by both sides), mirroring Forge's client/server proxy pattern.

### Entry point / wiring

- `SpeedyToolsMod` — `@Mod` class, forwards FML lifecycle events (`preInit`/
  `load`/`postInit`) to a `CommonProxy`, chosen via `@SidedProxy`:
  `clientside.CombinedClientProxy` (client) or `serverside.DedicatedServerProxy`
  (dedicated server).
- `common.CommonProxy` — shared setup: registers items/blocks
  (`RegistryForItems`, `RegistryForBlocks`), calls `ServerSide.load()`,
  registers `ServerEventHandler`/`ServerTickHandler`/`PlayerTrackerRegistry`
  with the Forge/FML event buses. Declares abstracts for
  `getOrCreateSaveBackupsFolder()` and `enqueueMessageOnCorrectThread()` that
  the client/server proxies implement differently (thread-safety: incoming
  network messages must be dispatched onto the correct main thread).
- `clientside.ClientSide` — static holder wiring up all client-side singletons
  in `preInitialise`/`load`/`postInitialise`: `ActiveTool`, packet
  registry/sender (`PacketHandlerRegistryClient`, `PacketSenderClient`),
  `CloneToolsNetworkClient`, `SpeedyToolRenderers`, `SoundController`,
  `UndoManagerClient` (one for simple tools, one for complex tools),
  `SelectionPacketSender`, `ClientVoxelSelection`, `UserInput`. This is the
  client-side composition root — start here to trace how objects are wired.
- `serverside.ServerSide` — server-side equivalent composition root (backups,
  `WorldHistory`, `ServerVoxelSelections`, `SpeedyToolServerActions`,
  `SpeedyToolsNetworkServer`, `PlayerTrackerRegistry`).

### Items (`common.items`) — the 9 tools, registered in `RegistryForItems`

For precise, source-verified per-tool mechanics (controls, exact selection
algorithms, what "cancel selection after action" does per tool, etc.), see
`docs/` — one file per tool, plus `docs/README.md` for the shared
simple-vs-complex interaction model. Note the Sceptre and Orb are `simple`
*and* `complex`: they run as instant single-click tools until scrolled into
infinite placement mode, at which point they switch to the same
selection-plus-hold-to-confirm model as the three Staff tools (see
`clientside.tools.SpeedyToolSimpleAndComplex`).

Display names from `assets/speedytoolsmod/lang/en_US.lang`:

| Class                                             | In-game name                               | Kind                                                                      |
| ------------------------------------------------- | ------------------------------------------ | ------------------------------------------------------------------------- |
| `ItemSpeedyWandStrong`                            | Wand of Destructive Linear Conjuration     | simple                                                                    |
| `ItemSpeedyWandWeak`                              | Wand of Non-destructive Linear Conjuration | simple                                                                    |
| `ItemSpeedySceptre`                               | Enchanted Sceptre of Contour Extrusion     | simple                                                                    |
| `ItemSpeedyOrb`                                   | Orb of Transmutation                       | simple                                                                    |
| `ItemSpeedyBoundary`                              | Arcane Claw of Boundary Creation           | defines a bounding box other tools operate within                         |
| `ItemComplexCopy`                                 | Staff of Duplication                       | complex (copy/paste)                                                      |
| `ItemComplexMove`                                 | Staff of Relocation                        | complex (cut/move)                                                        |
| `ItemComplexDelete`                               | Staff of Destruction                       | complex (delete)                                                          |
| `ItemSpeedyTester` (`ItemComplexOrb` also exists) | In-game tester                             | debug/testing only, gated by `SpeedyToolsOptions.getTesterToolsEnabled()` |

All extend `ItemSpeedyTool` (placement-count/damage-as-stack-size logic: item
damage value encodes finite vs. infinite placement mode) → ultimately
`ItemComplexBase`/`ItemSpeedyTool` base classes in `common.items`.

`common.blocks` has small support blocks: `BlockSelectionFog`/
`BlockSelectionSolidFog` (client-side "fog" rendering for selection previews),
`BlockSimple`, registered via `RegistryForBlocks`.

### Client-side tool state machine (`clientside.tools`)

- `ActiveTool` — holds the currently-equipped `SpeedyTool` (looked up by
  `Item` in a registry), forwards per-frame/per-tick/input events to it, and
  handles activate/deactivate transitions when the held item changes.
- `SpeedyTool` (abstract base) → `SpeedyToolSimple` / `SpeedyToolComplexBase` →
  `SpeedyToolComplex` (abstract, ~1270 lines — the meaty one) →
  `SpeedyToolComplexCopy` / `SpeedyToolComplexMove` / `SpeedyToolComplexDelete`
  / `SpeedyToolComplexOrb` / `SpeedyToolComplexSceptre` (concrete per-tool
  behaviour via small overridden hooks: `getCursorType()`,
  `getSelectionRenderColour()`, `cancelSelectionAfterAction()`,
  `selectionIsMoveable()`, `mouseWheelChangesCount()`,
  `getBlockSelectionBehaviour()`).
- `SpeedyToolComplex.processUserInput` implements the full click-and-hold
  interaction model documented in its own header comment: short right-click =
  create selection; right-click (no CTRL) toggles grab/drag; CTRL+right-click
  = mirror; CTRL+wheel = rotate; long right-hold = commit action (place); long
  left-hold = undo; short left-click = discard current selection. A
  `PowerUpEffect` (in `clientside.userinput`) drives the "charging up" visual/
  audio buildup for hold-to-confirm actions.
- `SpeedyToolComplex.ToolSelectionStates`/`ToolState` enums track
  NO_SELECTION → GENERATING → READY_FOR_DISPLAY, and IDLE →
  PERFORMING_ACTION/PERFORMING_UNDO_FROM_* → ACTION_SUCCEEDED/FAILED etc.
  Drives which renderers/sounds are active at any moment.
- `SpeedyToolBoundary` — manages the optional bounding box (from the Arcane
  Claw item) that constrains fill/selection algorithms for other tools.
- `CommonSelectionState` — shared mutable state (selection origin/orientation,
  grab point, "has been moved" flag) referenced by the complex tools while a
  selection is being dragged/rotated/flipped (`QuadOrientation` in
  `common.utilities` encodes rotation + flip).

### Selection generation (`clientside.selections`, `common.selections`)

- `common.selections.VoxelSelection` — a `BitSet`-backed 3D voxel mask, max
  256×256×256 (`MAX_X_SIZE`/`Y`/`Z_SIZE`). `VoxelSelectionWithOrigin` adds a
  world-space origin.
- `common.selections.BlockVoxelMultiSelector` — turns a world region into a
  `VoxelSelection` via `selectAllInBoxStart` (bounding box) or flood-fill /
  contour algorithms (`FillMatcher`, `FillAlgorithmSettings`); designed to run
  incrementally (`continueSelectionGeneration(timeout)`) so it doesn't block a
  tick, and tracks "unavailable voxels" (unloaded chunks — see Network protocol
  below).
- `clientside.selections.BlockMultiSelector` — client-side wrapper invoked by
  `SpeedyToolComplex.updateForThisFrame` for the live wireframe highlight
  preview (before a selection is committed).
- `clientside.selections.ClientVoxelSelection` — the client-side selection
  lifecycle owner: kicks off generation, tracks GENERATING/READY_FOR_DISPLAY
  state, and — if the local `BlockVoxelMultiSelector` reports unavailable
  voxels (unloaded chunks) — asks the server to complete the generation
  instead via `Packet250ServerSelectionGeneration`, then receives the result
  back through a `MultipartOneAtATimeReceiver`.

### Network protocol (`common.network`, `common.network.multipart`)

Uses Forge's `SimpleImpl` messaging (`IMessage`/`IMessageHandler`) wrapped in
this mod's own `Packet250Base` + `PacketHandlerRegistry`/`PacketSender`
abstraction (separate impls for client `PacketHandlerRegistryClient`/
`PacketSenderClient` and server `PacketHandlerRegistryServer`/
`PacketSenderServer`).

Key packet types (`common.network`):
- `Packet250CloneToolUse` — client→server: "selection made" (prepare/backup),
  "perform tool action" (place, with tool ID/position/`QuadOrientation`), and
  undo requests.
- `Packet250CloneToolStatus` / `ClientStatus` / `ServerStatus` — client and
  server continuously exchange status (`IDLE`, `MONITORING_STATUS`,
  `WAITING_FOR_ACTION_COMPLETE`, `PERFORMING_BACKUP`,
  `PERFORMING_YOUR_ACTION`, ...); `CloneToolsNetworkClient` polls/derives
  `ActionStatus` (`NONE_PENDING`/`WAITING_FOR_ACKNOWLEDGEMENT`/`PROCESSING`/
  `COMPLETED`/`REJECTED`) from this exchange.
- `Packet250CloneToolAcknowledge` — server ack/reject of a requested action.
- `Packet250ServerSelectionGeneration` — the "generate this selection on the
  server instead" protocol described in `notes/SelectionGeneration.txt`
  (commands: FILL / ALL_IN_BOX / ABORT / STATUS_REQUEST; server replies with
  STATUS). Full 5-step protocol: (1) client sends command + uniqueID, (2)
  server replies STATUS + uniqueID, (3) client polls with STATUS_REQUEST, (4)
  on completion server streams the selection back via
  `MultipartOneAtATimeSender`, (5) client updates render (unknown/unloaded
  areas shown as "fuzzy"/foggy blocks — see `BlockSelectionFog` block).
  Server-side driver: `serverside.ServerVoxelSelections`.

Multipart layer (`common.network.multipart`) exists because a full voxel
selection (up to 256³) doesn't fit in one `Packet250CustomPayload`:
- `MultipartPacket` — one large logical payload split into segments, with
  BitSet-tracked ack state; tolerant of dropped/duplicate/out-of-order
  segments and lost connections.
- `MultipartOneAtATimeSender` / `MultipartOneAtATimeReceiver` — only one
  in-flight multipart transfer per packet-type at a time; superseding a
  transfer aborts the previous one. Driven by periodic `onTick()` calls.
  `SelectionPacket` is the concrete `MultipartPacket` used for selection data.

### Server-side world manipulation & undo (`serverside.worldmanipulation`, `serverside.actions`)

- `WorldFragment` — captures block ID/metadata/TileEntity NBT/Entity data for
  an arbitrary voxel region; can `readFromWorld()` and `writeToWorld()`,
  synchronously or asynchronously (returns an `AsynchronousToken`, see below).
  This is the actual "copy" data structure.
- `WorldSelectionUndo` — records one clone-tool change (the pre-change state of
  everything touched) so it can be reversed; explicitly documents that
  out-of-order undo isn't always a perfect inverse (e.g. a torch that broke
  because its supporting block was replaced by a later action) — see its class
  doc for the worked example.
- `WorldHistory` — per-player, per-`WorldServer` (i.e. per-dimension) undo
  stacks: at least one "complex" undo slot (grows if space allows, discards
  oldest first when full) plus a fixed number of "simple" undos (wand/orb,
  instant placement). Cleans up stale `EntityPlayerMP`/`WorldServer`
  (`WeakReference`s).
- `AsynchronousToken` (interface) / `AsynchronousActionBase` — generic
  cooperative-multitasking pattern: long-running server operations are driven
  incrementally by repeated `continueProcessing()` calls (bounded by
  `setTimeOfInterrupt`/`isTimeToInterrupt`) rather than blocking the server
  thread. Concrete actions in `serverside.actions`:
  `AsynchronousActionCopy`, `AsynchronousActionMove`, `AsynchronousActionFill`
  (orb/sceptre — fills selection with one block), `AsynchronousActionUndo`.
  Each is a small state machine (`ActionStage` enum: SETUP → ... → COMPLETE),
  progressed by `SpeedyToolServerActions`/`ServerTickHandler`.
- `BlockDataStore` (interface) + `BlockDataStoreArray`/`BlockDataStoreSparse` —
  two storage strategies for per-voxel block ID/metadata/light data (dense
  array vs. sparse map), used depending on selection size/density.
- `WorldServerReader`/`WorldServerReaderFill` — read-side helpers for
  streaming world data into a `WorldFragment`.
- `SpeedyToolServerActions` — the actual command handler invoked when packets
  arrive: `prepareForToolAction` (triggers a world backup, throttled — see
  below), `performSimpleAction` (immediate wand/orb place or undo), and
  `performComplexAction` (kicks off the appropriate `AsynchronousAction*` and
  registers it with `WorldHistory`).

### World backup system (`serverside.backup`)

- `MinecraftSaveFolderBackups` — before any clone-tool action, copies the
  entire save folder to a timestamped backup (throttled: no more than once per
  5 minutes, see class doc `MINIMUM_TIME_BETWEEN_BACKUPS_MS`); keeps up to 6
  backups with increasing spacing as they age (oldest kept is 14–21 backups
  old) — an exponential/log-scale retention scheme. This is the safety net
  against the mod corrupting a world. See `notes/BackupSeries.xlsx` (binary
  spreadsheet, not machine-read — appears to model/plan this retention
  spacing scheme; open in Excel/LibreOffice if you need the exact numbers).
- `FolderBackup` — low-level recursive folder copy (contains adapted
  Oracle sample code per its header).
- `StoredBackups` — persists the backup-folder listing (NBT-based
  `backuplisting` file) so backups survive server restarts.

### Rendering, sound, input (client-only, lower priority to dig into further)

- `clientside.rendering` — one `RendererElement` per visual layer, composed
  per active tool by `SpeedyToolRenderers`: wireframe highlight
  (`RendererWireframeSelection`), boundary field box
  (`RendererBoundaryField`), the committed solid selection
  (`RendererSolidSelection`, uses `SelectionBoxRenderer`/
  `BlockVoxelMultiSelectorRenderer`), cursor status/spin animation
  (`RenderCursorStatus`), hotbar item overlay, status message overlay,
  crosshair overrides. Each has a `*RenderInfoUpdateLink` inner-class pattern
  in the owning tool (e.g. `SpeedyToolComplex.SolidSelectionRendererLink`)
  that pulls current state into a plain data struct the renderer reads —
  decouples renderer classes from tool internals.
- `clientside.sound` — `SoundController` + per-effect classes
  (`SoundEffectBoundaryHum`, `SoundEffectComplexTool` "ring" spin-up/down,
  `SoundEffectComplexSelectionGeneration`, `SoundEffectSimple`), same
  update-link pattern as rendering.
- `clientside.userinput` — `UserInput` turns raw mouse/keyboard into
  higher-level `InputEvent`s (click up/down with duration, wheel move, ctrl
  modifier) consumed by `SpeedyTool.processUserInput`; `PowerUpEffect` models
  the hold-to-charge timing for place/undo gestures; `KeyBindingInterceptor`
  suppresses vanilla key handling while a speedy tool is active.

### Testing/debug infra (`serverside.ingametester`)

`InGameTester`, `InGameStatusSimulator`, `SelectionFillTester`,
`WorldSelectionUndoTest` — in-world manual test harnesses gated behind
`ItemSpeedyTester`/`SpeedyToolsOptions.getTesterToolsEnabled()`, used by the
original author to exercise the undo/fill/network-status-simulation logic
in a live game rather than pure unit tests (the actual `src/test` JUnit tree
is minimal/excluded — `build.gradle` has `test { exclude '*' }`).

## Known TODOs / design notes

- `notes/notes.txt` (short, verbatim): reduce lag after large placements
  (lighting recalculation suspected), "infinite replace/place" for orb and
  sceptre, an options-buttons idea the author decided against.
- `notes/SelectionGeneration.txt`: design rationale for the client/server
  selection-generation split described above (why generation can't always
  happen purely client-side once chunks aren't loaded).
- `notes/OrderOfPlacementAndUndo.xls` — binary spreadsheet, not machine-read;
  by filename, models the ordering semantics for placement vs. undo
  (presumably backing the "out-of-order undo isn't always a perfect inverse"
  behaviour documented in `WorldSelectionUndo`'s class comment). Open in
  Excel/LibreOffice if the exact ordering rules matter for a change.
- `notes/BackupSeries.xlsx` — binary spreadsheet, not machine-read; by
  filename, models the backup retention/spacing series used by
  `MinecraftSaveFolderBackups`.
- `common.utilities.BlockRotateFlipHelperOLD` exists alongside
  `BlockRotateFlipHelper` — looks like a superseded implementation kept
  around; check call sites before assuming either is dead code.

# SpeedyTools ("Build Faster Mod") — Reference

Forge 1.8 Minecraft mod, cloned from `TheGreyGhost/SpeedyTools` (GitHub). Mod id
`speedytoolsmod`, display name "Build Faster Mod", version `4.2.0` (see
`SpeedyToolsMod.java`). `mcmod.info`'s `version`/`mcversion` fields use
`${version}`/`${mcversion}` Gradle template placeholders, expanded by
`build.gradle`'s `processResources` block (`expand 'version':project.version,
'mcversion':project.minecraft.version`) into the real values at build time —
confirmed by inspecting the packaged jar's `mcmod.info` after a build. (Earlier
revisions of this file had the placeholders replaced with literal stale values
`2.1.0`/`1.7.10`, which silently broke the expand — Gradle's `expand()` only
substitutes `${...}` tokens, so a literal string doesn't get touched. If
`mcmod.info` in a built jar ever shows a stale version again, check that the
placeholders in `src/main/resources/mcmod.info` weren't reverted.)

To bump the version, use `scripts/bump-version.sh <new-version>` (e.g.
`scripts/bump-version.sh 4.2.0`) rather than editing files by hand — it updates
`build.gradle` (`version = "X.Y.Z"`) and `SpeedyToolsMod.java` (both the `@Mod`
annotation's `version` attribute and the `VERSION` constant) together, and
deliberately leaves `mcmod.info` alone since its placeholders are
version-agnostic (see above).

Gives players in-game tools to select/copy/move/delete/fill large voxel regions
with undo support and a server-side backup system, without needing WorldEdit.

## Dev environment / build workflow

Docker-based, JDK 8 (mandatory — this era of Forge/ForgeGradle won't run on newer
JDKs). Files: `Dockerfile`, `docker-compose.yml`.

```
docker compose build
docker compose run --rm dev -lc "./gradlew setupDevWorkspace"   # one-time / after build.gradle changes
docker compose run --rm dev -lc "./gradlew build"                 # -> build/libs/speedytoolsmod-4.2.0.jar
```

`docker-compose.yml` mounts the project root at `/workspace` and keeps a named
`gradle-cache` volume at `/root/.gradle` so Gradle/Forge/MCP downloads persist
across container runs. Build output lands in `build/libs/` on the **host**
filesystem too (bind mount), not just inside the container.

### Gotchas already solved in `build.gradle` — don't re-break these

- **Forge version is pinned to `1.8-11.14.3.1450`** (bumped once from the
  original checkout's `11.14.1.1334`). This repo's `ForgeGradle:1.2-SNAPSHOT`
  hard-rejects any Forge build `>= 11.14.3.1503` for MC 1.8 (`ForgeGradle 1.2
  only supports Forge 1.8 before 11.14.3.1503` — this is a real, verified
  ceiling, not a guess). Do not bump Forge past that without also upgrading
  ForgeGradle (which would be a much bigger migration — see below).
- **`afterEvaluate` block overrides `downloadClient` / `downloadServer` /
  `getVersionJson` / `getAssetsIndex` task URLs** to current Mojang endpoints
  (`launcher.mojang.com`, `piston-meta.mojang.com`, `launchermeta.mojang.com`).
  ForgeGradle 1.2 hardcodes dead `s3.amazonaws.com/Minecraft.Download/...` URLs
  (Mojang retired that CDN years ago); without this override,
  `setupDevWorkspace`/`build` fail immediately with `FileNotFoundException`.
  The override works because `DownloadTask.setUrl(DelayedString)` is public and
  `DelayedString` just needs `(Project, String)` — both classes are already on
  the buildscript classpath via the `forge` plugin, so no extra import/dependency
  is needed in `build.gradle`.
- **Individual sound-asset downloads from `resources.download.minecraft.net`
  return HTTP 400** during `setupDevWorkspace`'s `getAssets` task. This is
  non-fatal — `getAssets` reports errors per-asset but does not fail the build,
  and it's irrelevant to compiling. It only matters if you try to actually run
  the client in-container (`runClient`) and want proper block/sound assets.
  Not yet fixed — flag if this becomes a blocker.
- **No LiteLoader anywhere in this project.** It's Forge-only; don't add a
  LiteLoader dependency/plugin unless explicitly asked for new work.
- **Gradle wrapper is pinned to Gradle 2.0** (very old — no `--console=plain`
  support, etc.). Stick to plain `./gradlew <task>` invocations.
- MCP mappings: `snapshot_nodoc_20141130` (`build.gradle` → `minecraft.mappings`).
- Upgrading past ForgeGradle 1.2 (e.g. to reach newer Forge builds, or fix the
  asset-download issue for real) would mean migrating the whole `build.gradle`
  to ForgeGradle 2.x+ conventions (different task names/DSL) — treat as a
  separate, deliberate migration, not a quick version bump.

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
  array vs. sparse map), used depending on selection size/density. Both preserve
  non-negative 32-bit block IDs independently of metadata and lighting.
- `WorldServerReader`/`WorldServerReaderFill` — read-side helpers for
  streaming world data into a `WorldFragment`.
- `SpeedyToolServerActions` — the actual command handler invoked when packets
  arrive: `prepareForToolAction` (triggers a world backup, throttled — see
  below), `performSimpleAction` (immediate wand/orb place or undo), and
  `performComplexAction` (kicks off the appropriate `AsynchronousAction*` and
  registers it with `WorldHistory`).

### World backup system (`serverside.backup`)

Automatic backups default to disabled (`backup.autoWorldBackupEnabled=false`);
existing saved preferences are preserved. Enable them in the mod Config screen.

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

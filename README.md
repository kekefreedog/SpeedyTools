SpeedyTools
===========

A set of custom items for Minecraft(Forge) to help you build faster.

Structure templates (4.6.4)
-------------------------

Structure Capture selects a boundary and opens Save after two clicks. The Build
Faster creative tab displays saved blueprints with simple colour miniatures. The
capture wand has a custom 3D selection-frame model. Short right-click grabs/releases a preview; hold then release to
build with the duplication charge animation. Left-click cancels; V mirrors.
The Structure Template tool adds World and Computer inventory-style libraries with
capture, anchors, rotation/mirroring, save/overwrite, sharing, recoverable deletion,
and construction undo. Copy templates between the server world and your computer
to reuse them in other worlds or servers, subject to server import permissions.
See [controls, multiplayer setup and validation](docs/structure-templates.md).

Build and run the template regression suite with JDK 8:

```
docker compose run --rm dev -lc './gradlew templateTest build'
```

Bumping the mod version
------------------------

To bump the mod version (updates `build.gradle` and `SpeedyToolsMod.java`):

```
scripts/bump-version.sh <new-version>
# e.g. scripts/bump-version.sh 4.1.0
```


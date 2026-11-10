# Valo Minimap (client-side Forge mod, Minecraft 1.20.1)

Goes ONLY in the players' own `mods` folder. NEVER on the server (Aternos works fine without it).

## Get the .jar (no software to install)
1. Create a free account on github.com, then **New repository** (name: valomap, Public).
2. In the empty repository click **uploading an existing file**, drag ALL the files of this folder
   (build.gradle, settings.gradle, gradle.properties, the `src` folder) and click **Commit changes**.
3. Hidden folders are often skipped by the upload: click **Add file > Create new file**, type the name
   `.github/workflows/build.yml` (typing the slashes creates the folders), paste the content of
   `WORKFLOW_TO_PASTE.txt`, click **Commit changes**.
4. Open the **Actions** tab. A "Build mod" run starts (about 5-10 min). When it has a green check, click it,
   scroll to **Artifacts**, download `valomap-mod`. The zip contains `valomap-1.0.0.jar`.
5. Put that jar in `.minecraft/mods` of every player (Forge 1.20.1). Nothing to do on the server.
If the run is red, click it and send the error log.

## Use
- The minimap appears (top left) only while a Valorant match is running and you are in a team.
- `K` = show / hide, `L` = change zoom. Keys can be changed in Options > Controls > Valo Minimap.
- Teammates: green squares (you: white). Enemies: red squares, only while a living teammate (or you) sees them
  (line of sight, in front of them, less than 60 blocks). When you are dead, the map follows the player you spectate.

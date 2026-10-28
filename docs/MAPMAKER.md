# Building a Clash map in game

The ClashMapmaker plugin turns a plain Paper server into a map editor for Clash. You build the
world as usual, then tell the plugin where everything is with `/cmap` commands instead of typing
coordinates into `map.yml`. It checks the map the same way Clash does and packs it into a zip
you can hand in.

## Install

1. Run a **Paper 26.2** server on **Java 25**, the versions Clash runs. Items saved on a newer
   Minecraft version do not load on an older one, so match it.
2. Download `clash-mapmaker-<version>.jar` from the
   [releases page](https://github.com/hcfrevival/Clash-Mapmaker/releases) into `plugins/` and
   restart.
3. Op yourself, or give yourself the `clash.mapmaker` permission.

Type `/cmap` for the list of commands. Every step tab-completes.

## How it works

A Clash map is a world plus `map.yml`. The plugin keeps one **session** per player: the map you
are editing and the world it lives in. Everything you add is outlined with particles so you can
see the map as Clash will: regions white, team bases in their team's color, goals orange, mines
green, subclaim chests lime, spawns as a short column in the team's color (pink for spectators),
shopkeepers aqua, your wand selection yellow.

Maps are saved under `plugins/ClashMapmaker/maps/<slug>/map.yml` and exported to
`plugins/ClashMapmaker/exports/<slug>-<version>.zip`. A session is kept while the server runs,
through relogs, until you `/cmap close` it; after a restart, `/cmap open <slug>` picks the map
up again.

## A Bunkers map, step by step

1. **A world.** `/cmap world ravine` makes an empty void world called `ravine` and puts you on a
   stone block (`/cmap world ravine flat` for a flat one). Build the map there, or paste it with
   WorldEdit. After a restart, `/cmap world ravine` loads it again. You can also use any world
   you already have.
2. **Start the map.** `/cmap new bunkers-ravine bunkers`. The slug (`bunkers-ravine`) is the
   folder Clash will know the map by: lowercase letters, digits and dashes. Spectators spawn where
   you are standing; move that later with `/cmap spawn add spectator`.
3. **The wand.** `/cmap wand` gives you a stick. Left-click a block for corner A, right-click
   for corner B; the selection shows in yellow. Every region below is made from the selection.
4. **Teams.** `/cmap team add red red 10` and `/cmap team add blue blue 10`: id, color (a name
   or `#rrggbb`) and the most players. Change anything later with
   `/cmap team edit red name <red>Red Dragons</red>` (keys: `name`, `display-name`, `color`,
   `min`, `max`, `overfill`, `role`, `base`).
5. **Bases.** Select a team's base and run `/cmap bunkers base red`. That makes the region
   `red-base` and gives it to the team.
6. **Spawns.** Stand where a player should appear, facing the way they should look, and run
   `/cmap spawn add red`. Repeat for a few points per team. `/cmap spawn clear red` starts over.
7. **The hill.** Select the hill and run `/cmap region add koth-hill deny-build`, then
   `/cmap goal add control-point koth koth-hill 10m` (the time is how long one team must hold it).
8. **Bounds.** Select the whole playable map, `/cmap region add map-bounds`, then
   `/cmap rules set bounds map-bounds`. Players who leave it die.
9. **Shopkeepers.** Stand where the villager goes, looking the way it should look, and run
   `/cmap bunkers shop red gear armorer`, `/cmap bunkers shop red sell cleric` and
   `/cmap bunkers shop red enchanter librarian`. The first word is the team (`none` for a neutral
   shop), then the shop, then an optional villager job and look (`desert`, `snow`, ...).
10. **Mines.** Select the ore area and run `/cmap bunkers mine red-mine 90s`: blocks broken
    there come back after that time.
11. **Subclaim chests.** Look at a chest inside a base and run `/cmap bunkers subclaim`, or
    `/cmap bunkers subclaim 400` for a chest with its own price. Set the usual price with
    `/cmap bunkers rules subclaim-price 250`.
12. **The starter kit.** Put the kit in your own inventory, armor on, then run
    `/cmap kit set starter` and `/cmap rules set respawn-kit starter`. Names, enchantments and
    potions are kept exactly.
13. **Rules.** `/cmap bunkers rules grace 10m`, `/cmap bunkers rules dtr 5.0`,
    `/cmap rules set time-limit 60m`, `/cmap name Ravine`, `/cmap objective Hold the KOTH or be
    the last team standing.` Everything you leave out uses Clash's defaults; see
    [MAP_FORMAT.md](MAP_FORMAT.md) for what each setting means.
14. **Check, save, export.** `/cmap validate` lists every problem with the map and the world,
    `/cmap save` writes `map.yml`, `/cmap export` saves and packs the zip. `/cmap info` sums
    the map up at any time; `/cmap tp red` or `/cmap tp koth` takes you to a spawn, goal or
    region; `/cmap preview` turns the outlines off and on.

## The other modes

Teams, spawns, regions, kits and rules work the same; only the goals change.

- **Destroy the Core**: `/cmap new dtc-forts dtc`. Build each core as a casing of obsidian with
  lava inside, select the casing and run `/cmap region add red-core-region`, then
  `/cmap goal add core red-core red red-core-region obsidian 5` (owner, region, casing block,
  how far the lava must leak). Validation checks that the lava is really inside.
- **Capture the Wool**: `/cmap new ctw-loom ctw`. Select the room with the wool chests and run
  `/cmap region add red-cyan-room keep-out:red` so the owning team cannot enter its own room.
  Look at the block where the wool must be placed (leave it empty) and run
  `/cmap goal add wool red-cyan red cyan red-cyan-room`.
- **Base Defense**: `/cmap new bd-vault bd`. Give the teams roles with
  `/cmap team edit raiders role attacker` and `/cmap team edit defenders role defender`, select
  the vault walls, `/cmap region add vault-walls`, then
  `/cmap goal add destroyable vault defenders vault-walls obsidian,netherite_block 60` (the
  blocks that count and the percent the raiders must break).

## What `/cmap validate` checks

The same rules Clash applies when it loads the map: every id points at something real, every
team has a spawn, the mode has the goals it needs, and, because the world is open, that regions
fit inside the world, spawns are inside the bounds, core casings hold lava, wool rooms hold a
container, the wool monument is empty, destroyable regions hold their blocks, and a container
stands at every subclaim chest. Errors stop `/cmap export`; warnings do not.

## The zip

`<slug>-<version>.zip` holds `<slug>/map.yml` and the world's chunks (`region/`, and
`entities/` and `poi/` when the world has them). Player data, stats and server settings stay
on your server. Unzipped into Clash's maps folder it is ready to play.

## Handing it in

Send the zip to the HCF Revival staff. They play it on a test server and add it to the map
repository; a changed map comes back as a new zip with a higher `/cmap version`.

## Every command

| Command | Does |
| --- | --- |
| `/cmap wand` | The selection stick. |
| `/cmap world <slug> [void\|flat]` | Make or load a world and go there. |
| `/cmap new <slug> <bunkers\|dtc\|ctw\|bd>` | Start a map in the world you are in. |
| `/cmap open <slug>` | Edit a saved map in the world you are in. |
| `/cmap close [confirm]` | Stop editing (`confirm` drops unsaved changes). |
| `/cmap info` | What the map holds and how many problems it has. |
| `/cmap name <text>`, `/cmap version <x.y.z>`, `/cmap objective <text>` | Names players see; the version names the zip. |
| `/cmap team add <id> <color> <max> [name]`, `edit <id> <key> <value>`, `remove <id>` | Teams. |
| `/cmap spawn add <team\|spectator>`, `clear <team>` | Spawn points where you stand. |
| `/cmap region add <id> [deny-build] [kill-outside] [keep-out:<team,team>]`, `remove <id>` | Regions from the selection. |
| `/cmap goal add control-point <id> <region> [capture-time]` | KOTH. |
| `/cmap goal add core <id> <owner> <region> [block] [leak]` | A core. |
| `/cmap goal add wool <id> <owner> <color> [room]` | A wool; the monument is the block you look at. |
| `/cmap goal add destroyable <id> <owner> <region> <block,block> [percent]` | A vault. |
| `/cmap goal remove <id>` | |
| `/cmap kit set <name>`, `remove <name>` | Kits from your inventory. |
| `/cmap classes allow <class ...>`, `cap <class> <n>` | Which classes, and how many per team. |
| `/cmap bunkers base <team>` | The team's base from the selection. |
| `/cmap bunkers shop <team\|none> <shop> [job] [look]` | A shopkeeper where you stand. |
| `/cmap bunkers mine <id> [respawn]` | A mine from the selection. |
| `/cmap bunkers subclaim [price]` | The chest you look at. |
| `/cmap bunkers rules <key> <value>` | `grace`, `dtr`, `outside-bases`, `catalog`, `subclaim-price`, `starting-balance`, `kill-reward`. |
| `/cmap rules set <key> <value>` | `max-players`, `late-join`, `time`, `weather`, `bounds`, `time-limit`, `time-limit-result`, `overtime`, `respawn-delay`, `respawn-kit`, `gamerule.<name>`, `cooldown.<item>`. |
| `/cmap preview` | Outlines off and on. |
| `/cmap tp <spectator\|team\|goal\|region>` | Go there. |
| `/cmap validate` | Every problem, with the world checked too. |
| `/cmap save` | Write `map.yml`. |
| `/cmap export` | Save and pack the zip. |

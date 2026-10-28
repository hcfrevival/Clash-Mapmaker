# Changelog

Changes to the `map.yml` format and the Clash Mapmaker tools. The format version
(`proto`) only changes when old maps would be read differently, and every change to it is
listed here.

## 1.0.0

- The ClashMapmaker plugin (`clash-mapmaker-<version>.jar` on the release) builds a map in game
  on a plain Paper 26.2 server: a wand with particle outlines, `/cmap` commands for teams,
  spawns, regions, goals, kits, classes and the Bunkers fixtures, `/cmap validate` with the
  world-dependent checks Clash runs, `/cmap save` and `/cmap export` to a zip ready to hand in.
  See [docs/MAPMAKER.md](docs/MAPMAKER.md).
- The `clash-map` library is unchanged apart from its version; `proto` stays 1.

## 0.4.0

- Regions take an optional `keep-out` list of team ids that may not enter or build there,
  so a Capture the Wool map closes each wool room to its own team as PGM maps do. Older
  maps read the same; `proto` stays 1.

## 0.3.0

- New optional top-level `cooldowns` section (`enderpearl`, `crapple`, `gapple`, `totem`,
  `wind-charge`): how long each item stays unusable after use on this map. Leave a key out
  for the server's default; `0s` turns that cooldown off. Variants can replace it. Older
  maps read the same; `proto` stays 1.

## 0.2.0

- `bunkers.shops.npcs` entries take an optional `profession` (the villager's job: `armorer`,
  `cleric`, ... or `none`) and `biome` (`plains`, `desert`, ...) so a map picks how each
  shopkeeper looks. Older maps read the same; `proto` stays 1.

## 0.1.0

First release of the `clash-map` library.

- `map.yml` format `proto: 1` for Bunkers, Destroy the Core, Capture the Wool and Base
  Defense. See [docs/MAP_FORMAT.md](docs/MAP_FORMAT.md).
- Reading with clear messages for every mistake, including misspelled keys.
- Saving in a stable, readable order.
- Checks that every id points at something real, every team can spawn, and each mode has
  the goals it needs.
- One example map per mode in `examples/`.

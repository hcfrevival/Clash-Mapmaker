# map.yml reference

This page lists every field a Clash `map.yml` can have. If you are new, start from one of
the [examples](../examples/) and come back here when you want to know what a field does.

**How to read this page**

- **Required** fields must be there or the map will not load.
- Everything else is optional. Leave it out and Clash uses its normal default.
- Fields marked **MM** accept [MiniMessage](https://docs.advntr.dev/minimessage/format.html)
  colors and formatting, such as `"<gold>KOTH</gold>"`. Put these values in quotes.
- **Ids** are short lowercase names (letters, numbers, `-` and `_`) that other parts of
  the file use to point at something. For example a team's `base: red-base` points at
  the region with `id: red-base`.
- **Times** are written as `90s`, `5m`, `1h` or combined like `1h30m`. A plain number
  means seconds.
- **Positions** are `{ x: 0.5, y: 64, z: 0.5 }`. Add `yaw` (which way you face, -180 to
  180) and `pitch` (looking up or down) when it matters, for example for spawns. Press F3
  in game to see your own position and facing.
- **Block positions** are whole numbers: `{ x: 10, y: 64, z: -3 }`.
- **Materials** are Minecraft ids such as `diamond_sword` or `obsidian`.

If something is misspelled, Clash warns you about the unknown key and ignores it.

Contents: [The basics](#the-basics) ·
[world](#world) · [teams](#teams) · [spawns](#spawns) · [kits](#kits) ·
[kit-chests](#kit-chests) · [classes](#classes) · [regions](#regions) · [goals](#goals) ·
[respawn](#respawn) · [bunkers](#bunkers) · [time-limit](#time-limit) ·
[victory](#victory) · [variants](#variants)

## The basics

```yaml
proto: 1
name: Ravine
slug: bunkers-ravine
version: 1.0.0
objective: "Hold the <gold>KOTH</gold> for 5 minutes or be the last team standing."
authors:
  - { name: YourName, contribution: Build }
gamemode: bunkers
max-players: 40
join: { late: true }
```

| Field | | What it is |
| --- | --- | --- |
| `proto` | required | The file format version. Always `1` for now. |
| `name` | required, MM | The map name players see. |
| `slug` | required | A lowercase id for the map, such as `bunkers-ravine`. Use the same name for the map's folder. |
| `version` | required | Your version of the map, such as `1.0.0`. Raise it every time you change the map. |
| `objective` | MM | One sentence telling players how to win. |
| `authors` | | Who made the map. Each entry has a `name`, and can have a `contribution` (such as `Build` or `Layout`) and a `uuid` (Minecraft account id). |
| `gamemode` | required | `bunkers`, `dtc` (Destroy the Core), `ctw` (Capture the Wool) or `bd` (Base Defense). |
| `max-players` | | The most players across all teams. |
| `join.late` | | `true` lets players join after the match started, `false` does not. Each mode has its own default: Bunkers closes joining when the grace period ends, the others allow it. |

## world

```yaml
world:
  spawn: { x: 0.5, y: 90, z: 0.5 }
  bounds: map-bounds
  time: 6000
  weather: clear
  gamerules: { doDaylightCycle: false, doMobSpawning: false }
```

| Field | | What it is |
| --- | --- | --- |
| `spawn` | required | Where spectators appear. Pick a spot with a good view. |
| `bounds` | | The id of a region that covers the playable area. Give that region the `kill-outside` flag so players who leave it die. |
| `time` | | Time of day in ticks (`0` is sunrise, `6000` noon, `18000` midnight). |
| `weather` | | `clear`, `rain` or `thunder`. |
| `gamerules` | | Minecraft gamerules, by their normal names. |

## teams

```yaml
teams:
  - { id: red, name: Red, color: red, max: 10, overfill: 2, base: red-base }
  - { id: blue, name: Blue, color: "#3a7bff", max: 10, overfill: 2, base: blue-base }
```

You decide how many teams there are (at least two) and how big they are.

| Field | | What it is |
| --- | --- | --- |
| `id` | required | The team's id, such as `red`. |
| `name` | required, MM | The team name players see. |
| `display-name` | MM | A longer name for titles, such as `Red Team`. |
| `color` | required | A color name or a hex color such as `"#ff5555"` (in quotes). Names: `black`, `dark_blue`, `dark_green`, `dark_aqua`, `dark_red`, `dark_purple`, `gold`, `gray`, `dark_gray`, `blue`, `green`, `aqua`, `red`, `light_purple`, `yellow`, `white`. |
| `max` | required | The most players on this team. |
| `min` | | Players this team needs before the match can start. |
| `overfill` | | Extra seats above `max` that only premium players and staff can take. |
| `role` | | `attacker` or `defender`. Base Defense maps need one of each. |
| `base` | | The id of this team's base region. Every Bunkers team needs one. |

## spawns

```yaml
spawns:
  - team: red
    points:
      - { x: -200.5, y: 70, z: 0.5, yaw: -90 }
      - { x: -200.5, y: 70, z: 4.5, yaw: -90 }
```

Every team needs at least one spawn point. With several points, players are spread
across them. Use `.5` on x and z to stand in the middle of a block.

## kits

Kits are the items players get. They are written into the map itself.

```yaml
kits:
  starter:
    armor:
      helmet: iron_helmet
      chestplate: { material: iron_chestplate, enchantments: { protection: 1 } }
      leggings: iron_leggings
      boots: iron_boots
    items:
      - { slot: 0, material: iron_sword, enchantments: { sharpness: 1 } }
      - { slot: 1, material: bow }
      - { slot: 8, material: cooked_beef, amount: 16 }
    offhand: shield
```

Each kit has a name you choose (`starter` above) and can have:

- `armor`: `helmet`, `chestplate`, `leggings` and `boots`.
- `items`: the rest of the inventory. Slots 0 to 8 are the hotbar, 9 to 35 the main
  inventory. Items without a `slot` go into the first free one.
- `offhand`: one item for the off hand.

An item can be just a material (`iron_helmet`) or a section with these fields:

| Field | | What it is |
| --- | --- | --- |
| `material` | required | The item, such as `diamond_sword`. |
| `amount` | | How many, 1 to 99. Default 1. |
| `slot` | | Inventory slot, 0 to 35. |
| `name` | MM | A custom item name. |
| `lore` | MM | Lines of text under the name, as a list. |
| `enchantments` | | Enchantments and their levels, such as `{ sharpness: 2, unbreaking: 3 }`. |
| `unbreakable` | | `true` to never break. |
| `color` | | Dye color for leather armor, a color name or hex color. |
| `potion` | | Potion type for potions and tipped arrows, such as `healing`, `strong_healing` or `swiftness`. |
| `data` | | An exact copy of an item saved by the Mapmaker plugin. You never need to write this by hand. |

Which kit players get when they spawn is set in [respawn](#respawn).

## kit-chests

Fill chests and other containers with items when the match starts.

```yaml
kit-chests:
  - region: mid-supplies
    items:
      - { material: golden_apple, amount: 2 }
      - { material: splash_potion, potion: healing }
```

Every container inside the `region` gets the `items`.

## classes

Which armor classes players can use on this map, and how many per team.

```yaml
classes:
  allowed: [archer, bard, rogue, guardian]
  caps: { archer: 2, bard: 1, rogue: 2, guardian: 1 }
```

| Field | What it is |
| --- | --- |
| `allowed` | The classes players may use: `archer`, `bard`, `rogue`, `guardian`, `miner`, `diver`. Leave the section out to allow none. |
| `caps` | The most players on one team that can use a class at the same time. |

## regions

A region is a box between two corner blocks. Both corners are part of the box and you
can give them in any order.

```yaml
regions:
  - { id: koth-hill, cuboid: { a: { x: -6, y: 68, z: -6 }, b: { x: 6, y: 74, z: 6 } }, flags: [deny-build] }
  - { id: map-bounds, cuboid: { a: { x: -300, y: 0, z: -300 }, b: { x: 300, y: 255, z: 300 } }, flags: [kill-outside] }
```

| Field | | What it is |
| --- | --- | --- |
| `id` | required | The region's id. |
| `cuboid` | required | The two corners, `a` and `b`, as block positions. |
| `flags` | | Extra rules for this region. |

Flags:

| Flag | What it does |
| --- | --- |
| `deny-build` | Nobody can place or break blocks here. |
| `kill-outside` | Players outside this region die. Use it on the region you set as `world.bounds`. |

Regions do nothing on their own. Other parts of the map point at them: team bases, goals,
mines and kit chests.

## goals

Goals are what teams fight over. Each mode uses one kind:

| Mode | Goal type |
| --- | --- |
| Bunkers | exactly one `control-point` |
| Destroy the Core | one `core` per team |
| Capture the Wool | `wool`, usually two per team |
| Base Defense | one or more `destroyable` owned by the defenders |

Every goal has a `type`, an `id` and a `name` (MM).

**Who owns a goal:** for cores and destroyables the `owner` is the team defending it.
For wool the `owner` is the team that has to place it.

### control-point

A hill. A team captures it by being the only team standing in it for `capture-time`. If
it is empty or two teams are inside, the timer goes back to zero.

```yaml
goals:
  - type: control-point
    id: koth
    name: KOTH
    region: koth-hill
    capture-time: 5m
```

| Field | | What it is |
| --- | --- | --- |
| `region` | required | The hill. |
| `capture-time` | | How long a team must hold it. Default `5m`. |

### core

A block casing with lava inside. Enemies break in, and when the lava flows `leak` blocks
below the core, it has leaked and the enemy team completes it.

```yaml
goals:
  - type: core
    id: red-core
    name: Red Core
    owner: red
    material: obsidian
    region: red-core-casing
    leak: 5
    mode-changes:
      - { after: 20m, material: gold_block }
```

| Field | | What it is |
| --- | --- | --- |
| `owner` | required | The team defending this core. |
| `region` | required | A box around the core's casing. Put the lava inside it. |
| `material` | | What the casing is made of. Default `obsidian`. |
| `leak` | | How far below the core the lava must reach. Default `5`. |
| `mode-changes` | | Later in the match, turn the casing into an easier block, `after` that much time. |

### wool

A wool block the owner must find and place on their monument.

```yaml
goals:
  - { type: wool, id: red-cyan, name: Cyan Wool, owner: red, color: cyan, monument: { x: -105, y: 66, z: -2 }, room: red-cyan-room }
```

| Field | | What it is |
| --- | --- | --- |
| `owner` | required | The team that has to place this wool. |
| `color` | required | A dye color: `white`, `orange`, `magenta`, `light_blue`, `yellow`, `lime`, `pink`, `gray`, `light_gray`, `cyan`, `purple`, `blue`, `brown`, `green`, `red`, `black`. |
| `monument` | required | The block position where the wool is placed. |
| `room` | | The region with the chests that hold this wool. They refill. |
| `craftable` | | `true` lets players craft this wool. Default `false`. |

### destroyable

Blocks the owner defends. The other team completes it by breaking enough of them.

```yaml
goals:
  - type: destroyable
    id: vault
    name: Vault
    owner: defenders
    region: vault-walls
    materials: [obsidian, netherite_block]
    completion: 60%
    repairable: false
    show-progress: true
```

| Field | | What it is |
| --- | --- | --- |
| `owner` | required | The team defending these blocks. |
| `region` | required | Where the blocks are. |
| `materials` | required | Which blocks count. |
| `completion` | | How much has to be broken. Default `60%`. |
| `repairable` | | `true` lets defenders place the blocks back. Default `false`. |
| `show-progress` | | `true` shows the percent broken to everyone. |

## respawn

What happens when a player dies.

```yaml
respawn:
  delay: 5s
  kit: starter
  teams:
    attackers: { kit: raider, lives: 3 }
    defenders: { kit: defender, delay: 10s }
```

| Field | What it is |
| --- | --- |
| `kit` | The kit players get every time they spawn. |
| `delay` | How long players wait before they come back. |
| `lives` | How many times each player can respawn. Leave it out for no limit. |
| `enabled` | `false` means players do not respawn at all. |
| `teams` | Different rules for single teams, by team id. Anything a team does not set comes from the rules above. |

In Bunkers, a team stops respawning when its DTR runs out (see below), whatever this
section says.

## bunkers

Only Bunkers maps use this section. Every value has a default, so you only need to write
what you want different.

```yaml
bunkers:
  grace: 10m
  dtr: 5.0
  build:
    outside-bases: [cobweb, water, lava]
  shops:
    catalog: default
    npcs:
      - { team: red, shop: gear, profession: armorer, at: { x: -210.5, y: 70, z: 10.5, yaw: 180 } }
      - { team: red, shop: sell, profession: cleric, biome: desert, at: { x: -212.5, y: 70, z: 10.5, yaw: 180 } }
  mines:
    - { region: red-mine, respawn: 90s }
  subclaims:
    price: 250
    chests:
      - { x: -212, y: 70, z: -3 }
      - { x: -212, y: 70, z: 3, price: 400 }
  economy: { starting-balance: 0, kill-reward: 50 }
```

How a Bunkers match plays:

- At the start there is a **grace period**. Fighting, building, shops and mines are open,
  only the KOTH is closed, and new players can still join. When grace ends the
  KOTH opens and nobody new can join.
- Each team has a **DTR** number. Every time a team member dies, it goes down by 1. It
  never goes back up. When it reaches 0 the team's base is open to raiders and dead
  players stop respawning.
- Players build freely in **their own base**. Outside bases only the blocks in
  `build.outside-bases` can be placed.
- Players earn money from kills and by selling at **shops**, and spend it on gear and on
  **subclaims**: chests in the base that only the buyer can open.
- The match ends when a team captures the KOTH or only one team is left alive.

| Field | What it is |
| --- | --- |
| `grace` | How long the grace period lasts. Usually `10m`. |
| `dtr` | The DTR every team starts with. Usually `5.0`. |
| `build.outside-bases` | The only blocks players can place outside their own base. Usually cobweb, water and lava. |
| `shops.catalog` | Which set of shop items the server uses. Use `default`. |
| `shops.npcs` | The shopkeepers. Each has a `shop` (`gear` to buy, `sell` to sell ores), a position `at`, and the `team` it belongs to. Leave out `team` for a shopkeeper anyone can use. Optional `profession` (the villager's job: `armorer`, `weaponsmith`, `librarian`, `cleric`, ... or `none`) and `biome` (its skin: `plains`, `desert`, `jungle`, `savanna`, `snow`, `swamp`, `taiga`) pick how the shopkeeper looks. |
| `mines` | Regions where broken ores grow back after `respawn` (usually `90s`). |
| `subclaims.price` | What a subclaim chest costs. Usually `250`. |
| `subclaims.chests` | The block positions of the chests players can buy, each with an optional `price` of its own. Place them inside team bases. |
| `economy.starting-balance` | Money each player starts with. Usually `0`. |
| `economy.kill-reward` | Money for each kill. Usually `50`. |

## time-limit

A clock for the match.

```yaml
time-limit: { duration: 45m, result: most-goals, overtime: 5m }
```

| Field | | What it is |
| --- | --- | --- |
| `duration` | required | How long the match can last. |
| `result` | | Who wins when time runs out: `most-dtr` (Bunkers), `most-goals`, `most-kills`, `tie`, or a team id (for example `defenders` in Base Defense). |
| `overtime` | | Extra time when the result would be a tie. |

## victory

How the match is won. Each mode already has a sensible list, so most maps leave this out.

```yaml
victory: [{ goals: all-enemy }, time-limit]
```

The first condition a team meets ends the match. The choices:

| Condition | What it means |
| --- | --- |
| `{ goals: all-own }` | Complete every goal your team owns (Capture the Wool). |
| `{ goals: all-enemy }` | Complete every goal the other teams own (Destroy the Core, Base Defense). |
| `{ goals: some-goal-id }` | Complete that one goal. |
| `control-point` or `{ control-point: koth }` | Fully capture the hill. |
| `last-team-standing` | Be the last team with players alive. |
| `time-limit` | When the clock runs out, `time-limit.result` decides. |

The defaults:

| Mode | Default |
| --- | --- |
| Bunkers | `[control-point, last-team-standing, time-limit]` |
| Destroy the Core | `[{ goals: all-enemy }, time-limit]` |
| Capture the Wool | `[{ goals: all-own }, time-limit]` |
| Base Defense | `[{ goals: all-enemy }, time-limit]` |

## variants

One world can be played in more than one way. A variant gives its own `name` and
replaces whole sections of the map.

```yaml
variants:
  quick:
    name: "Ravine (quick)"
    goals:
      - { type: control-point, id: koth, name: KOTH, region: koth-hill, capture-time: 3m }
    time-limit: { duration: 30m, result: most-dtr }
```

A variant can replace `objective`, `max-players`, `join`, `world`, `teams`, `spawns`,
`kits`, `kit-chests`, `classes`, `regions`, `goals`, `respawn`, `bunkers`, `time-limit`
and `victory`. A replaced section is replaced completely, so write it out in full. For
example, a variant with fewer teams also needs its own `spawns`, and for Bunkers its own
`bunkers.shops`. Each variant is checked like a map of its own.

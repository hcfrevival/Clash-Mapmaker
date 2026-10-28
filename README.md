# Clash Mapmaker

Build maps for **Clash**, the match server on HCF Revival.

A Clash map is a normal Minecraft world plus one text file, `map.yml`, that says who
plays, where they spawn and how they win. This repository holds everything you need to
write that file and check it.

Clash has four modes:

| Mode | How you win |
| --- | --- |
| **Bunkers** | Hold the KOTH in the middle of the map, or be the last team alive. Teams have bases, shops, ores and a few lives (DTR). |
| **Destroy the Core** | Break into the enemy core and make its lava leak out. |
| **Capture the Wool** | Steal the enemy's wool and place it on your own monument. |
| **Base Defense** | Raiders try to break the defenders' vault before the timer runs out. |

## Making a map

1. **Build the world** on any Minecraft server or in single player.
2. **Copy an example** from [`examples/`](examples/) for the mode you want and put it
   next to your world as `map.yml`.
3. **Fill in your own positions.** Press F3 in game to see the coordinates you are
   standing on. Change the teams, spawns, regions and goals to match your build.
4. **Look up anything you are unsure about** in [docs/MAP_FORMAT.md](docs/MAP_FORMAT.md).
   Every field is explained there.

A finished map folder looks like this:

```
my-map/
  map.yml
  region/
```

### Or build it in game: the ClashMapmaker plugin

Drop `clash-mapmaker-<version>.jar` from the
[releases page](https://github.com/hcfrevival/Clash-Mapmaker/releases) into a plain Paper
26.2 server and do all of this with commands instead of coordinates: select regions with a
wand, place spawns where you stand, see everything outlined with particles, check the map
with `/cmap validate` and export it as a zip ready to hand in.
[docs/MAPMAKER.md](docs/MAPMAKER.md) walks through a map step by step.

## Tips

- Start from an example. It is much easier to change a working map than to write one
  from nothing.
- Keep ids short and lowercase, like `red`, `red-base` or `koth`. Ids are how parts of
  the file point at each other: a team's `base: red-base` must match a region with
  `id: red-base`.
- Names that players see (map name, team names, goal names, item names) can use
  [MiniMessage](https://docs.advntr.dev/minimessage/format.html) colors, for example
  `"<gold>KOTH</gold>"`.
- Times are written like `90s`, `5m` or `1h30m`.
- Leave a setting out and Clash uses its normal default.

## For developers

The `clash-map` library reads, writes and checks `map.yml` files. It is plain Java 25
with one dependency (SnakeYAML) and needs no Minecraft server, so you can use it in
plugins, tools or tests.

Maven:

```xml
<repositories>
    <repository>
        <id>clash-mapmaker</id>
        <url>https://hcfrevival.github.io/Clash-Mapmaker/maven/</url>
    </repository>
</repositories>

<dependency>
    <groupId>gg.hcfrevival.clash</groupId>
    <artifactId>clash-map</artifactId>
    <version>0.4.0</version>
</dependency>
```

Gradle:

```kotlin
repositories {
    maven("https://hcfrevival.github.io/Clash-Mapmaker/maven/")
}

dependencies {
    implementation("gg.hcfrevival.clash:clash-map:0.4.0")
}
```

Reading and checking a map:

```java
MapManifestReader.Result result = ClashMaps.load(Path.of("my-map/map.yml"));
for (Problem problem : result.problems()) {
    System.out.println(problem); // for example: ERROR teams[0].base: there is no region called red-bsae.
}
if (!result.hasErrors()) {
    MapManifest map = result.manifest();
    System.out.println(map.info().name() + " has " + map.teams().size() + " teams");
}
```

The main classes:

| Class | What it does |
| --- | --- |
| `ClashMaps` | Read and check a map in one call. |
| `MapManifestReader` | Turns `map.yml` into a `MapManifest`. Reports mistakes instead of throwing. |
| `MapManifestWriter` | Saves a `MapManifest` back to `map.yml`, always in the same tidy order. |
| `MapValidator` | Checks that the map makes sense: ids point at real things, every team can spawn, the mode has what it needs. |
| `MapManifest` | The map itself, as plain records. |

### Building

You need JDK 25.

```
mvn verify
```

The tests load every map in `examples/`, so a broken example fails the build.

### Releases

Pushing a tag such as `v1.0.0` builds the project, creates a GitHub release with the plugin
jar and publishes the library to the Maven repository above. See [CHANGELOG.md](CHANGELOG.md).

## License

MIT, see [LICENSE](LICENSE).

package gg.hcfrevival.clash.map;

import gg.hcfrevival.clash.map.MapManifest.Bunkers;
import gg.hcfrevival.clash.map.MapManifest.ControlPoint;
import gg.hcfrevival.clash.map.MapManifest.Item;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MapManifestReaderTest {
    private final MapManifestReader reader = new MapManifestReader();

    @Test
    void missingProtoIsAnError() {
        MapManifestReader.Result result = reader.read("name: Test\n");
        assertNull(result.manifest());
        assertTrue(result.hasErrors());
        assertEquals("proto", result.problems().getFirst().path());
    }

    @Test
    void newerProtoIsRefused() {
        MapManifestReader.Result result = reader.read("proto: " + (Proto.CURRENT + 1) + "\nname: Test\n");
        assertNull(result.manifest());
        assertTrue(result.problems().getFirst().message().contains("Update Clash Mapmaker"));
    }

    @Test
    void notYamlIsAnError() {
        MapManifestReader.Result result = reader.read("proto: 1\nteams: [ {\n");
        assertNull(result.manifest());
        assertTrue(result.hasErrors());
    }

    @Test
    void duplicateKeysAreRefused() {
        MapManifestReader.Result result = reader.read("proto: 1\nname: A\nname: B\n");
        assertNull(result.manifest());
    }

    @Test
    void unknownKeysAreWarnings() {
        MapManifestReader.Result result = reader.read("""
                proto: 1
                nmae: Typo
                teams:
                  - { id: red, colour: red }
                """);
        assertFalse(result.hasErrors());
        assertEquals(2, result.problems().size());
        assertEquals("nmae", result.problems().stream().filter(p -> p.path().equals("nmae")).findFirst().orElseThrow().path());
        assertTrue(result.problems().stream().anyMatch(p -> p.path().equals("teams[0].colour")));
        assertTrue(result.problems().stream().noneMatch(Problem::isError));
    }

    @Test
    void wrongTypesAreErrorsWithAPath() {
        MapManifestReader.Result result = reader.read("""
                proto: 1
                max-players: lots
                goals:
                  - { type: control-point, id: koth, region: hill, capture-time: soon }
                """);
        assertTrue(result.hasErrors());
        assertTrue(result.problems().stream().anyMatch(p -> p.path().equals("max-players")));
        assertTrue(result.problems().stream().anyMatch(p -> p.path().equals("goals[0].capture-time")));
    }

    @Test
    void unknownGoalTypeIsAnError() {
        MapManifestReader.Result result = reader.read("""
                proto: 1
                goals:
                  - { type: flag, id: f }
                """);
        assertTrue(result.problems().stream().anyMatch(p -> p.isError() && p.path().equals("goals[0].type")));
        assertEquals(0, result.manifest().goals().size());
    }

    @Test
    void itemsCanBeJustAMaterial() {
        MapManifestReader.Result result = reader.read("""
                proto: 1
                kits:
                  starter:
                    armor: { helmet: iron_helmet }
                    items: [stone_sword, { material: arrow, amount: 16, slot: 9 }]
                """);
        MapManifest.Kit kit = result.manifest().kits().get("starter");
        assertEquals("iron_helmet", kit.armor().get("helmet").material());
        Item arrows = kit.items().get(1);
        assertEquals(16, arrows.amount());
        assertEquals(9, arrows.slot());
        assertEquals(1, kit.items().getFirst().amountOrOne());
    }

    @Test
    void readsDurations() {
        MapManifestReader.Result result = reader.read("""
                proto: 1
                goals:
                  - { type: control-point, id: koth, region: hill, capture-time: 1h30m }
                """);
        ControlPoint point = (ControlPoint) result.manifest().goals().getFirst();
        assertEquals(Duration.ofMinutes(90), point.captureTime());
    }

    @Test
    void shopkeepersCarryTheirLook() {
        MapManifest manifest = reader.read("""
                proto: 1
                bunkers:
                  shops:
                    npcs:
                      - { team: red, shop: gear, profession: armorer, biome: desert, at: { x: 0.5, y: 64, z: 0.5 } }
                      - { shop: sell, at: { x: 1.5, y: 64, z: 0.5 } }
                """).manifest();
        List<Bunkers.ShopNpc> npcs = manifest.bunkers().shops().npcs();
        assertEquals("armorer", npcs.get(0).profession());
        assertEquals("desert", npcs.get(0).biome());
        assertNull(npcs.get(1).profession());
        assertNull(npcs.get(1).biome());
        String saved = new MapManifestWriter().write(manifest);
        assertTrue(saved.contains("profession: armorer"), saved);
        assertEquals(manifest.bunkers(), reader.read(saved).manifest().bunkers());
    }

    @Test
    void cooldownsRoundTripAndVariantsReplaceThem() {
        MapManifest manifest = reader.read("""
                proto: 1
                cooldowns: { enderpearl: 16s, gapple: 5m, totem: 0s }
                variants:
                  fast:
                    cooldowns: { enderpearl: 5s }
                """).manifest();
        assertEquals(new MapManifest.Cooldowns(Duration.ofSeconds(16), null, Duration.ofMinutes(5), Duration.ZERO, null), manifest.cooldowns());
        String saved = new MapManifestWriter().write(manifest);
        assertTrue(saved.contains("cooldowns: {enderpearl: 16s, gapple: 5m, totem: 0s}"), saved);
        assertEquals(manifest.cooldowns(), reader.read(saved).manifest().cooldowns());
        assertEquals(new MapManifest.Cooldowns(Duration.ofSeconds(5), null, null, null, null),
                reader.readVariant(manifest, "fast").manifest().cooldowns());
        assertNull(reader.read("proto: 1\n").manifest().cooldowns());
    }

    @Test
    void variantsReplaceWholeSections() {
        MapManifestReader.Result base = reader.read("""
                proto: 1
                name: Base
                max-players: 40
                teams:
                  - { id: red, name: Red, color: red, max: 10 }
                  - { id: blue, name: Blue, color: blue, max: 10 }
                  - { id: green, name: Green, color: green, max: 10 }
                variants:
                  small:
                    name: Base (small)
                    max-players: 20
                    teams:
                      - { id: red, name: Red, color: red, max: 10 }
                      - { id: blue, name: Blue, color: blue, max: 10 }
                """);
        MapManifestReader.Result small = reader.readVariant(base.manifest(), "small");
        assertEquals("Base (small)", small.manifest().info().name());
        assertEquals(20, small.manifest().info().maxPlayers());
        assertEquals(2, small.manifest().teams().size());
        assertTrue(small.manifest().variants().isEmpty());
    }

    @Test
    void variantsCannotChangeTheSlug() {
        MapManifestReader.Result result = reader.read("""
                proto: 1
                slug: base
                variants:
                  other: { slug: other }
                """);
        assertTrue(result.problems().stream().anyMatch(p -> p.path().equals("variants.other.slug")));
    }
}

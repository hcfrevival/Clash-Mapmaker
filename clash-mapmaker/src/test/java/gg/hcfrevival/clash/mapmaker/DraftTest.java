package gg.hcfrevival.clash.mapmaker;

import gg.hcfrevival.clash.map.ClashMaps;
import gg.hcfrevival.clash.map.Gamemode;
import gg.hcfrevival.clash.map.MapManifest;
import gg.hcfrevival.clash.map.MapManifest.Author;
import gg.hcfrevival.clash.map.MapManifest.BlockPos;
import gg.hcfrevival.clash.map.MapManifest.ControlPoint;
import gg.hcfrevival.clash.map.MapManifest.Cuboid;
import gg.hcfrevival.clash.map.MapManifest.Pose;
import gg.hcfrevival.clash.map.MapManifestWriter;
import gg.hcfrevival.clash.map.MapValidator;
import gg.hcfrevival.clash.map.Problem;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DraftTest {
    private static final Author BUILDER = new Author("c8d2a6a0-3c4b-4b7b-9b1e-1f2a3b4c5d6e", "Builder", "Build");

    /** The command sequence from docs/MAPMAKER.md, minus the world, gives a map the library accepts. */
    @Test
    void bunkersWalkthroughValidates() {
        Draft draft = Draft.fresh("test-ravine", Gamemode.BUNKERS, BUILDER, new Pose(0.5, 80, 0.5, 0, 0));
        draft.addTeam("red", "red", 10, null);
        draft.addTeam("blue", "#5555ff", 10, "Blue Team");
        draft.setBase("red", cuboid(-260, 0, -60, -140, 120, 60));
        draft.setBase("blue", cuboid(140, 0, -60, 260, 120, 60));
        draft.addSpawn("red", new Pose(-200.5, 70, 0.5, 90, 0));
        draft.addSpawn("blue", new Pose(200.5, 70, 0.5, -90, 0));
        draft.putRegion("koth-hill", cuboid(-6, 68, -6, 6, 74, 6), Set.of("deny-build"), Set.of());
        draft.putRegion("map-bounds", cuboid(-300, 0, -120, 300, 120, 120), Set.of(), Set.of());
        draft.putGoal(new ControlPoint("koth", "KOTH", "koth-hill", Duration.ofMinutes(10)));
        draft.addShop("red", "gear", new Pose(-210.5, 70, 10.5, 180, 0), "armorer", null);
        draft.addMine("red-mine", cuboid(-230, 20, -40, -200, 50, -10), Duration.ofSeconds(90));
        draft.putSubclaim(new BlockPos(-212, 70, -3), null);
        draft.putSubclaim(new BlockPos(-212, 70, -3), 400);
        draft.bunkersRule("grace", "10m");
        draft.bunkersRule("dtr", "5.0");
        draft.bunkersRule("kill-reward", "50");
        draft.rule("bounds", "map-bounds");
        draft.rule("time-limit", "60m");
        draft.rule("gamerule.doDaylightCycle", "false");
        draft.rule("cooldown.enderpearl", "16s");

        MapManifest manifest = draft.build();
        List<Problem> problems = new MapValidator().validate(manifest);
        assertEquals(List.of(), problems.stream().filter(Problem::isError).toList(), problems.toString());
        assertEquals("Test Ravine", manifest.info().name());
        assertEquals("Blue Team", manifest.team("blue").orElseThrow().name());
        assertEquals("red-base", manifest.team("red").orElseThrow().base());
        assertEquals(List.of(400), manifest.bunkers().subclaims().chests().stream().map(chest -> chest.price()).toList());
        assertEquals(Boolean.FALSE, manifest.world().gamerules().get("doDaylightCycle"));
        assertEquals(Duration.ofSeconds(16), manifest.cooldowns().enderpearl());
        assertEquals(manifest, Draft.of(manifest).build());
    }

    @Test
    void savedFileReadsBackEqual() throws Exception {
        Draft draft = Draft.fresh("round-trip", Gamemode.DESTROY_THE_CORE, BUILDER, new Pose(0.5, 80, 0.5, 90, 0));
        draft.addTeam("red", "red", 5, null);
        draft.addTeam("blue", "blue", 5, null);
        draft.addSpawn("red", new Pose(-10.5, 70, 0.5, 90, 0));
        draft.addSpawn("blue", new Pose(10.5, 70, 0.5, -90, 0));
        draft.putRegion("red-core", cuboid(-20, 60, -3, -14, 66, 3), Set.of(), Set.of("blue"));
        draft.putGoal(new MapManifest.Core("red-core", "Red Core", "red", "obsidian", "red-core", 5, List.of()));
        MapManifest manifest = draft.build();
        Path file = Files.createTempFile("map", ".yml");
        new MapManifestWriter().write(manifest, file);
        assertEquals(manifest, ClashMaps.load(file).manifest());
    }

    @Test
    void mistakesAreSentencesForTheMaker() {
        Draft draft = Draft.fresh("test", Gamemode.BUNKERS, BUILDER, new Pose(0.5, 80, 0.5, 0, 0));
        draft.addTeam("red", "red", 10, null);
        assertEquals("There is already a team called red.", message(() -> draft.addTeam("red", "blue", 10, null)));
        assertEquals("purple-ish is not a color. Use a name like red or #ff5555.", message(() -> draft.addTeam("blue", "purple-ish", 10, null)));
        assertTrue(message(() -> draft.editTeam("red", "base", "red-bsae")).startsWith("There is no region called red-bsae."));
        assertTrue(message(() -> draft.addSpawn("green", new Pose(0, 0, 0, 0, 0))).startsWith("There is no team called green."));
        assertEquals("soon is not a time. Write it like 90s, 5m or 1h30m.", message(() -> draft.bunkersRule("grace", "soon")));
        assertTrue(message(() -> draft.putRegion("Hill", cuboid(0, 0, 0, 1, 1, 1), Set.of(), Set.of())).startsWith("A region id is lowercase"));
        assertTrue(message(() -> draft.putRegion("hill", cuboid(0, 0, 0, 1, 1, 1), Set.of("no-pvp"), Set.of())).startsWith("Unknown flag no-pvp."));
        assertTrue(message(() -> draft.rule("gravity", "low")).startsWith("Unknown rule gravity."));
        assertEquals("There is no kit called starter. Save one with /cmap kit set starter.", message(() -> draft.rule("respawn-kit", "starter")));
        assertThrows(IllegalArgumentException.class, () -> Draft.fresh("Bad Slug", Gamemode.BUNKERS, BUILDER, null));
    }

    private static String message(Runnable action) {
        return assertThrows(IllegalArgumentException.class, action::run).getMessage();
    }

    private static Cuboid cuboid(int x1, int y1, int z1, int x2, int y2, int z2) {
        return new Cuboid(new BlockPos(x1, y1, z1), new BlockPos(x2, y2, z2));
    }
}

package gg.hcfrevival.clash.map;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MapValidatorTest {
    private static String example(String slug) throws IOException {
        return Files.readString(ExamplesTest.examples().resolve(slug).resolve("map.yml"));
    }

    private static List<Problem> problems(String yaml) {
        return ClashMaps.load(yaml).problems();
    }

    private static boolean hasError(List<Problem> problems, String path) {
        return problems.stream().anyMatch(problem -> problem.isError() && problem.path().equals(path));
    }

    private static boolean hasWarning(List<Problem> problems, String path) {
        return problems.stream().anyMatch(problem -> !problem.isError() && problem.path().equals(path));
    }

    @Test
    void missingRegionIsAnError() throws IOException {
        String yaml = example("bunkers-ravine").replace("base: red-base", "base: red-bsae");
        assertTrue(hasError(problems(yaml), "teams[0].base"));
    }

    @Test
    void teamWithoutSpawnIsAnError() throws IOException {
        String yaml = example("dtc-twin-forts").replace("  - team: blue\n", "  - team: red\n");
        assertTrue(problems(yaml).stream().anyMatch(p -> p.isError() && p.message().contains("blue has nowhere to spawn")));
    }

    @Test
    void bunkersTeamsNeedABase() throws IOException {
        String yaml = example("bunkers-ravine").replace(", base: yellow-base", "");
        assertTrue(hasError(problems(yaml), "teams[3].base"));
    }

    @Test
    void bunkersNeedsExactlyOneControlPoint() throws IOException {
        String yaml = example("bunkers-ravine").replace("""
                goals:
                  - type: control-point
                """, """
                goals:
                  - { type: control-point, id: koth2, region: koth-hill }
                  - type: control-point
                """);
        assertTrue(hasError(problems(yaml), "goals"));
    }

    @Test
    void goalOwnerMustBeATeam() throws IOException {
        String yaml = example("dtc-twin-forts").replace("owner: blue", "owner: purple");
        assertTrue(hasError(problems(yaml), "goals[1].owner"));
    }

    @Test
    void baseDefenseNeedsRoles() throws IOException {
        String yaml = example("bd-the-vault").replace(", role: attacker", "");
        assertTrue(hasError(problems(yaml), "teams"));
    }

    @Test
    void unknownKitIsAnError() throws IOException {
        String yaml = example("dtc-twin-forts").replace("kit: fighter", "kit: figher");
        assertTrue(hasError(problems(yaml), "respawn.kit"));
    }

    @Test
    void victoryMustPointAtRealGoals() throws IOException {
        String yaml = example("dtc-twin-forts").replace("victory: [{ goals: all-enemy }, time-limit]",
                "victory: [{ goals: green-core }]");
        List<Problem> problems = problems(yaml);
        assertTrue(hasError(problems, "victory[0]"));
        assertTrue(hasError(problems, "victory"));
    }

    @Test
    void badColorsAreErrors() throws IOException {
        String yaml = example("dtc-twin-forts").replace("color: \"#3a7bff\"", "color: \"#3a7bf\"");
        assertTrue(hasError(problems(yaml), "teams[1].color"));
        String wool = example("ctw-loom").replace("color: cyan", "color: aqua");
        assertTrue(hasError(problems(wool), "goals[0].color"));
    }

    @Test
    void subclaimOutsideABaseIsAWarning() throws IOException {
        String yaml = example("bunkers-ravine").replace("- { x: -212, y: 70, z: -3 }", "- { x: 0, y: 70, z: 0 }");
        List<Problem> problems = problems(yaml);
        assertTrue(hasWarning(problems, "bunkers.subclaims.chests[0]"));
        assertFalse(MapValidator.hasErrors(problems));
    }

    @Test
    void shopkeeperLooksMustBeVillagerOnes() throws IOException {
        String yaml = example("bunkers-ravine").replace("profession: armorer", "profession: blacksmith").replace("biome: desert", "biome: nether");
        List<Problem> problems = problems(yaml);
        assertTrue(hasError(problems, "bunkers.shops.npcs[0].profession"));
        assertTrue(hasError(problems, "bunkers.shops.npcs[1].biome"));
        assertFalse(MapValidator.hasErrors(problems(example("bunkers-ravine"))));
    }

    @Test
    void variantsAreCheckedToo() throws IOException {
        String yaml = example("bunkers-ravine").replace(
                "region: koth-hill\n        capture-time: 3m", "region: koth-hil\n        capture-time: 3m");
        assertTrue(hasError(problems(yaml), "variants.quick.goals[0].region"));
    }

    @Test
    void anEmptyMapListsWhatIsMissing() {
        List<Problem> problems = problems("proto: 1\n");
        for (String path : List.of("name", "slug", "version", "gamemode", "world.spawn", "teams")) {
            assertTrue(hasError(problems, path), "expected an error at " + path + " in " + problems);
        }
    }
}

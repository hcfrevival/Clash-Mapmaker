package gg.hcfrevival.clash.map;

import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.TestFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Every map in examples/ must be clean and survive a save. */
class ExamplesTest {
    static Path examples() {
        return Path.of(System.getProperty("clash.examples", "../examples"));
    }

    static List<Path> maps() throws IOException {
        try (Stream<Path> files = Files.walk(examples())) {
            return files.filter(file -> file.getFileName().toString().equals("map.yml")).sorted().toList();
        }
    }

    @TestFactory
    Stream<DynamicTest> examplesHaveNoProblems() throws IOException {
        return maps().stream().map(file -> DynamicTest.dynamicTest(file.getParent().getFileName().toString(), () -> {
            MapManifestReader.Result result = ClashMaps.load(file);
            assertEquals(List.of(), result.problems(), "problems in " + file);
            assertNotNull(result.manifest());
            assertEquals(file.getParent().getFileName().toString(), result.manifest().info().slug(),
                    "the folder name should match the slug");
        }));
    }

    @TestFactory
    Stream<DynamicTest> examplesRoundTrip() throws IOException {
        return maps().stream().map(file -> DynamicTest.dynamicTest(file.getParent().getFileName().toString(), () -> {
            MapManifest original = new MapManifestReader().read(file).manifest();
            String saved = new MapManifestWriter().write(original);
            MapManifestReader.Result reread = new MapManifestReader().read(saved);
            assertEquals(List.of(), reread.problems(), saved);
            assertEquals(original, reread.manifest(), saved);
            assertEquals(saved, new MapManifestWriter().write(reread.manifest()), "saving twice should not change the file");
        }));
    }

    @TestFactory
    Stream<DynamicTest> oneExamplePerGamemode() throws IOException {
        List<Gamemode> covered = maps().stream()
                .map(file -> new MapManifestReader().read(file).manifest().info().gamemode())
                .toList();
        return Stream.of(Gamemode.values()).map(mode -> DynamicTest.dynamicTest(mode.id(),
                () -> assertTrue(covered.contains(mode), "no example for " + mode.id())));
    }
}

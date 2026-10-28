package gg.hcfrevival.clash.mapmaker;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ExportTest {
    @Test
    void zipHoldsTheManifestAndOnlyTheChunks() throws IOException {
        Path world = Files.createTempDirectory("world");
        write(world.resolve("region/r.0.0.mca"), "chunks");
        write(world.resolve("region/r.0.-1.mca"), "chunks");
        write(world.resolve("entities/r.0.0.mca"), "entities");
        write(world.resolve("playerdata/builder.dat"), "secret");
        write(world.resolve("data/raids.dat"), "raids");
        write(world.resolve("paper-world.yml"), "settings");
        write(world.resolve("session.lock"), "");
        write(world.resolve("level.dat"), "level");
        Path zip = world.resolve("out/ravine-1.0.0.zip");

        Export.zip("ravine", "proto: 1\n", world, zip);

        List<String> entries = new ArrayList<>();
        try (ZipInputStream in = new ZipInputStream(Files.newInputStream(zip))) {
            for (ZipEntry entry = in.getNextEntry(); entry != null; entry = in.getNextEntry()) {
                entries.add(entry.getName());
                if (entry.getName().equals("ravine/map.yml")) {
                    assertEquals("proto: 1\n", new String(in.readAllBytes(), StandardCharsets.UTF_8));
                }
            }
        }
        assertEquals(List.of("ravine/map.yml", "ravine/region/r.0.-1.mca", "ravine/region/r.0.0.mca", "ravine/entities/r.0.0.mca"), entries);
    }

    @Test
    void refusesAWorldWithoutChunks() throws IOException {
        Path world = Files.createTempDirectory("empty");
        IOException error = assertThrows(IOException.class, () -> Export.zip("x", "", world, world.resolve("x.zip")));
        assertTrue(error.getMessage().contains(world.toString()), error.getMessage());
    }

    private static void write(Path file, String text) throws IOException {
        Files.createDirectories(file.getParent());
        Files.writeString(file, text);
    }
}

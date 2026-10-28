package gg.hcfrevival.clash.mapmaker;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Stream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/**
 * The zip a map is submitted as: {@code <slug>/map.yml} and the world's chunks, the layout Clash reads straight
 * from its maps folder. Only {@link #WORLD_FOLDERS} are taken, so player data, stats and per-world server
 * configs never leave the builder's server.
 */
final class Export {
    static final List<String> WORLD_FOLDERS = List.of("region", "entities", "poi");

    private Export() {
    }

    static void zip(String slug, String mapYaml, Path worldFolder, Path zip) throws IOException {
        if (!Files.isDirectory(worldFolder.resolve("region"))) {
            throw new IOException("no region/ folder in " + worldFolder + "; the world has no saved chunks");
        }
        Files.createDirectories(zip.getParent());
        try (ZipOutputStream out = new ZipOutputStream(Files.newOutputStream(zip))) {
            out.putNextEntry(new ZipEntry(slug + "/map.yml"));
            out.write(mapYaml.getBytes(StandardCharsets.UTF_8));
            out.closeEntry();
            for (String name : WORLD_FOLDERS) {
                Path folder = worldFolder.resolve(name);
                if (!Files.isDirectory(folder)) {
                    continue;
                }
                List<Path> files;
                try (Stream<Path> walk = Files.walk(folder)) {
                    files = walk.filter(Files::isRegularFile).sorted().toList();
                }
                for (Path file : files) {
                    out.putNextEntry(new ZipEntry(slug + "/" + name + "/" + folder.relativize(file).toString().replace('\\', '/')));
                    Files.copy(file, out);
                    out.closeEntry();
                }
            }
        }
    }
}

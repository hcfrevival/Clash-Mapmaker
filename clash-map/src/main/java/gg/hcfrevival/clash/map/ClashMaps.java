package gg.hcfrevival.clash.map;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * The one-call way in: read a map.yml and check it.
 *
 * <pre>{@code
 * MapManifestReader.Result result = ClashMaps.load(Path.of("maps/ravine/map.yml"));
 * result.problems().forEach(System.out::println);
 * if (!result.hasErrors()) {
 *     MapManifest map = result.manifest();
 * }
 * }</pre>
 */
public final class ClashMaps {
    private ClashMaps() {
    }

    /** Reads and validates a file. Problems from both steps are in the result. */
    public static MapManifestReader.Result load(Path file) {
        return validated(new MapManifestReader().read(file));
    }

    /** Reads and validates YAML text. */
    public static MapManifestReader.Result load(String yaml) {
        return validated(new MapManifestReader().read(yaml));
    }

    private static MapManifestReader.Result validated(MapManifestReader.Result read) {
        if (read.manifest() == null) {
            return read;
        }
        List<Problem> problems = new ArrayList<>(read.problems());
        problems.addAll(new MapValidator().validate(read.manifest()));
        return new MapManifestReader.Result(read.manifest(), problems);
    }
}

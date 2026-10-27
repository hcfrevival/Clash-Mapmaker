package gg.hcfrevival.clash.map;

import gg.hcfrevival.clash.map.MapManifest.BlockPos;
import gg.hcfrevival.clash.map.MapManifest.Cuboid;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SmallTypesTest {
    @Test
    void durations() {
        assertEquals(Optional.of(Duration.ofSeconds(90)), Durations.parse("90s"));
        assertEquals(Optional.of(Duration.ofSeconds(90)), Durations.parse("90"));
        assertEquals(Optional.of(Duration.ofMinutes(5)), Durations.parse("5m"));
        assertEquals(Optional.of(Duration.ofMinutes(90)), Durations.parse("1h30m"));
        assertEquals(Optional.empty(), Durations.parse("soon"));
        assertEquals(Optional.empty(), Durations.parse("5m1h"));
        assertEquals("1m30s", Durations.format(Duration.ofSeconds(90)));
        assertEquals("1h", Durations.format(Duration.ofHours(1)));
        assertEquals("0s", Durations.format(Duration.ZERO));
    }

    @Test
    void colors() {
        assertTrue(MapColors.isColor("red"));
        assertTrue(MapColors.isColor("#FF5555"));
        assertFalse(MapColors.isColor("#ff555"));
        assertFalse(MapColors.isColor("cyan"));
        assertTrue(MapColors.isDye("cyan"));
        assertEquals("red", MapColors.nearestNamed("#ff5050"));
        assertEquals("blue", MapColors.nearestNamed("blue"));
    }

    @Test
    void cuboidIncludesBothCorners() {
        Cuboid cuboid = new Cuboid(new BlockPos(5, 10, 5), new BlockPos(-5, 0, -5));
        assertTrue(cuboid.contains(new BlockPos(5, 10, 5)));
        assertTrue(cuboid.contains(new BlockPos(-5, 0, -5)));
        assertTrue(cuboid.contains(5.9, 10.9, 5.9));
        assertFalse(cuboid.contains(6, 10, 5));
    }

    @Test
    void protoSupport() {
        assertTrue(Proto.isSupported(Proto.CURRENT));
        assertFalse(Proto.isSupported(Proto.CURRENT + 1));
        assertFalse(Proto.isSupported(0));
    }
}

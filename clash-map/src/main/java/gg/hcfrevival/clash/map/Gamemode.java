package gg.hcfrevival.clash.map;

import gg.hcfrevival.clash.map.MapManifest.ControlPointVictory;
import gg.hcfrevival.clash.map.MapManifest.GoalsVictory;
import gg.hcfrevival.clash.map.MapManifest.LastTeamStanding;
import gg.hcfrevival.clash.map.MapManifest.TimeLimitVictory;
import gg.hcfrevival.clash.map.MapManifest.VictoryCondition;

import java.util.Arrays;
import java.util.List;
import java.util.Optional;

/** The four ways Clash plays. A map picks one with {@code gamemode:}. */
public enum Gamemode {
    BUNKERS("bunkers", "Bunkers", List.of(
            new ControlPointVictory(null), new LastTeamStanding(), new TimeLimitVictory())),
    DESTROY_THE_CORE("dtc", "Destroy the Core", List.of(
            new GoalsVictory(GoalsVictory.ALL_ENEMY), new TimeLimitVictory())),
    CAPTURE_THE_WOOL("ctw", "Capture the Wool", List.of(
            new GoalsVictory(GoalsVictory.ALL_OWN), new TimeLimitVictory())),
    BASE_DEFENSE("bd", "Base Defense", List.of(
            new GoalsVictory(GoalsVictory.ALL_ENEMY), new TimeLimitVictory()));

    private final String id;
    private final String displayName;
    private final List<VictoryCondition> defaultVictory;

    Gamemode(String id, String displayName, List<VictoryCondition> defaultVictory) {
        this.id = id;
        this.displayName = displayName;
        this.defaultVictory = defaultVictory;
    }

    /** The value written in map.yml, for example {@code dtc}. */
    public String id() {
        return id;
    }

    public String displayName() {
        return displayName;
    }

    /** How a map of this mode is won when it has no {@code victory:} list. */
    public List<VictoryCondition> defaultVictory() {
        return defaultVictory;
    }

    public static Optional<Gamemode> byId(String id) {
        return Arrays.stream(values()).filter(mode -> mode.id.equalsIgnoreCase(id)).findFirst();
    }

    public static List<String> ids() {
        return Arrays.stream(values()).map(Gamemode::id).toList();
    }
}

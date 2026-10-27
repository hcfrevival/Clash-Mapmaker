package gg.hcfrevival.clash.map;

/**
 * The map.yml format version, written as {@code proto:} at the top of every map.
 *
 * <p>It only goes up when old maps would be read differently. A reader refuses maps
 * with a newer proto than it knows, so a map never loads half understood.
 */
public final class Proto {
    /** The newest format this version of the library reads and writes. */
    public static final int CURRENT = 1;

    private Proto() {
    }

    public static boolean isSupported(int proto) {
        return proto >= 1 && proto <= CURRENT;
    }
}

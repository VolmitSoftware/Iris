package art.arcane.iris.platform.bukkit.nms.datapack;

import art.arcane.iris.spi.IrisLogging;
import art.arcane.iris.platform.bukkit.nms.INMS;
import art.arcane.iris.platform.bukkit.nms.datapack.v1217.DataFixerV1217;
import art.arcane.volmlib.util.collection.KMap;
import lombok.AccessLevel;
import lombok.Getter;

import java.util.function.Supplier;

//https://minecraft.wiki/w/Pack_format
@Getter
public enum DataVersion {
    UNSUPPORTED("0.0.0", 0, () -> null),
    V26_1_2("26.1.2", 101, DataFixerV1217::new),
    V26_2("26.2", 107, DataFixerV1217::new);
    private static final KMap<DataVersion, IDataFixer> cache = new KMap<>();
    @Getter(AccessLevel.NONE)
    private final Supplier<IDataFixer> constructor;
    private final String version;
    private final int packFormat;

    DataVersion(String version, int packFormat, Supplier<IDataFixer> constructor) {
        this.constructor = constructor;
        this.packFormat = packFormat;
        this.version = version;
    }

    public IDataFixer get() {
        return cache.computeIfAbsent(this, k -> constructor.get());
    }

    public static IDataFixer getDefault() {
        DataVersion version = INMS.get().getDataVersion();
        if (version == null || version == UNSUPPORTED) {
            DataVersion fallback = getLatest();
            IrisLogging.warn("Unsupported datapack version mapping detected, falling back to latest fixer: " + fallback.getVersion());
            return fallback.get();
        }

        IDataFixer fixer = version.get();
        if (fixer == null) {
            DataVersion fallback = getLatest();
            IrisLogging.warn("Null datapack fixer for " + version.getVersion() + ", falling back to latest fixer: " + fallback.getVersion());
            return fallback.get();
        }

        return fixer;
    }

    public static DataVersion getLatest() {
        return values()[values().length - 1];
    }

    public static int minSupportedPackFormat() {
        int minimum = Integer.MAX_VALUE;
        for (DataVersion version : values()) {
            if (version == UNSUPPORTED) {
                continue;
            }
            minimum = Math.min(minimum, version.packFormat);
        }
        return minimum;
    }
}

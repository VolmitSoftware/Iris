package art.arcane.iris.pack.datapack;

import art.arcane.iris.spi.IrisPlatforms;
import art.arcane.iris.pack.datapack.v1217.DataFixerV1217;
import art.arcane.volmlib.util.collection.KMap;
import lombok.AccessLevel;
import lombok.Getter;

import java.util.function.Supplier;
import art.arcane.iris.pack.datapack.v263.DataFixerV263;

//https://minecraft.wiki/w/Pack_format
@Getter
public enum DataVersion {
    UNSUPPORTED("0.0.0", 0, () -> null),
    V26_1_2("26.1.2", 101, DataFixerV1217::new),
    V26_2("26.2", 107, DataFixerV1217::new),
    V26_3("26.3", 121, DataFixerV263::new);
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
        return getRuntime().get();
    }

    public static DataVersion forMinecraftVersion(String version) {
        if (version == null) {
            return UNSUPPORTED;
        }
        return switch (version) {
            case "26.1.2" -> V26_1_2;
            case "26.2", "26.2.0" -> V26_2;
            case "26.3", "26.3.0" -> V26_3;
            default -> UNSUPPORTED;
        };
    }

    public static DataVersion getRuntime() {
        if (!IrisPlatforms.isBound() || IrisPlatforms.get().minecraftVersion() == null) {
            return getLatest();
        }
        DataVersion version = forMinecraftVersion(IrisPlatforms.get().minecraftVersion());
        if (version == UNSUPPORTED) {
            throw new IllegalStateException("Unsupported Minecraft datapack version: " + IrisPlatforms.get().minecraftVersion());
        }
        return version;
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

package com.volmit.iris.core.nms.datapack;

import com.volmit.iris.core.nms.INMS;
import com.volmit.iris.core.nms.datapack.v1192.DataFixerV1192;
import com.volmit.iris.core.nms.datapack.v1206.DataFixerV1206;
import com.volmit.iris.core.nms.datapack.v1213.DataFixerV1213;
import com.volmit.iris.util.collection.KMap;
import lombok.AccessLevel;
import lombok.Getter;

import java.util.function.Supplier;

//https://minecraft.wiki/w/Pack_format
@Getter
public enum DataVersion {
    V1192("1.19.2", 10, DataFixerV1192::new),
    V1205("1.20.6", 41, DataFixerV1206::new),
    V1213("1.21.3", 57, DataFixerV1213::new);
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
        return INMS.get().getDataVersion().get();
    }

    public static DataVersion getLatest() {
        return values()[values().length - 1];
    }
}

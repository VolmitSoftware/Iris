package art.arcane.iris.modded;

import art.arcane.iris.generation.block.TileData;
import art.arcane.iris.spi.IrisLogging;
import art.arcane.volmlib.nativelib.minecraft26_2.modded.NativeTileData;
import art.arcane.volmlib.nativelib.terrain.NativeBlockState;
import art.arcane.volmlib.util.collection.KMap;

import java.io.DataOutputStream;
import java.io.IOException;

public final class ModdedTileData extends TileData {
    private final NativeTileData data;

    private ModdedTileData(NativeTileData data) {
        this.data = data;
    }

    public static ModdedTileData wrap(NativeTileData data) {
        return new ModdedTileData(data);
    }

    public static ModdedTileData capture(String blockKey, String snbt) throws IOException {
        return wrap(NativeTileData.capture(blockKey, snbt, IrisLogging::warn));
    }

    public static ModdedTileData fromProperties(NativeBlockState state, KMap<String, Object> properties) {
        return wrap(NativeTileData.fromProperties(state, properties));
    }

    public NativeTileData nativeData() {
        return data;
    }

    @Override
    public String getMaterialKey() {
        return data.getMaterialKey();
    }

    @Override
    public KMap<String, Object> getProperties() {
        return data.getProperties();
    }

    @Override
    public void toBinary(DataOutputStream out) throws IOException {
        data.toBinary(out);
    }

    @Override
    public boolean equals(Object other) {
        return other instanceof ModdedTileData tile && data.equals(tile.data);
    }

    @Override
    public int hashCode() {
        return data.hashCode();
    }

    @Override
    public String toString() {
        return data.toString();
    }

    @Override
    public TileData clone() {
        return wrap(data.clone());
    }
}

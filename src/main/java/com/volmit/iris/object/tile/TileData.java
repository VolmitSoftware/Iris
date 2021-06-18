package com.volmit.iris.object.tile;

import com.volmit.iris.util.KList;
import net.querz.nbt.tag.CompoundTag;
import org.bukkit.block.Block;
import org.bukkit.block.BlockState;
import org.bukkit.block.TileState;
import org.bukkit.block.data.BlockData;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;

public interface TileData<T extends TileState> extends Cloneable {

    public static final KList<TileData<? extends TileState>> registry = setup();

    static KList<TileData<? extends TileState>> setup() {
        KList<TileData<? extends TileState>> registry = new KList<>();

        registry.add(new TileSign());
        registry.add(new TileSpawner());
        registry.add(new TileBanner());

        return registry;
    }

    public static TileData<? extends TileState> read(DataInputStream s) throws Throwable {
        int id = s.readShort();
        TileData<? extends TileState> d = registry.get(id).getClass().getConstructor().newInstance();
        d.fromBinary(s);
        return d;
    }

    public static void setTileState(Block block, TileData<? extends TileState> data)
    {
        if(data.isApplicable(block.getBlockData()))
        {
            data.toBukkitTry(block.getState());
        }
    }

    public static TileData<? extends TileState> getTileState(Block block)
    {
        for(TileData<? extends TileState> i : registry)
        {
            BlockData data = block.getBlockData();

            if(i.isApplicable(data))
            {
                try {
                    TileData<? extends TileState> s = i.getClass().getConstructor().newInstance();
                    s.fromBukkitTry(block.getState());
                    return s;
                } catch (Throwable e) {
                    e.printStackTrace();
                }
            }
        }

        return null;
    }

    public String getTileId();

    public boolean isApplicable(BlockData data);

    public void toBukkit(T t);

    public void fromBukkit(T t);

    public default boolean toBukkitTry(BlockState t)
    {
        try {
            toBukkit((T) t);
            return true;
        }

        catch(Throwable e)
        {

        }

        return false;
    }

    public default boolean fromBukkitTry(BlockState t)
    {
        try {
            fromBukkit((T) t);
            return true;
        }

        catch(Throwable e)
        {

        }

        return false;
    }

    public TileData<T> clone();

    public void toBinary(DataOutputStream out) throws IOException;

    public void toNBT(CompoundTag tag);

    public void fromBinary(DataInputStream in) throws IOException;
}

package com.volmit.iris.nms.v1X;

import com.volmit.iris.nms.INMSBinding;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.block.Biome;

public class NMSBinding1X implements INMSBinding {
    @Override
    public Object getBiomeBaseFromId(int id) {
        return null;
    }

    @Override
    public int getTrueBiomeBaseId(Object biomeBase) {
        return 0;
    }

    @Override
    public Object getTrueBiomeBase(Location location) {
        return null;
    }

    @Override
    public String getTrueBiomeBaseKey(Location location) {
        return null;
    }

    @Override
    public Object getCustomBiomeBaseFor(String mckey) {
        return null;
    }

    @Override
    public String getKeyForBiomeBase(Object biomeBase) {
        return null;
    }

    public Object getBiomeBase(World world, Biome biome) {
        return null;
    }

    @Override
    public Object getBiomeBase(Object registry, Biome biome) {
        return null;
    }

    @Override
    public boolean isBukkit() {
        return true;
    }

    @Override
    public int getBiomeId(Biome biome) {
        return biome.ordinal();
    }
}

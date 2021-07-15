package com.volmit.iris.map;

import com.google.common.collect.BiMap;
import com.google.common.collect.HashBiMap;
import com.volmit.iris.object.IrisBiome;
import com.volmit.iris.object.IrisDimension;
import com.volmit.iris.object.IrisRegion;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class BiomeMap {

    private BiMap<IrisBiome, Integer> biomeMap;
    private BiMap<IrisRegion, Integer> regionMap;
    private IrisDimension dimension;

    private Set<Integer> activeBiomes = new HashSet<>();
    private Set<Integer> activeRegions = new HashSet<>();

    public BiomeMap(IrisDimension dimension) {
        this.dimension = dimension;

        List<IrisBiome> biomes = dimension.getAllAnyBiomes();
        List<IrisRegion> regions = dimension.getAllAnyRegions();

        biomeMap = HashBiMap.create(biomes.size());
        regionMap = HashBiMap.create(regions.size());

        int nextID = 0;

        for (IrisBiome biome : biomes) {
            biomeMap.putIfAbsent(biome, nextID);
            activeBiomes.add(nextID);
            nextID++;
        }

        nextID = 0;

        for (IrisRegion region : regions) {
            regionMap.putIfAbsent(region, nextID);
            activeRegions.add(nextID);
            nextID++;
        }
    }

    public IrisDimension getDimension() {
        return dimension;
    }

    public IrisBiome getBiome(int id) {
        return biomeMap.inverse().get(id);
    }

    public int getBiomeId(IrisBiome biome) {
        return biomeMap.get(biome);
    }

    public IrisRegion getRegion(int id) {
        return regionMap.inverse().get(id);
    }

    public int getRegionId(IrisRegion region) {
        return regionMap.get(region);
    }
}

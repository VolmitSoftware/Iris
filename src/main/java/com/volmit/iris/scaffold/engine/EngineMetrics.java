package com.volmit.iris.scaffold.engine;

import com.volmit.iris.util.AtomicRollingSequence;
import com.volmit.iris.util.KMap;
import lombok.Data;

@Data
public class EngineMetrics {
    private final AtomicRollingSequence total;
    private final AtomicRollingSequence updates;
    private final AtomicRollingSequence terrain;
    private final AtomicRollingSequence biome;
    private final AtomicRollingSequence parallax;
    private final AtomicRollingSequence parallaxInsert;
    private final AtomicRollingSequence post;
    private final AtomicRollingSequence decoration;
    private final AtomicRollingSequence cave;
    private final AtomicRollingSequence ravine;
    private final AtomicRollingSequence deposit;

    public EngineMetrics(int mem)
    {
        this.total = new AtomicRollingSequence(mem);
        this.terrain = new AtomicRollingSequence(mem);
        this.biome = new AtomicRollingSequence(mem);
        this.parallax = new AtomicRollingSequence(mem);
        this.parallaxInsert = new AtomicRollingSequence(mem);
        this.post = new AtomicRollingSequence(mem);
        this.decoration = new AtomicRollingSequence(mem);
        this.updates = new AtomicRollingSequence(mem);
        this.cave = new AtomicRollingSequence(mem);
        this.ravine = new AtomicRollingSequence(mem);
        this.deposit = new AtomicRollingSequence(mem);
    }

    public KMap<String, Double> pull() {
        KMap<String, Double> v = new KMap<>();
        v.put("terrain", terrain.getAverage());
        v.put("biome", biome.getAverage());
        v.put("parallax", parallax.getAverage());
        v.put("parallax.insert", parallaxInsert.getAverage());
        v.put("post", post.getAverage());
        v.put("decoration", decoration.getAverage());
        v.put("updates", updates.getAverage());
        v.put("cave", cave.getAverage());
        v.put("ravine", ravine.getAverage());
        v.put("deposit", deposit.getAverage());

        return v;
    }
}

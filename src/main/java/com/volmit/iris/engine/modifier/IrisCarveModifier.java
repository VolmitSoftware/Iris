/*
 * Iris is a World Generator for Minecraft Bukkit Servers
 * Copyright (c) 2021 Arcane Arts (Volmit Software)
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

package com.volmit.iris.engine.modifier;

import com.volmit.iris.engine.IrisEngine;
import com.volmit.iris.engine.actuator.IrisDecorantActuator;
import com.volmit.iris.engine.data.cache.Cache;
import com.volmit.iris.engine.framework.Engine;
import com.volmit.iris.engine.framework.EngineAssignedModifier;
import com.volmit.iris.engine.object.biome.IrisBiome;
import com.volmit.iris.engine.object.decoration.IrisDecorationPart;
import com.volmit.iris.engine.object.decoration.IrisDecorator;
import com.volmit.iris.util.collection.KList;
import com.volmit.iris.util.collection.KMap;
import com.volmit.iris.util.data.B;
import com.volmit.iris.util.format.Form;
import com.volmit.iris.util.hunk.Hunk;
import com.volmit.iris.util.mantle.Mantle;
import com.volmit.iris.util.mantle.MantleChunk;
import com.volmit.iris.util.math.RNG;
import com.volmit.iris.util.matter.MatterCavern;
import com.volmit.iris.util.scheduling.PrecisionStopwatch;
import lombok.Data;
import org.bukkit.Material;
import org.bukkit.block.data.BlockData;

import java.util.Objects;
import java.util.function.Supplier;

public class IrisCarveModifier extends EngineAssignedModifier<BlockData> {
    private final RNG rng;
    private final BlockData AIR = Material.CAVE_AIR.createBlockData();

    public IrisCarveModifier(Engine engine) {
        super(engine, "Carve");
        rng = new RNG(getEngine().getWorld().seed() + 3297778).nextParallelRNG(67648777);
    }

    @Override
    public void onModify(int x, int z, Hunk<BlockData> output, boolean multicore) {
        PrecisionStopwatch p = PrecisionStopwatch.start();
        Mantle mantle = getEngine().getMantle().getMantle();
        MantleChunk mc = getEngine().getMantle().getMantle().getChunk(x, z);
        KMap<Long, KList<Integer>> positions = new KMap<>();

        mc.iterate(MatterCavern.class, (xx, yy, zz, c) -> {
            if(yy > 256 || yy < 0)
            {
                return;
            }

            int rx = xx & 15;
            int rz = zz & 15;
            BlockData current = output.get(rx, yy, rz);

            if(B.isFluid(current))
            {
                return;
            }

            positions.compute(Cache.key(rx, rz), (k,v) -> Objects.requireNonNullElseGet(v, (Supplier<KList<Integer>>) KList::new).qadd(yy));

            if(current.getMaterial().isAir())
            {
                return;
            }

            output.set(rx, yy, rz, AIR);
        });

        positions.forEach((k, v) -> {
            if(v.isEmpty())
            {
                return;
            }

            int rx = Cache.keyX(k);
            int rz = Cache.keyZ(k);
            v.sort(Integer::compare);
            CaveZone zone = new CaveZone();
            zone.setFloor(v.get(0));
            int buf = v.get(0) - 1;
            for(Integer i : v) {
                if (i < 0 || i > 255) {
                    continue;
                }

                if (i == buf + 1)
                {
                    buf = i;
                    zone.ceiling = buf;
                }

                else if(zone.isValid())
                {
                    processZone(output, mc, zone, rx, rz, rx + (x << 4), rz + (z << 4));
                    zone = new CaveZone();
                    zone.setFloor(i);
                    buf = i;
                }
            }
        });

        getEngine().getMetrics().getDeposit().put(p.getMilliseconds());
    }

    private void processZone(Hunk<BlockData> output, MantleChunk mc, CaveZone zone, int rx, int rz, int xx, int zz) {
        boolean decFloor = B.isSolid(output.get(rx, zone.floor - 1, rz));
        boolean decCeiling = B.isSolid(output.get(rx, zone.ceiling + 1, rz));
        int center = (zone.floor + zone.ceiling) / 2;
        int thickness = zone.airThickness();
        MatterCavern cavernData = (MatterCavern) mc.getOrCreate(center >> 4).slice(MatterCavern.class)
                .get(rx, center & 15, rz);
        IrisBiome biome = cavernData.getCustomBiome().isEmpty() ? getEngine().getCaveBiome(xx, zz)
                : getEngine().getData().getBiomeLoader().load(cavernData.getCustomBiome());

        if(biome == null)
        {
            return;
        }

        IrisDecorantActuator actuator = (IrisDecorantActuator) ((IrisEngine)getEngine()).getDecorantActuator();
        for(IrisDecorator i : biome.getDecorators()) {
            if (i.getPartOf().equals(IrisDecorationPart.NONE)) {
                actuator.getSurfaceDecorator().decorate(rx, rz, xx, xx, xx, zz, zz, zz, output, biome, zone.getFloor() - 1, zone.getCeiling());
            } else if (i.getPartOf().equals(IrisDecorationPart.CEILING)) {
                actuator.getCeilingDecorator().decorate(rx, rz, xx, xx, xx, zz, zz, zz, output, biome, zone.getCeiling() + 1, zone.getFloor());
            }
        }

        KList<BlockData> blocks = biome.generateLayers(getDimension(), xx, zz, rng, 3, zone.floor, getData(), getComplex());

        for(int i = 0; i < zone.floor-1; i++)
        {
            if(!blocks.hasIndex(i))
            {
                break;
            }

            output.set(rx, zone.floor - i, rz, blocks.get(i));
        }

//        blocks = biome.generateCeilingLayers(getDimension(), xx, zz, rng, 3, zone.floor, getData(), getComplex()).reverse();
//
//        for(int i = 0; i < zone.ceiling-1; i++)
//        {
//            if(!blocks.hasIndex(i))
//            {
//                break;
//            }
//
//            output.set(rx, zone.ceiling + i, rz, blocks.get(i));
//        }
    }

    @Data
    public static class CaveZone
    {
        private int ceiling = -1;
        private int floor = -1;

        public int airThickness()
        {
            return (ceiling - floor) - 1;
        }

        public boolean isValid()
        {
            return floor < ceiling && ceiling - floor >= 1 && floor >= 0 && ceiling <= 255 && airThickness() > 0;
        }
    }
}

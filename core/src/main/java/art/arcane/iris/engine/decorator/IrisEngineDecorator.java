/*
 * Iris is a World Generator for Minecraft Bukkit Servers
 * Copyright (c) 2022 Arcane Arts (Volmit Software)
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

package art.arcane.iris.engine.decorator;

import art.arcane.iris.Iris;
import art.arcane.iris.engine.framework.Engine;
import art.arcane.iris.engine.framework.EngineAssignedComponent;
import art.arcane.iris.engine.framework.EngineDecorator;
import art.arcane.iris.engine.mantle.EngineMantle;
import art.arcane.iris.engine.object.IrisBiome;
import art.arcane.iris.engine.object.IrisDecorationPart;
import art.arcane.iris.engine.object.IrisDecorator;
import art.arcane.volmlib.util.collection.KList;
import art.arcane.iris.util.common.data.B;
import art.arcane.iris.util.project.hunk.Hunk;
import art.arcane.volmlib.util.documentation.BlockCoordinates;
import art.arcane.volmlib.util.math.RNG;
import lombok.Getter;
import org.bukkit.block.BlockFace;
import org.bukkit.block.BlockSupport;
import org.bukkit.block.data.BlockData;
import org.bukkit.block.data.MultipleFacing;

public abstract class IrisEngineDecorator extends EngineAssignedComponent implements EngineDecorator {
    @Getter
    private final IrisDecorationPart part;
    private final long seed;
    private final long modX, modZ;

    public IrisEngineDecorator(Engine engine, String name, IrisDecorationPart part) {
        super(engine, name + " Decorator");
        this.part = part;
        this.seed = getSeed() + 29356788 - (part.ordinal() * 10439677L);
        this.modX = 29356788 ^ (part.ordinal() + 6);
        this.modZ = 10439677 ^ (part.ordinal() + 1);
    }

    @BlockCoordinates
    protected RNG getRNG(int x, int z) {
        return new RNG(x * modX + z * modZ + seed);
    }

    protected IrisDecorator getDecorator(RNG rng, IrisBiome biome, double realX, double realZ) {
        KList<IrisDecorator> v = new KList<>();

        RNG gRNG = new RNG(seed);
        for (IrisDecorator i : biome.getDecorators()) {
            try {
                if (i.getPartOf().equals(part) && i.getBlockData(biome, gRNG, realX, realZ, getData()) != null) {
                    v.add(i);
                }
            } catch (Throwable e) {
                Iris.reportError(e);
                Iris.error("PART OF: " + biome.getLoadFile().getAbsolutePath() + " HAS AN INVALID DECORATOR near 'partOf'!!!");
            }
        }

        if (v.isNotEmpty()) {
            return v.get(rng.nextInt(v.size()));
        }

        return null;
    }

    protected BlockData fixFaces(BlockData b, Hunk<BlockData> hunk, int rX, int rZ, int x, int y, int z) {
        if (B.isVineBlock(b)) {
            MultipleFacing data = (MultipleFacing) b.clone();
            data.getFaces().forEach(f -> data.setFace(f, false));

            boolean found = false;
            for (BlockFace f : BlockFace.values()) {
                if (!f.isCartesian())
                    continue;
                int yy = y + f.getModY();

                BlockData r = getEngine().getMantle().getMantle().get(x + f.getModX(), yy, z + f.getModZ(), BlockData.class);
                if (r == null) {
                    r = EngineMantle.AIR;
                }
                if (r.isFaceSturdy(f.getOppositeFace(), BlockSupport.FULL)) {
                    found = true;
                    data.setFace(f, true);
                    continue;
                }

                int xx = rX + f.getModX();
                int zz = rZ + f.getModZ();
                if (xx < 0 || xx > 15 || zz < 0 || zz > 15 || yy < 0 || yy > hunk.getHeight())
                    continue;

                r = hunk.get(xx, yy, zz);
                if (r.isFaceSturdy(f.getOppositeFace(), BlockSupport.FULL)) {
                    found = true;
                    data.setFace(f, true);
                }
            }
            if (!found)
                data.setFace(BlockFace.DOWN, true);
            return data;
        }
        return b;
    }
}

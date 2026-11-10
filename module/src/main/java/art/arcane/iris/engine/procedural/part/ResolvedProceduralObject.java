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
package art.arcane.iris.engine.procedural.part;

import art.arcane.iris.engine.procedural.BakeProfile;
import art.arcane.iris.engine.procedural.ProceduralShape;
import art.arcane.iris.engine.procedural.ProceduralVariants;
import art.arcane.iris.modules.objects.ObjectBuilder;
import art.arcane.volmlib.util.math.RNG;
import com.volmit.geometry.BlockPosition;
import com.volmit.object.ObjectBlock;
import com.volmit.object.PlaceableObject;
import com.volmit.resource.PackResources;
import com.volmit.tooling.BlockParts;

import java.util.HashMap;
import java.util.Map;

/** The variants of a procedural object, baked by {@link ObjectBaker}. */
public final class ResolvedProceduralObject extends ProceduralVariants {
    private final ProceduralObject definition;
    private final PartOutline outline;

    public ResolvedProceduralObject(String path, ProceduralObject definition, PackResources resources) {
        super(path, definition.seed(), definition.variants(), resources);
        this.definition = definition;
        this.outline = PartOutline.of(definition);
    }

    @Override
    protected ProceduralShape generate(int index, RNG rng, ObjectBuilder blocks, BakeProfile profile) {
        return ObjectBaker.bake(definition, outline, rng, blocks, profile);
    }

    public PartOutline outline() {
        return outline;
    }

    /** The part of each block of {@code variant} that has one, in the positions of its geometry; empty when it is not one of these. */
    public BlockParts parts(PlaceableObject variant) {
        PartTrace trace = trace(variant);
        Map<BlockPosition, Integer> partsByBlock = new HashMap<>();
        for (ObjectBlock block : variant.geometry().blocks()) {
            BlockPosition position = block.position();
            int part = trace.partAt(position.x(), position.y(), position.z());
            if (part != PartOutline.NO_PART) {
                partsByBlock.put(position, part);
            }
        }
        return new BlockParts(partsByBlock);
    }

    /** Which part placed each block of {@code variant}, baked again to find out; empty when it is not one of these. */
    public PartTrace trace(PlaceableObject variant) {
        int index = bakeIndexOf(variant);
        if (index < 0) {
            return PartTrace.EMPTY;
        }
        return ObjectBaker.trace(definition, outline, variantRng(index), newBuilder());
    }
}

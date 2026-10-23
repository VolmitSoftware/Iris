/*
 * Iris is a World Generator for Minecraft Servers
 * Copyright (c) 2026 Arcane Arts (Volmit Software)
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

package art.arcane.iris.engine.mantle.components;

import art.arcane.iris.builtin.objects.ObjectHost;
import art.arcane.iris.builtin.objects.ObjectHost.Declaration;
import art.arcane.iris.builtin.objects.ObjectPlacement;
import art.arcane.iris.builtin.objects.ObjectSettings;
import art.arcane.iris.builtin.objects.ProceduralObjectSettings;
import art.arcane.iris.builtin.objects.ProceduralPlacement;
import art.arcane.iris.builtin.IrisSeed;
import art.arcane.iris.engine.object.procedural.ResolvedProceduralObjects;
import art.arcane.volmlib.util.math.RNG;
import com.volmit.config.Level;
import com.volmit.world.PackWorld;

import java.util.Comparator;
import java.util.List;

/**
 * Builds what object placing would build on first use, for every object placement and procedural object the world's
 * biomes and regions declare: the placement's surface warp and density noise, and a procedural object's variants. None
 * of it depends on the random it is warmed with; the warp is seeded from the world, the density noise is cached per seed
 * and the variants are baked whatever picks one.
 */
final class ObjectWarmUp {
    private static final long ROOT_SEED_SALT = 7777L;
    private static final Level[] WARMED_LEVELS = {Level.BIOME, Level.REGION};

    private final ObjectHost host;
    private final RNG root;
    private int warmed;

    ObjectWarmUp(PackWorld world) {
        this.host = ObjectHost.of(world);
        this.root = new RNG(IrisSeed.COMPONENT.of(world) + ROOT_SEED_SALT);
    }

    void warm() {
        List<Declaration<ObjectSettings>> placements = byKey(host.declarations(ObjectSettings.class));
        List<Declaration<ProceduralObjectSettings>> procedural = byKey(host.declarations(ProceduralObjectSettings.class));
        for (Level level : WARMED_LEVELS) {
            for (Declaration<ObjectSettings> declaration : placements) {
                if (declaration.level() == level) {
                    warmPlacements(declaration.settings().objects());
                }
            }
            for (Declaration<ProceduralObjectSettings> declaration : procedural) {
                if (declaration.level() == level) {
                    warmProcedural(declaration.settings());
                }
            }
        }
    }

    private static <F extends com.volmit.IrisFeature> List<Declaration<F>> byKey(List<Declaration<F>> declarations) {
        return declarations.stream().sorted(Comparator.comparing(Declaration::key)).toList();
    }

    private void warmPlacements(List<ObjectPlacement> placements) {
        for (ObjectPlacement placement : placements) {
            if (placement != null) {
                host.warm(placement, root.nextParallelRNG(warmed++));
            }
        }
    }

    private void warmProcedural(ProceduralObjectSettings settings) {
        for (ProceduralPlacement placement : ResolvedProceduralObjects.of(host, settings)) {
            placement.variant(root.nextParallelRNG(warmed++));
        }
    }
}

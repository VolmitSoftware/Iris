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

package art.arcane.iris.engine.framework.structure;

import art.arcane.iris.core.modules.IrisPackSettings;
import art.arcane.iris.core.modules.IrisPackWorld;
import art.arcane.iris.engine.framework.Engine;
import art.arcane.iris.engine.framework.IrisStructureLocator;
import art.arcane.iris.engine.framework.StructurePlacementGrid;
import art.arcane.iris.engine.object.structure.IrisStructurePlacement;
import art.arcane.iris.engine.object.structure.vanilla.NativeStructureSuppression;
import com.google.gson.Gson;
import com.volmit.resource.ResourceId;
import com.volmit.vanilla.JigsawOverrides;
import com.volmit.vanilla.StructureStilts;
import com.volmit.vanilla.StructureTerrain;
import com.volmit.vanilla.VanillaStructureAdjustment;
import com.volmit.vanilla.VanillaStructureContext;
import com.volmit.vanilla.VanillaStructureFit;
import com.volmit.vanilla.VanillaStructureHook;
import com.volmit.vanilla.VanillaStructureRule;
import com.volmit.vanilla.VanillaStructureSetup;
import com.volmit.vanilla.VanillaStructureStart;
import com.volmit.vanilla.VanillaStructureStartContext;

import java.util.List;

/**
 * What {@code iris:structure} says about the platform's own structures. The dimension's {@code iris:structure}
 * settings ({@link ImportedStructures}) disable, adjust and set them up; the pack's structure placements replace
 * them ({@code vanillaSource} with native suppression), place them alongside ({@link IrisStructureLocator#isPlaced})
 * and start them where their {@code nativeStructures} grids say ({@link NativeStartPlanner}).
 */
public final class StructureVanillaHook implements VanillaStructureHook {
    private static final Gson SAME_JSON = new Gson();

    /**
     * {@link VanillaStructureRule#REPLACE} for a structure an Iris structure replaces or the dimension disables while a
     * placement places it, {@link VanillaStructureRule#BLOCK} for one the dimension disables, and
     * {@link VanillaStructureRule#ALSO_PLACE} for one a placement places alongside the platform.
     */
    @Override
    public VanillaStructureRule rule(ResourceId structure, VanillaStructureContext context) {
        Engine engine = IrisPackSettings.of(context.settings()).engine();
        String key = structure.toString();
        boolean replaced = IrisStructureLocator.suppressesVanilla(engine, key);
        boolean blocked = replaced || ImportedStructures.of(context.settings()).disables(key);
        boolean placed = replaced || IrisStructureLocator.isPlaced(engine, key);
        VanillaStructureRule rule = blocked ? VanillaStructureRule.BLOCK : VanillaStructureRule.GENERATE;
        return placed ? rule.and(VanillaStructureRule.ALSO_PLACE) : rule;
    }

    @Override
    public VanillaStructureAdjustment adjustment(ResourceId structure, boolean underground, VanillaStructureContext context) {
        return ImportedStructures.of(context.settings()).adjustment(structure.toString(), underground);
    }

    @Override
    public List<VanillaStructureStart> starts(int chunkX, int chunkZ, VanillaStructureStartContext context) {
        Engine engine = IrisPackWorld.of(context.world()).engine();
        return NativeStartPlanner.plansAt(engine, chunkX, chunkZ).stream()
                .map(plan -> start(plan, engine.getMinHeight()))
                .toList();
    }

    @Override
    public VanillaStructureSetup setup(VanillaStructureContext context) {
        return ImportedStructures.of(context.settings()).setup();
    }

    /** {@code plan} as the start the platform generates: heights above the bottom, the placement's fit and identity. */
    static VanillaStructureStart start(NativeStartPlanner.Plan plan, int minHeight) {
        IrisStructurePlacement placement = plan.placement();
        VanillaStructureStart start = VanillaStructureStart.at(ResourceId.parse(plan.source().getStructure()),
                        plan.chunkX(), plan.chunkZ(), plan.baseY() - minHeight)
                .withIdentity(StructurePlacementGrid.placementIdentity(placement))
                .withFit(fit(placement));
        if (placement.isUnderground()) {
            start = start.underground();
        }
        if (placement.getNativeSuppression() == NativeStructureSuppression.REPLACE_SOURCE) {
            start = start.replacingPlatformStart();
        }
        if (plan.source().getJigsaw() != null) {
            start = start.withJigsaw(sameJson(plan.source().getJigsaw(), JigsawOverrides.class));
        }
        return start;
    }

    private static VanillaStructureFit fit(IrisStructurePlacement placement) {
        VanillaStructureFit fit = VanillaStructureFit.SOURCE.withTerrain(sameJson(placement.resolvedTerrain(), StructureTerrain.class));
        return placement.getStilt() == null ? fit : fit.withStilts(sameJson(placement.getStilt(), StructureStilts.class));
    }

    /** The placement classes are Iris's; the API values read the same JSON. */
    private static <T> T sameJson(Object irisValue, Class<T> apiType) {
        return SAME_JSON.fromJson(SAME_JSON.toJsonTree(irisValue), apiType);
    }
}

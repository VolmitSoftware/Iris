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
import art.arcane.iris.engine.framework.structure.fit.StartLayouts;
import art.arcane.iris.engine.framework.structure.fit.StructureFitting;
import art.arcane.iris.engine.object.structure.IrisStructurePlacement;
import art.arcane.iris.engine.object.structure.vanilla.NativeStructureSuppression;
import com.volmit.resource.ResourceId;
import com.volmit.service.VanillaStructures;
import com.volmit.vanilla.PlatformStart;
import com.volmit.vanilla.StartMemo;
import com.volmit.vanilla.StartReach;
import com.volmit.vanilla.StructureFit;
import com.volmit.vanilla.VanillaStructureContext;
import com.volmit.vanilla.VanillaStructureRule;
import com.volmit.vanilla.VanillaStructureSetup;
import com.volmit.vanilla.VanillaStructureStart;
import com.volmit.vanilla.VanillaStructureStartContext;

import java.util.List;
import java.util.Optional;

/**
 * What {@code iris:structure} says about the platform's own structures. The dimension's {@code importedStructures}
 * settings ({@link ImportedStructures}) disable, move, fit and set them up; the pack's structure placements replace
 * them ({@code vanillaSource} with native suppression), place them alongside ({@link IrisStructureLocator#isPlaced}),
 * start them where their {@code nativeStructures} grids say ({@link NativeStartPlanner}) and fit those starts the way
 * the placement says, which their memo carries to every chunk they reach ({@link StructureFitting}).
 */
public final class StructureVanillaStructures implements VanillaStructures {
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
    public List<VanillaStructureStart> starts(int chunkX, int chunkZ, VanillaStructureStartContext context) {
        Engine engine = IrisPackWorld.of(context.world()).engine();
        return NativeStartPlanner.plansAt(engine, chunkX, chunkZ).stream()
                .map(plan -> start(plan, engine.getMinHeight()))
                .toList();
    }

    @Override
    public int shift(PlatformStart start, VanillaStructureContext context) {
        return StartLayouts.shift(start, ImportedStructures.of(context.settings())
                .adjustment(start.structure().toString(), start.underground()));
    }

    @Override
    public StartReach reach(ResourceId structure, boolean underground, Optional<StartMemo> memo,
                            VanillaStructureContext context) {
        NativeStructureFit fit = memo.map(NativeStructureFit::of)
                .orElseGet(() -> ImportedStructures.of(context.settings()).adjustment(structure.toString(), underground));
        return StartLayouts.reach(fit.resolvedTerrain());
    }

    @Override
    public void beforePieces(StructureFit fit) {
        StructureFitting.beforePieces(fit);
    }

    @Override
    public void afterPieces(StructureFit fit, StructureFit.Start placed) {
        StructureFitting.afterPieces(fit, placed);
    }

    @Override
    public VanillaStructureSetup setup(VanillaStructureContext context) {
        return ImportedStructures.of(context.settings()).setup();
    }

    /** {@code plan} as the start the platform generates: heights above the bottom, the placement's identity and fit. */
    static VanillaStructureStart start(NativeStartPlanner.Plan plan, int minHeight) {
        IrisStructurePlacement placement = plan.placement();
        NativeStructureFit fit = new NativeStructureFit(0, null, false, placement.getStilt(), placement.resolvedTerrain());
        VanillaStructureStart start = VanillaStructureStart.at(ResourceId.parse(plan.source().getStructure()),
                        plan.chunkX(), plan.chunkZ(), plan.baseY() - minHeight)
                .withIdentity(StructurePlacementGrid.placementIdentity(placement))
                .withMemo(fit.memo());
        if (placement.isUnderground()) {
            start = start.underground();
        }
        if (placement.getNativeSuppression() == NativeStructureSuppression.REPLACE_SOURCE) {
            start = start.replacingPlatformStart();
        }
        if (plan.source().getJigsaw() != null) {
            start = start.withJigsaw(plan.source().getJigsaw());
        }
        return start;
    }
}

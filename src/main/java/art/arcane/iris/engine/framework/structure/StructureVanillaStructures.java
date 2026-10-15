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
import art.arcane.iris.builtin.structure.StructurePlacement;
import art.arcane.iris.builtin.structure.NativeStructureSuppression;
import com.volmit.resource.ResourceId;
import com.volmit.service.LocateResult;
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
import com.volmit.world.BlockPosition;
import com.volmit.world.PackWorld;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;

/**
 * What {@code iris:structure} says about the platform's own structures. The dimension's {@code importedStructures}
 * settings ({@link ImportedStructures}) disable, move, fit and set them up; the pack's structure placements replace
 * them ({@code vanillaSource} with native suppression), place them alongside ({@link IrisStructureLocator#isPlaced}),
 * start them where their {@code nativeStructures} grids say ({@link NativeStartPlanner}) and fit those starts the way
 * the placement says, which their memo carries to every chunk they reach ({@link StructureFitting}). It names the
 * structures its native placements start and finds their starts for the platform's own locate.
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
        Engine engine = engine(context.world());
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

    @Override
    public Set<ResourceId> startedStructures(PackWorld world) {
        return structureIds(IrisStructureLocator.nativeKeys(engine(world)));
    }

    @Override
    public Set<ResourceId> locatableStarts(PackWorld world) {
        return structureIds(IrisStructureLocator.locatableNativeKeys(engine(world)));
    }

    /** Searches the rings of the placements that start {@code structure} natively, as the platform's own locate does. */
    @Override
    public LocateResult locateStart(PackWorld world, ResourceId structure, int fromX, int fromZ, int maxRings,
                                    ChunkFilter accepts) {
        Engine engine = engine(world);
        IrisStructureLocator.LocateResult result = IrisStructureLocator.locateInPlacementRings(
                engine, structure.toString(), fromX, fromZ, maxRings, accepts::accepts);
        return switch (result.status()) {
            case FOUND -> LocateResult.found(new BlockPosition(result.originX(), result.baseY() - engine.getMinHeight(), result.originZ()));
            case NOT_FOUND -> LocateResult.NOT_FOUND;
            case SEARCH_LIMIT_REACHED -> LocateResult.SEARCH_LIMIT_REACHED;
        };
    }

    private static Engine engine(PackWorld world) {
        return IrisPackWorld.of(world).engine();
    }

    private static Set<ResourceId> structureIds(Set<String> keys) {
        Set<ResourceId> ids = new LinkedHashSet<>();
        for (String key : keys) {
            ids.add(ResourceId.parse(key.toLowerCase(Locale.ROOT)));
        }
        return ids;
    }

    /** {@code plan} as the start the platform generates: heights above the bottom, the placement's identity and fit. */
    static VanillaStructureStart start(NativeStartPlanner.Plan plan, int minHeight) {
        StructurePlacement placement = plan.placement();
        NativeStructureFit fit = new NativeStructureFit(0, null, false, placement.stilt().orElse(null), placement.resolvedTerrain());
        VanillaStructureStart start = VanillaStructureStart.at(ResourceId.parse(plan.source().structure()),
                        plan.chunkX(), plan.chunkZ(), plan.baseY() - minHeight)
                .withIdentity(StructurePlacementGrid.placementIdentity(placement))
                .withMemo(fit.memo());
        if (placement.underground()) {
            start = start.underground();
        }
        if (placement.nativeSuppression() == NativeStructureSuppression.REPLACE_SOURCE) {
            start = start.replacingPlatformStart();
        }
        return plan.source().jigsaw().map(start::withJigsaw).orElse(start);
    }
}

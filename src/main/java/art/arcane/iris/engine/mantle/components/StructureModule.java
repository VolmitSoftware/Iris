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

import art.arcane.iris.builtin.IrisStages;
import art.arcane.iris.builtin.structure.StructureSettings;
import art.arcane.iris.core.modules.IrisMantleChunk;
import art.arcane.iris.core.modules.IrisPackSettings;
import art.arcane.iris.core.modules.IrisPackWorld;
import art.arcane.iris.engine.framework.Engine;
import art.arcane.iris.engine.framework.IrisStructureLocator;
import art.arcane.iris.engine.framework.structure.StructureLocating;
import art.arcane.iris.engine.framework.structure.StructureResources;
import art.arcane.iris.engine.framework.structure.StructureValidator;
import art.arcane.iris.engine.framework.structure.StructureVanillaHook;
import com.volmit.FeatureRegistry;
import com.volmit.IrisModule;
import com.volmit.ModuleInfo;
import com.volmit.ModuleLifecycle;
import com.volmit.config.PackSettings;
import com.volmit.service.StructureLocator;
import com.volmit.stage.MantleStageContext;
import com.volmit.stage.StageGraph;
import com.volmit.world.PackWorld;

/**
 * Module {@code iris:structure}: Iris structures, written into the mantle after objects, whose trees they clear
 * where they intersect, and after floating objects. A port of what was Iris's structure mantle component, its
 * work done by {@link StructurePlacing}, one per engine runtime. Its reach is the pack's largest structure.
 * <p>
 * The module owns the pack's structures, jigsaw pools and jigsaw pieces as resource kinds
 * ({@link StructureResources}), read from the folders packs have always used, and checks them with
 * {@link StructureValidator} wherever Iris validates a pack. It lets Iris find its structures through
 * {@link StructureLocating}, and forgets a world's structure index when the world stops, which includes every reload of
 * the pack's structure files.
 * <p>
 * It decides over the platform's own structures through its vanilla structure hook ({@link StructureVanillaHook}): the
 * dimension's {@code iris:structure} settings disable, adjust and set them up, and the pack's structure placements
 * replace them, place them alongside, and start them where their {@code nativeStructures} grids say.
 */
public final class StructureModule implements IrisModule, ModuleLifecycle {
    private static final String VERSION = "built-in";

    @Override
    public ModuleInfo info() {
        return ModuleInfo.of("iris:structure", "Structures", VERSION);
    }

    @Override
    public void install(StageGraph graph) {
        graph.define(IrisStages.Structure.PLACE).requires(IrisStages.Objects.PLACE).softAfter(IrisStages.Floating.OBJECTS.ref())
                .reach(StructureModule::reach).run(StructureModule::place);
    }

    @Override
    public void register(FeatureRegistry registry) {
        registry.registerSettings(StructureSettings.class);
        StructureResources.kinds().forEach(registry::registerResourceKind);
        registry.registerValidator(new StructureValidator());
        registry.registerService(StructureLocator.class, new StructureLocating());
        registry.registerLifecycle(this);
        registry.registerVanillaStructureHook(new StructureVanillaHook());
    }

    /** The structure index is still kept per engine rather than in the world's state, so a stopping world drops it. */
    @Override
    public void worldStopped(PackWorld world) {
        IrisStructureLocator.invalidate(IrisPackWorld.of(world).engine());
    }

    private static void place(MantleStageContext context) {
        IrisMantleChunk chunk = IrisMantleChunk.current();
        placing(chunk.engine()).place(chunk.writer(), chunk.chunkX(), chunk.chunkZ(), chunk.context());
    }

    private static int reach(PackSettings settings) {
        return placing(IrisPackSettings.of(settings).engine()).reach();
    }

    private static StructurePlacing placing(Engine engine) {
        return engine.getGeneration().state(StructurePlacing.class, StructurePlacing::new);
    }
}

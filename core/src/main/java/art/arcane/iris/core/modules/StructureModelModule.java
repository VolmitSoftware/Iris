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

package art.arcane.iris.core.modules;

import art.arcane.iris.core.structure.tooling.JigsawCommands;
import art.arcane.iris.core.structure.tooling.JigsawStudioHost;
import art.arcane.iris.engine.framework.structure.JigsawStructureAssembly;
import art.arcane.iris.engine.framework.structure.StructureLocating;
import art.arcane.iris.engine.framework.structure.assembly.StructureAssembly;
import art.arcane.iris.engine.framework.structure.StructureResources;
import art.arcane.iris.engine.framework.structure.StructureValidator;
import art.arcane.iris.engine.framework.structure.StructureVanillaStructures;
import art.arcane.iris.spi.IrisServices;
import com.volmit.IrisModule;
import com.volmit.ModuleInfo;
import com.volmit.ModuleSetup;
import com.volmit.service.StructureLocator;
import com.volmit.service.VanillaStructures;

import java.util.Optional;

/**
 * Module {@code iris:structure-model}: the parts of Iris's jigsaw structures that still need Iris itself. The structures,
 * jigsaw pools and jigsaw pieces are resource kinds of {@code iris:structure} ({@link StructureResources}).
 * <p>
 * It checks them with {@link StructureValidator} wherever Iris validates a pack. It lets Iris find its structures
 * through {@link StructureLocating}; a world's structure index lives in the
 * world's state, so a reload of the pack's structure files, which starts a new world, builds it again.
 * <p>
 * It decides over the platform's own structures through its {@link VanillaStructures} provider
 * ({@link StructureVanillaStructures}): the dimension's {@code importedStructures} settings disable, move, fit and set
 * them up, and the pack's structure placements replace them, place them alongside, start them where their
 * {@code nativeStructures} grids say and fit them into the terrain.
 * <p>
 * It mounts {@code /iris module structure list|info|verify} ({@link StructureCommands}) and the Jigsaw Studio tree
 * {@code /iris module jigsaw ...} ({@link JigsawCommands}) through the tooling api.
 */
public final class StructureModelModule implements IrisModule {
    private static final String VERSION = "built-in";

    @Override
    public ModuleInfo info() {
        return ModuleInfo.of("iris:structure-model", "Structure model", VERSION);
    }

    @Override
    public void setup(ModuleSetup setup) {
        var registry = setup.registry();
        registry.registerValidator(new StructureValidator());
        registry.registerService(StructureAssembly.class, new JigsawStructureAssembly(setup.log()));
        registry.registerService(StructureLocator.class, new StructureLocating());
        registry.registerService(VanillaStructures.class, new StructureVanillaStructures());
        setup.tooling().registerCommand(StructureCommands.root());
        setup.tooling().registerCommand(JigsawCommands.root(() -> Optional.ofNullable(IrisServices.getOrNull(JigsawStudioHost.class))));
    }
}

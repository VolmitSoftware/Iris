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

package art.arcane.iris.modules.jigsaw;

import art.arcane.iris.core.structure.tooling.JigsawCommands;
import art.arcane.iris.core.structure.tooling.JigsawStudios;
import art.arcane.iris.engine.framework.structure.JigsawStructureAssembly;
import art.arcane.iris.engine.framework.structure.JigsawValidator;
import art.arcane.iris.engine.framework.structure.StructureResources;
import art.arcane.iris.engine.framework.structure.assembly.StructureAssembly;
import com.volmit.IrisModule;
import com.volmit.ModuleInfo;
import com.volmit.ModuleSetup;
import com.volmit.tooling.DatapackStructureImport;

/**
 * Module {@code iris:jigsaw}: what a structure is made of. It registers the pack's {@code structures}, {@code jigsaw-pools}
 * and {@code jigsaw-pieces} resource kinds ({@link StructureResources}), checks them ({@link JigsawValidator}), and builds
 * the pieces of the structures {@code iris:structure} places ({@link JigsawStructureAssembly}).
 * <p>
 * It brings the tools that make them: the importers of the platform's structures and of a datapack's
 * ({@link DatapackStructureImport}) and the {@code /iris module jigsaw} commands with the in-world Jigsaw Studio, which the
 * first command that needs it creates. The studio closes with its tool world, which the host closes when it stops.
 */
public final class JigsawModule implements IrisModule {
    private static final String VERSION = "1.0.0";

    @Override
    public ModuleInfo info() {
        return ModuleInfo.of("iris:jigsaw", "Jigsaw", VERSION);
    }

    @Override
    public void setup(ModuleSetup setup) {
        var registry = setup.registry();
        StructureResources.kinds().forEach(registry::registerResourceKind);
        registry.registerValidator(new JigsawValidator());
        registry.registerService(StructureAssembly.class, new JigsawStructureAssembly(setup.log()));
        registry.registerService(DatapackStructureImport.class, new JigsawDatapackImport());
        setup.tooling().registerCommand(JigsawCommands.root(new JigsawStudios()));
    }
}

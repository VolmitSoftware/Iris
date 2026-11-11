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
import art.arcane.iris.builtin.structure.StructurePlacementSettings;
import art.arcane.iris.builtin.structure.StructureSettings;
import art.arcane.iris.core.modules.StructureCommands;
import art.arcane.iris.engine.framework.structure.StructureLocating;
import art.arcane.iris.engine.framework.structure.StructureValidator;
import art.arcane.iris.engine.framework.structure.StructureVanillaStructures;
import com.volmit.IrisModule;
import com.volmit.ModuleInfo;
import com.volmit.ModuleLog;
import com.volmit.ModuleSetup;
import com.volmit.service.StructureLocator;
import com.volmit.service.VanillaStructures;
import com.volmit.stage.MantleStageContext;

/**
 * Module {@code iris:structure}: where structures go and how they meet the terrain. It owns the structure placements a
 * pack declares, the mantle stage that places the structures starting in each chunk, the locator that finds them, the
 * answers about the platform's own structures ({@link StructureVanillaStructures}: the dimension's
 * {@code importedStructures} settings disable, move, fit and set them up, and the pack's placements replace them, place
 * them alongside, start them where their {@code nativeStructures} grids say and fit them into the terrain), the checks of
 * the placements, and the {@code /iris module structure} commands.
 * <p>
 * It gets the pieces of a structure only from the {@link art.arcane.iris.engine.framework.structure.assembly.StructureAssembly}
 * providers of the pack's modules, such as {@code iris:jigsaw}, and knows none of them.
 */
public final class StructureModule implements IrisModule {
    private static final String VERSION = "1.0.0";

    @Override
    public ModuleInfo info() {
        return ModuleInfo.of("iris:structure", "Structures", VERSION);
    }

    @Override
    public void setup(ModuleSetup setup) {
        ModuleLog log = setup.log();
        setup.stages().define(IrisStages.Structure.PLACE).softAfter(IrisStages.Objects.PLACE, IrisStages.Floating.OBJECTS)
                .reach(StructurePlacing::reach).run(context -> place(context, log));
        var registry = setup.registry();
        registry.registerSettings(StructureSettings.class);
        registry.registerSettings(StructurePlacementSettings.class);
        registry.registerValidator(new StructureValidator());
        registry.registerService(StructureLocator.class, new StructureLocating());
        registry.registerService(VanillaStructures.class, new StructureVanillaStructures());
        setup.tooling().registerCommand(StructureCommands.root());
    }

    private static void place(MantleStageContext context, ModuleLog log) {
        context.world().state(StructurePlacing.class, generating -> new StructurePlacing(generating, log)).place(context);
    }
}

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

import art.arcane.iris.builtin.blocks.ObjectReplace;
import art.arcane.iris.builtin.prepare.AxisRotationClamp;
import art.arcane.iris.builtin.prepare.SlopeClip;
import art.arcane.iris.builtin.stilt.StiltSettings;
import art.arcane.iris.builtin.vacuum.VacuumSettings;
import art.arcane.iris.builtin.validate.ObjectLimit;
import com.volmit.FeatureRegistry;
import com.volmit.IrisModule;
import com.volmit.ModuleInfo;
import com.volmit.stage.StageGraph;

/**
 * Module {@code iris:placement-settings}: describes the classes an object placement's settings hold, which Iris's own
 * {@code IrisObjectPlacement} holds as fields. Only a module compiled into Iris may describe such a class, so the
 * placement stages, which ship in the built-ins jar, cannot. It has no stages and goes when the placement settings move
 * into a module and nothing of Iris's holds these classes.
 */
public final class PlacementSettingsModule implements IrisModule {
    private static final String VERSION = "built-in";

    @Override
    public ModuleInfo info() {
        return ModuleInfo.of("iris:placement-settings", "Placement settings", VERSION);
    }

    @Override
    public void install(StageGraph graph) {
    }

    @Override
    public void register(FeatureRegistry registry) {
        registry.registerHeldType(SlopeClip.class);
        registry.registerHeldType(AxisRotationClamp.class);
        registry.registerHeldType(ObjectLimit.class);
        registry.registerHeldType(StiltSettings.class);
        registry.registerHeldType(VacuumSettings.class);
        registry.registerHeldType(ObjectReplace.class);
    }
}

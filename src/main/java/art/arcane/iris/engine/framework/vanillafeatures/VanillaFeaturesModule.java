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

package art.arcane.iris.engine.framework.vanillafeatures;

import art.arcane.iris.builtin.vanillafeatures.ImportedFeaturesSettings;
import com.volmit.IrisModule;
import com.volmit.ModuleInfo;
import com.volmit.ModuleSetup;
import com.volmit.service.VanillaFeatures;

/**
 * Module {@code iris:vanilla-features}: whether the platform's own placed features (ores, trees, plants, springs) run
 * over a dimension's terrain, and which steps and features they leave out, decided from the dimension's
 * {@code importedFeatures} settings and answered through {@link VanillaFeatures} ({@link ImportedFeatures}). The
 * platform runs the feature pass itself; the module has no stages.
 */
public final class VanillaFeaturesModule implements IrisModule {
    private static final String VERSION = "built-in";

    @Override
    public ModuleInfo info() {
        return ModuleInfo.of("iris:vanilla-features", "Vanilla features", VERSION);
    }

    @Override
    public void setup(ModuleSetup setup) {
        var registry = setup.registry();
        registry.registerSettings(ImportedFeaturesSettings.class);
        registry.registerService(VanillaFeatures.class, new ImportedFeatures());
    }
}

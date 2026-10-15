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

import art.arcane.iris.builtin.RegistryKeyFamily;
import art.arcane.iris.builtin.vanillafeatures.ImportedFeaturesSettings;
import com.volmit.config.FeatureSettings.Entry;
import com.volmit.resource.ResourceId;
import com.volmit.service.VanillaFeatures;
import com.volmit.vanilla.DecorationStep;
import com.volmit.world.PackWorld;

import java.util.List;
import java.util.Optional;

/**
 * What a dimension's {@link ImportedFeaturesSettings} say about the platform's own placed features: whether they run,
 * and which decoration steps and features they leave out. A dimension without the settings runs none.
 */
public final class ImportedFeatures implements VanillaFeatures {
    private static final ImportedFeaturesSettings NONE = new ImportedFeaturesSettings();

    @Override
    public boolean generates(PackWorld world) {
        return settings(world).enabled();
    }

    /** A step the API does not name is allowed unless the dimension narrowed generation to listed steps. */
    @Override
    public boolean generatesStep(PackWorld world, Optional<DecorationStep> step) {
        ImportedFeaturesSettings settings = settings(world);
        if (step.isEmpty()) {
            return settings.steps().isEmpty();
        }
        if (!settings.steps().isEmpty() && !settings.steps().contains(step.get())) {
            return false;
        }
        return !settings.disabledSteps().contains(step.get());
    }

    @Override
    public boolean generatesFeature(PackWorld world, ResourceId feature) {
        String key = feature.toString();
        return settings(world).disabled().stream().noneMatch(pattern -> RegistryKeyFamily.matches(pattern, key));
    }

    private static ImportedFeaturesSettings settings(PackWorld world) {
        List<Entry<ImportedFeaturesSettings>> declared = world.settings().declared(ImportedFeaturesSettings.class);
        return declared.isEmpty() ? NONE : declared.getFirst().value();
    }
}

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

import com.volmit.resource.ResourceId;
import com.volmit.service.VanillaFeatures;
import com.volmit.vanilla.DecorationStep;
import com.volmit.world.PackWorld;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Predicate;

/**
 * What the world's {@link VanillaFeatures} providers say, kept for the world: whether the platform's placed features run
 * over its terrain, and which decoration steps and features they leave out. Each answer is asked once per world.
 */
public final class VanillaFeatureAnswers {
    private static final DecorationStep[] STEPS = DecorationStep.values();

    /** One provider and the world as its module sees it. */
    record Provider(VanillaFeatures service, PackWorld world) {
    }

    private final List<Provider> providers;
    private final boolean generates;
    private final Map<Integer, Boolean> steps = new ConcurrentHashMap<>();
    private final Map<String, Boolean> features = new ConcurrentHashMap<>();

    VanillaFeatureAnswers(List<Provider> providers) {
        this.providers = List.copyOf(providers);
        this.generates = this.providers.stream().anyMatch(provider -> provider.service().generates(provider.world()));
    }

    /** Whether the platform runs its placed features over the world's terrain: some module turned them on. */
    public boolean generates() {
        return generates;
    }

    /**
     * Whether the platform places the features of its decoration step {@code stepIndex}, counted in the platform's order
     * (a step past the ones IrisApi names is asked as an unknown step). False while the features are off.
     */
    public boolean generatesStep(int stepIndex) {
        if (!generates) {
            return false;
        }
        return steps.computeIfAbsent(stepIndex, index -> everyProviderAllows(provider ->
                provider.service().generatesStep(provider.world(), stepAt(index))));
    }

    /** Whether the platform places the feature {@code featureKey}; false while the features are off and for a key that is no registry key. */
    public boolean generatesFeature(String featureKey) {
        if (!generates || featureKey == null || featureKey.isBlank()) {
            return false;
        }
        Boolean known = features.get(featureKey);
        if (known != null) {
            return known;
        }
        return features.computeIfAbsent(featureKey, key -> registryId(key)
                .map(feature -> everyProviderAllows(provider -> provider.service().generatesFeature(provider.world(), feature)))
                .orElse(false));
    }

    private boolean everyProviderAllows(Predicate<Provider> allows) {
        return providers.stream().allMatch(allows);
    }

    private static Optional<DecorationStep> stepAt(int index) {
        return index >= 0 && index < STEPS.length ? Optional.of(STEPS[index]) : Optional.empty();
    }

    private static Optional<ResourceId> registryId(String key) {
        try {
            return Optional.of(ResourceId.parse(key.trim()));
        } catch (IllegalArgumentException notAnId) {
            return Optional.empty();
        }
    }
}

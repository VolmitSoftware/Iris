/*
 * Iris is a World Generator for Minecraft Bukkit Servers
 * Copyright (c) 2022 Arcane Arts (Volmit Software)
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

package art.arcane.iris.command.specialhandlers;

import art.arcane.iris.generation.runtime.Engine;
import art.arcane.iris.structure.placement.IrisStructureLocator;
import art.arcane.iris.structure.nativegen.NativeStructureGenerationPolicy;
import art.arcane.iris.structure.placement.StructureReachability;
import art.arcane.iris.world.history.GenerationFindCatalog;
import art.arcane.iris.structure.nativegen.IrisNativeStructureDecision;
import art.arcane.iris.structure.nativegen.NativeStructureGenerationStatus;
import art.arcane.iris.spi.IrisPlatforms;
import art.arcane.iris.spi.PlatformStructureHooks;
import art.arcane.iris.command.DirectorParameterHandler;
import art.arcane.volmlib.util.collection.KList;
import art.arcane.volmlib.util.director.exceptions.DirectorParsingException;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

public class StructureHandler implements DirectorParameterHandler<String> {
    @Override
    public KList<String> getPossibilities() {
        Engine activeEngine = engine();
        if (activeEngine == null) {
            return new KList<>();
        }

        boolean nativeGenerationEnabled = nativeStructureGenerationEnabled();
        PlatformStructureHooks structureHooks = IrisPlatforms.get().structureHooks();
        Map<String, String> registeredKeys = distinctKeys(structureHooks.structureKeys());
        Set<String> reachableKeys = StructureReachability.reachableKeys(activeEngine);
        Map<String, String> suggestions = new LinkedHashMap<>();

        for (Map.Entry<String, String> entry : registeredKeys.entrySet()) {
            if (isEligibleRegisteredKey(activeEngine, entry.getValue(), entry.getKey(), reachableKeys,
                    nativeGenerationEnabled)) {
                suggestions.put(entry.getKey(), entry.getValue());
            }
        }

        Set<String> locatableKeys = IrisStructureLocator.locatableEditableKeys(activeEngine);
        for (String key : locatableKeys) {
            String normalizedKey = normalizeKey(key);
            if (!normalizedKey.isEmpty()
                    && !registeredKeys.containsKey(normalizedKey)
                    && !IrisStructureLocator.hasNativePlacement(activeEngine, key)) {
                suggestions.putIfAbsent(normalizedKey, key.trim());
            }
        }

        for (String key : GenerationFindCatalog.retainedStructureKeys(activeEngine)) {
            suggestions.putIfAbsent(normalizeKey(key), key);
        }
        return new KList<>(suggestions.values());
    }

    @Override
    public String toString(String structure) {
        return structure;
    }

    @Override
    public String parse(String in, boolean force) throws DirectorParsingException {
        KList<String> options = getPossibilities(in);
        for (String option : options) {
            if (option.equalsIgnoreCase(in)) {
                return option;
            }
        }
        return in;
    }

    @Override
    public boolean supports(Class<?> type) {
        return type.equals(String.class);
    }

    @Override
    public String getRandomDefault() {
        String f = getPossibilities().getRandom();

        return f == null ? "minecraft_ancient_city" : f;
    }

    protected boolean nativeStructureGenerationEnabled() {
        return playerWorldGeneratesStructures();
    }

    private static Map<String, String> distinctKeys(List<String> keys) {
        Map<String, String> distinct = new LinkedHashMap<>();
        for (String key : keys) {
            String normalizedKey = normalizeKey(key);
            if (!normalizedKey.isEmpty()) {
                distinct.putIfAbsent(normalizedKey, key.trim());
            }
        }
        return distinct;
    }

    private static boolean isEligibleRegisteredKey(Engine engine, String key, String normalizedKey,
                                                   Set<String> reachableKeys, boolean nativeGenerationEnabled) {
        IrisNativeStructureDecision decision = NativeStructureGenerationPolicy.resolve(engine, key, false);
        boolean nativePlacement = IrisStructureLocator.hasNativePlacement(engine, key);
        boolean locatableNativePlacement = nativePlacement
                && IrisStructureLocator.hasLocatableNativePlacement(engine, key);
        boolean locatableEditableReplacement = !nativePlacement
                && decision.status() == NativeStructureGenerationStatus.REPLACED_BY_IRIS
                && IrisStructureLocator.hasLocatableEditablePlacement(engine, key);
        return isEligibleRegisteredKey(
                decision, nativePlacement, locatableNativePlacement, locatableEditableReplacement,
                reachableKeys.contains(normalizedKey), nativeGenerationEnabled);
    }

    static boolean isEligibleRegisteredKey(IrisNativeStructureDecision decision, boolean nativePlacement,
                                           boolean locatableNativePlacement,
                                           boolean locatableEditableReplacement,
                                           boolean reachable, boolean nativeGenerationEnabled) {
        if (decision.status() == NativeStructureGenerationStatus.REPLACED_BY_IRIS) {
            return locatableEditableReplacement
                    || nativeGenerationEnabled && nativePlacement && locatableNativePlacement;
        }
        return nativeGenerationEnabled && decision.generate()
                && (reachable || (nativePlacement && locatableNativePlacement));
    }

    private static String normalizeKey(String key) {
        return key == null ? "" : key.trim().toLowerCase(Locale.ROOT);
    }
}

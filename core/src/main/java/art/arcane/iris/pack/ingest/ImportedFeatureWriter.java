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

package art.arcane.iris.pack.ingest;

import art.arcane.iris.generation.decoration.IrisDecorationStep;
import art.arcane.iris.structure.nativegen.IrisImportedFeatureControl;
import art.arcane.iris.structure.nativegen.NativeFeatureGenerationStatus;
import art.arcane.volmlib.util.collection.KList;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

final class ImportedFeatureWriter {
    static final String VANILLA_PREFIX = "minecraft:";

    private ImportedFeatureWriter() {
    }

    static IrisImportedFeatureControl write(JsonObject dimension, Set<String> nativeKeys, Set<String> unselectedNamespaces) {
        JsonElement existing = dimension.get("importedFeatures");
        JsonObject imported = existing != null && existing.isJsonObject() ? existing.getAsJsonObject() : new JsonObject();
        Set<String> disabled = new LinkedHashSet<>(IngestJson.strings(imported.get("disabled")));
        if (!enabled(imported)) {
            disabled.add(VANILLA_PREFIX);
            for (String namespace : unselectedNamespaces) {
                disabled.add(namespace + ":");
            }
        }
        Set<String> disabledExact = new LinkedHashSet<>(IngestJson.strings(imported.get("disabledExact")));
        disabledExact.addAll(nativeKeys);
        imported.addProperty("enabled", true);
        imported.add("disabled", IngestJson.stringArray(disabled));
        imported.add("disabledExact", IngestJson.stringArray(disabledExact));
        dimension.add("importedFeatures", imported);
        IrisImportedFeatureControl control = new IrisImportedFeatureControl();
        control.setEnabled(true);
        control.setDisabled(new KList<>(disabled));
        control.setDisabledExact(new KList<>(disabledExact));
        control.setSteps(steps(imported.get("steps")));
        control.setDisabledSteps(steps(imported.get("disabledSteps")));
        return control;
    }

    static List<ModIngestItem> applySuppression(List<ModIngestItem> items, Map<String, ModFeatureIngester.FeaturePlan> plans,
                                                IrisImportedFeatureControl control) {
        List<ModIngestItem> result = new ArrayList<>(items.size());
        for (ModIngestItem item : items) {
            ModFeatureIngester.FeaturePlan plan = plans.get(item.sourceKey());
            if (item.tier() != ModIngestTier.PASSTHROUGH || plan == null) {
                result.add(item);
                continue;
            }
            String suppression = suppression(item.sourceKey(), plan.steps(), control);
            result.add(suppression == null
                    ? item
                    : new ModIngestItem(item.namespace(), item.sourceKey(), item.kind(), ModIngestTier.SKIPPED, "", suppression));
        }
        return result;
    }

    private static String suppression(String key, Set<Integer> steps, IrisImportedFeatureControl control) {
        NativeFeatureGenerationStatus status = null;
        for (int step : steps) {
            status = control.resolve(key, IrisDecorationStep.byOrdinal(step));
            if (status == NativeFeatureGenerationStatus.GENERATE_NATIVE) {
                return null;
            }
        }
        if (status == NativeFeatureGenerationStatus.STEP_DISABLED) {
            return "its decoration step is disabled by importedFeatures";
        }
        for (String entry : control.getDisabled()) {
            IrisImportedFeatureControl single = new IrisImportedFeatureControl();
            single.setEnabled(true);
            single.setDisabled(new KList<>(entry));
            if (!single.shouldGenerate(key)) {
                return "suppressed by importedFeatures.disabled entry " + entry;
            }
        }
        for (String entry : control.getDisabledExact()) {
            if (entry.trim().equalsIgnoreCase(key.trim())) {
                return "suppressed by importedFeatures.disabledExact entry " + entry;
            }
        }
        return "suppressed by importedFeatures";
    }

    private static boolean enabled(JsonObject imported) {
        JsonElement value = imported.get("enabled");
        return value != null && value.isJsonPrimitive() && value.getAsJsonPrimitive().isBoolean() && value.getAsBoolean();
    }

    private static KList<IrisDecorationStep> steps(JsonElement value) {
        KList<IrisDecorationStep> steps = new KList<>();
        for (String name : IngestJson.strings(value)) {
            IrisDecorationStep step = IrisDecorationStep.byKey(name);
            if (step != null) {
                steps.add(step);
            }
        }
        return steps;
    }
}

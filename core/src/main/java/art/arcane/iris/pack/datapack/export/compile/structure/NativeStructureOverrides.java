package art.arcane.iris.pack.datapack.export.compile.structure;

import art.arcane.iris.pack.datapack.export.compile.PackCompileContext;
import art.arcane.iris.pack.datapack.export.ir.ExportDiagnostic;
import art.arcane.iris.pack.datapack.export.ir.ExportLimit;
import art.arcane.iris.pack.datapack.export.ir.JsonResource;
import art.arcane.iris.structure.nativegen.IrisImportedStructureControl;
import art.arcane.iris.structure.nativegen.IrisNativeStructureDecision;
import art.arcane.volmlib.util.structure.StructureTerrainMode;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.SortedMap;
import java.util.SortedSet;
import java.util.TreeMap;
import java.util.TreeSet;

final class NativeStructureOverrides {
    static final String DISABLED_TAG = "structure/disabled";
    private static final int LISTED_KEYS = 8;

    private final PackCompileContext context;
    private final DatapackBundle bundle;
    private final StructureDefinitions definitions;
    private final IrisImportedStructureControl control;
    private final SortedMap<String, SortedSet<String>> dropped = new TreeMap<>();

    NativeStructureOverrides(PackCompileContext context, DatapackBundle bundle, StructureDefinitions definitions) {
        this.context = context;
        this.bundle = bundle;
        this.definitions = definitions;
        IrisImportedStructureControl configured = context.dimension().getImportedStructures();
        control = configured == null ? new IrisImportedStructureControl() : configured;
    }

    void apply(Set<String> suppressed) {
        boolean adjusting = !control.getAdjustments().isEmpty() || control.getUndergroundYShift() != 0;
        boolean disabledAny = false;
        for (String key : definitions.keys()) {
            boolean disabled = !control.shouldGenerate(key) || suppressed.contains(key);
            if (!disabled && !adjusting) {
                continue;
            }
            Optional<JsonObject> source = definitions.read(key);
            if (source.isEmpty()) {
                continue;
            }
            JsonObject structure = source.get().deepCopy();
            if (disabled) {
                StructureJson.scope(structure, context.namespace() + ":" + DISABLED_TAG);
                override(key, structure);
                disabledAny = true;
            } else if (adjust(key, structure)) {
                override(key, structure);
            }
        }
        if (disabledAny) {
            JsonObject empty = new JsonObject();
            empty.addProperty("replace", false);
            empty.add("values", new JsonArray());
            context.jsonResources().add(new JsonResource("data/" + context.namespace() + "/tags/worldgen/biome/" + DISABLED_TAG + ".json", empty));
        }
        reportDropped();
        if (!definitions.vanillaAvailable() && (configuresVanilla(suppressed) || adjusting)) {
            context.diagnostics().add(new ExportDiagnostic(ExportDiagnostic.Severity.WARNING, ExportLimit.VANILLA_STRUCTURE_DATA,
                    "dimensions/" + context.dimension().getLoadKey(),
                    "Vanilla structure data for Minecraft " + context.capabilities().target().getVersion()
                            + " is not available on this server; disabled structures and structure adjustments apply only to structures from the bundled datapacks"));
        }
    }

    private boolean adjust(String key, JsonObject structure) {
        IrisNativeStructureDecision decision = control.resolve(key, StructureJson.underground(structure));
        boolean changed = false;
        if (decision.yShift() != 0) {
            if (StructureJson.jigsaw(structure) && StructureJson.shiftStartHeight(structure, decision.yShift())) {
                changed = true;
            } else {
                drop("vertical shift", key);
            }
        }
        if (decision.yBand() != null) {
            drop("Y band", key);
        }
        if (decision.stilt() != null) {
            drop("stilt foundations", key);
        }
        StructureTerrainMode terrain = decision.terrain() == null ? StructureTerrainMode.SOURCE : decision.terrain().resolvedMode();
        if (terrain == StructureTerrainMode.PRESERVE) {
            structure.addProperty("terrain_adaptation", "none");
            changed = true;
        } else if (terrain != StructureTerrainMode.SOURCE) {
            drop("terrain mode " + terrain.name(), key);
        }
        return changed;
    }

    private void drop(String adjustment, String key) {
        dropped.computeIfAbsent(adjustment, ignored -> new TreeSet<>()).add(key);
    }

    private void reportDropped() {
        for (Map.Entry<String, SortedSet<String>> entry : dropped.entrySet()) {
            SortedSet<String> keys = entry.getValue();
            String listed = String.join(", ", keys.stream().limit(LISTED_KEYS).toList()) + (keys.size() > LISTED_KEYS ? ", ..." : "");
            context.diagnostics().add(new ExportDiagnostic(ExportDiagnostic.Severity.WARNING, ExportLimit.STRUCTURE_ADJUSTMENT, keys.first(),
                    "The structure adjustment '" + entry.getKey() + "' has no vanilla equivalent and is not exported for "
                            + keys.size() + " structure(s): " + listed));
        }
    }

    private void override(String key, JsonObject structure) {
        String path = StructureDefinitions.path(key);
        bundle.files().remove(path);
        context.jsonResources().add(new JsonResource(path, structure));
    }

    private boolean configuresVanilla(Set<String> suppressed) {
        return !control.getDisabled().isEmpty() || !control.getDisabledExact().isEmpty() || !suppressed.isEmpty();
    }
}

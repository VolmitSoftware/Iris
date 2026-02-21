package art.arcane.iris.engine.object;

import art.arcane.iris.engine.object.annotations.ArrayType;
import art.arcane.iris.engine.object.annotations.Desc;
import art.arcane.volmlib.util.collection.KList;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.Accessors;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Accessors(chain = true)
@Desc("Explicit minecraft namespace targets that may be replaced by an external datapack")
public class IrisExternalDatapackReplaceTargets {
    @ArrayType(type = String.class, min = 1)
    @Desc("Structure ids that may be replaced when replaceVanilla is enabled")
    private KList<String> structures = new KList<>();

    @ArrayType(type = String.class, min = 1)
    @Desc("Structure set ids that may be replaced when replaceVanilla is enabled")
    private KList<String> structureSets = new KList<>();

    @ArrayType(type = String.class, min = 1)
    @Desc("Template pool ids that may be replaced when replaceVanilla is enabled")
    private KList<String> templatePools = new KList<>();

    @ArrayType(type = String.class, min = 1)
    @Desc("Processor list ids that may be replaced when replaceVanilla is enabled")
    private KList<String> processorLists = new KList<>();

    @ArrayType(type = String.class, min = 1)
    @Desc("Biome has_structure tag ids that may be replaced when replaceVanilla is enabled")
    private KList<String> biomeHasStructureTags = new KList<>();

    @ArrayType(type = String.class, min = 1)
    @Desc("Configured feature ids that may be replaced when replaceVanilla is enabled")
    private KList<String> configuredFeatures = new KList<>();

    @ArrayType(type = String.class, min = 1)
    @Desc("Placed feature ids that may be replaced when replaceVanilla is enabled")
    private KList<String> placedFeatures = new KList<>();

    public boolean hasAnyTargets() {
        return !structures.isEmpty()
                || !structureSets.isEmpty()
                || !templatePools.isEmpty()
                || !processorLists.isEmpty()
                || !biomeHasStructureTags.isEmpty()
                || !configuredFeatures.isEmpty()
                || !placedFeatures.isEmpty();
    }
}

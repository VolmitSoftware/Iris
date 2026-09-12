package art.arcane.iris.structure.export;

import art.arcane.iris.pack.loading.IrisData;
import art.arcane.iris.structure.graph.StructureGraphResolver;
import art.arcane.iris.structure.placement.IrisStructure;

import java.util.Objects;

public final class VanillaJigsawExportSource {
    private final String structureKey;
    private final IrisData data;
    private final IrisStructure structure;
    private final StructureGraphResolver resolver;

    private VanillaJigsawExportSource(
            String structureKey,
            IrisData data,
            IrisStructure structure,
            StructureGraphResolver resolver
    ) {
        this.structureKey = requireKey(structureKey);
        this.data = data;
        this.structure = structure;
        this.resolver = resolver;
    }

    public static VanillaJigsawExportSource forData(IrisData data, String structureKey) {
        return new VanillaJigsawExportSource(
                structureKey,
                Objects.requireNonNull(data),
                null,
                StructureGraphResolver.forData(data));
    }

    public static VanillaJigsawExportSource forStructure(
            String structureKey,
            IrisStructure structure,
            StructureGraphResolver resolver
    ) {
        return new VanillaJigsawExportSource(
                structureKey,
                null,
                Objects.requireNonNull(structure),
                Objects.requireNonNull(resolver));
    }

    public String structureKey() {
        return structureKey;
    }

    public IrisStructure loadStructure() {
        if (structure != null) {
            return structure;
        }
        return data.load(IrisStructure.class, structureKey, false);
    }

    public StructureGraphResolver resolver() {
        return resolver;
    }

    private static String requireKey(String value) {
        String key = Objects.requireNonNull(value).trim();
        if (key.isEmpty()) {
            throw new IllegalArgumentException("Structure key must not be blank");
        }
        return key;
    }
}

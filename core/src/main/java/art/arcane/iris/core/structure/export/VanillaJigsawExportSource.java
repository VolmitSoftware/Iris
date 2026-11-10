package art.arcane.iris.core.structure.export;

import art.arcane.iris.engine.framework.structure.StructureGraphResolver;
import art.arcane.iris.engine.framework.structure.StructureResources;
import art.arcane.iris.engine.object.structure.IrisStructure;
import com.volmit.resource.PackResources;

import java.util.Objects;
import java.util.function.Function;

public final class VanillaJigsawExportSource {
    private final String structureKey;
    private final PackResources pack;
    private final IrisStructure structure;
    private final StructureGraphResolver resolver;
    private final Function<String, ObjectContents> objects;

    private VanillaJigsawExportSource(
            String structureKey,
            PackResources pack,
            IrisStructure structure,
            StructureGraphResolver resolver,
            Function<String, ObjectContents> objects
    ) {
        this.structureKey = requireKey(structureKey);
        this.pack = pack;
        this.structure = structure;
        this.resolver = resolver;
        this.objects = objects;
    }

    public static VanillaJigsawExportSource forPack(PackResources pack, String structureKey) {
        Objects.requireNonNull(pack);
        return new VanillaJigsawExportSource(
                structureKey,
                pack,
                null,
                StructureGraphResolver.forPack(pack),
                key -> pack.objects().object(pack.idOf(key)).map(object -> ObjectContents.of(object, pack.blocks())).orElse(null));
    }

    /** A source of {@code structure} whose pieces' objects {@code objects} reads, for a structure that is not in a pack. */
    public static VanillaJigsawExportSource forStructure(
            String structureKey,
            IrisStructure structure,
            StructureGraphResolver resolver,
            Function<String, ObjectContents> objects
    ) {
        return new VanillaJigsawExportSource(
                structureKey,
                null,
                Objects.requireNonNull(structure),
                Objects.requireNonNull(resolver),
                Objects.requireNonNull(objects));
    }

    public String structureKey() {
        return structureKey;
    }

    public IrisStructure loadStructure() {
        if (structure != null) {
            return structure;
        }
        return StructureResources.structure(pack, structureKey);
    }

    public StructureGraphResolver resolver() {
        return resolver;
    }

    /** What the object the pack key {@code objectKey} names holds; null when there is none. */
    public ObjectContents contents(String objectKey) {
        return objects.apply(objectKey);
    }

    private static String requireKey(String value) {
        String key = Objects.requireNonNull(value).trim();
        if (key.isEmpty()) {
            throw new IllegalArgumentException("Structure key must not be blank");
        }
        return key;
    }
}

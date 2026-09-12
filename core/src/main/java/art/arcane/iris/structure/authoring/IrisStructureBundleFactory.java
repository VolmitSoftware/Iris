package art.arcane.iris.structure.authoring;

import art.arcane.iris.structure.object.IrisObject;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

public final class IrisStructureBundleFactory {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    private IrisStructureBundleFactory() {
    }

    public static StructureResourceBundle singlePiece(SinglePieceOptions options) throws IOException {
        SinglePieceOptions activeOptions = Objects.requireNonNull(options);
        StructureResourceBundle.Builder bundle = StructureResourceBundle.builder(activeOptions.bundleKey())
                .source(activeOptions.source())
                .backend(StructureBackend.SNAPSHOT)
                .capabilities(activeOptions.capabilities())
                .losses(activeOptions.losses())
                .resource("objects/" + activeOptions.resourceKey() + ".iob", serialize(activeOptions.object()))
                .textResource("jigsaw-pieces/" + activeOptions.resourceKey() + ".json",
                        GSON.toJson(pieceJson(activeOptions.resourceKey())));
        if (!activeOptions.objectOnly()) {
            bundle.capability(StructureCapability.IRIS_PLACEMENT)
                    .textResource("jigsaw-pools/" + activeOptions.resourceKey() + ".json",
                            GSON.toJson(poolJson(activeOptions.resourceKey())))
                    .textResource("structures/" + activeOptions.resourceKey() + ".json",
                            GSON.toJson(structureJson(activeOptions)));
        }
        return bundle.build();
    }

    private static byte[] serialize(IrisObject object) throws IOException {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        object.write(output);
        return output.toByteArray();
    }

    private static Map<String, Object> pieceJson(String resourceKey) {
        Map<String, Object> piece = new LinkedHashMap<>();
        piece.put("object", resourceKey);
        piece.put("connectors", new ArrayList<>());
        piece.put("rotatable", true);
        return piece;
    }

    private static Map<String, Object> poolJson(String resourceKey) {
        Map<String, Object> entry = new LinkedHashMap<>();
        entry.put("piece", resourceKey);
        entry.put("weight", 1);
        List<Object> pieces = new ArrayList<>();
        pieces.add(entry);
        Map<String, Object> pool = new LinkedHashMap<>();
        pool.put("pieces", pieces);
        return pool;
    }

    private static Map<String, Object> structureJson(SinglePieceOptions options) {
        Map<String, Object> structure = new LinkedHashMap<>();
        structure.put("startPool", options.resourceKey());
        structure.put("maxDepth", 1);
        structure.put("maxSizeChunks", Math.max(1, (options.maxSpan() / 16) + 1));
        structure.put("placeMode", options.placeMode());
        structure.put("vanillaSource", options.source().key().value());
        return structure;
    }

    public record SinglePieceOptions(
            StructureKey bundleKey,
            StructureSource source,
            String resourceKey,
            IrisObject object,
            int maxSpan,
            String placeMode,
            boolean objectOnly,
            Collection<StructureCapability> capabilities,
            Collection<StructureLoss> losses
    ) {
        public SinglePieceOptions {
            Objects.requireNonNull(bundleKey);
            Objects.requireNonNull(source);
            Objects.requireNonNull(resourceKey);
            Objects.requireNonNull(object);
            Objects.requireNonNull(placeMode);
            Objects.requireNonNull(capabilities);
            Objects.requireNonNull(losses);
            StructureResourceBundle.validateRelativePath("structures/" + resourceKey + ".json");
            if (maxSpan < 1) {
                throw new IllegalArgumentException("Structure span must be positive");
            }
            if (placeMode.isBlank()) {
                throw new IllegalArgumentException("Structure place mode cannot be blank");
            }
            capabilities = List.copyOf(capabilities);
            losses = List.copyOf(losses);
        }
    }
}

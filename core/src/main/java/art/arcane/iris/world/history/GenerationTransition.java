package art.arcane.iris.world.history;

import com.google.gson.JsonNull;
import com.google.gson.JsonObject;

import java.util.Objects;
import java.util.regex.Pattern;

public record GenerationTransition(
        int algorithmVersion,
        int widthBlocks,
        String boundaryIdentity,
        String terrainSignatureIdentity
) {
    public static final int CURRENT_ALGORITHM_VERSION = 1;
    public static final int MINIMUM_WIDTH_BLOCKS = 16;
    public static final int MAXIMUM_WIDTH_BLOCKS = 16_384;

    private static final Pattern SHA_256 = Pattern.compile("[0-9a-f]{64}");

    public GenerationTransition {
        if (algorithmVersion < 1) {
            throw new IllegalArgumentException("Transition algorithm version must be positive.");
        }
        if (widthBlocks < MINIMUM_WIDTH_BLOCKS || widthBlocks > MAXIMUM_WIDTH_BLOCKS) {
            throw new IllegalArgumentException("Transition width must be between "
                    + MINIMUM_WIDTH_BLOCKS + " and " + MAXIMUM_WIDTH_BLOCKS + " blocks.");
        }
        if ((boundaryIdentity == null) != (terrainSignatureIdentity == null)) {
            throw new IllegalArgumentException("Transition snapshot identities must be completed together.");
        }
        if (boundaryIdentity != null) {
            boundaryIdentity = requireIdentity(boundaryIdentity, "Boundary identity");
            terrainSignatureIdentity = requireIdentity(
                    terrainSignatureIdentity,
                    "Terrain signature identity"
            );
        }
    }

    public static GenerationTransition pending(int widthBlocks) {
        return new GenerationTransition(CURRENT_ALGORITHM_VERSION, widthBlocks, null, null);
    }

    public boolean isComplete() {
        return boundaryIdentity != null;
    }

    public GenerationTransition complete(String boundaryIdentity, String terrainSignatureIdentity) {
        if (isComplete()) {
            if (this.boundaryIdentity.equals(boundaryIdentity)
                    && this.terrainSignatureIdentity.equals(terrainSignatureIdentity)) {
                return this;
            }
            throw new IllegalStateException("Generation transition snapshots are already immutable.");
        }
        return new GenerationTransition(
                algorithmVersion,
                widthBlocks,
                boundaryIdentity,
                terrainSignatureIdentity
        );
    }

    JsonObject toJson() {
        JsonObject json = new JsonObject();
        json.addProperty("algorithmVersion", algorithmVersion);
        json.addProperty("widthBlocks", widthBlocks);
        if (boundaryIdentity == null) {
            json.add("boundaryIdentity", JsonNull.INSTANCE);
            json.add("terrainSignatureIdentity", JsonNull.INSTANCE);
        } else {
            json.addProperty("boundaryIdentity", boundaryIdentity);
            json.addProperty("terrainSignatureIdentity", terrainSignatureIdentity);
        }
        return json;
    }

    static GenerationTransition fromJson(JsonObject json) {
        GenerationManifest.JsonSchema.requireFields(
                json,
                "generation transition",
                "algorithmVersion",
                "widthBlocks",
                "boundaryIdentity",
                "terrainSignatureIdentity"
        );
        int algorithmVersion = GenerationManifest.JsonSchema.requireInt(
                json,
                "algorithmVersion",
                "generation transition"
        );
        int widthBlocks = GenerationManifest.JsonSchema.requireInt(
                json,
                "widthBlocks",
                "generation transition"
        );
        String boundaryIdentity = GenerationManifest.JsonSchema.requireNullableString(
                json,
                "boundaryIdentity",
                "generation transition"
        );
        String terrainSignatureIdentity = GenerationManifest.JsonSchema.requireNullableString(
                json,
                "terrainSignatureIdentity",
                "generation transition"
        );
        return new GenerationTransition(
                algorithmVersion,
                widthBlocks,
                boundaryIdentity,
                terrainSignatureIdentity
        );
    }

    private static String requireIdentity(String value, String label) {
        String requiredValue = Objects.requireNonNull(value, label);
        if (!SHA_256.matcher(requiredValue).matches()) {
            throw new IllegalArgumentException(label + " must be a lowercase SHA-256 value.");
        }
        return requiredValue;
    }
}

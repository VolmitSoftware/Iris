package art.arcane.iris.world.history;

import art.arcane.iris.pack.loading.IrisData;
import art.arcane.iris.platform.bukkit.nms.datapack.IDataFixer;
import art.arcane.iris.generation.terrain.IrisDimension;
import art.arcane.iris.generation.terrain.IrisDimensionMode;
import art.arcane.iris.generation.terrain.IrisDimensionModeType;
import art.arcane.iris.generation.terrain.IrisDimensionType;
import art.arcane.iris.generation.terrain.IrisDimensionTypeOptions;
import art.arcane.iris.world.IrisEnvironment;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Locale;
import java.util.Objects;

public final class GenerationEpochContractFactory {
    public static final int DIMENSION_TYPE_FINGERPRINT_SCHEMA_VERSION_ONE = 1;
    public static final int CURRENT_DIMENSION_TYPE_FINGERPRINT_SCHEMA =
            DIMENSION_TYPE_FINGERPRINT_SCHEMA_VERSION_ONE;

    private static final String FINGERPRINT_DOMAIN = "iris-dimension-type-authored-semantics";
    private static final String NO_UPPER_TERRAIN_FINGERPRINT = "0".repeat(64);
    private static final Gson SEMANTIC_JSON = new GsonBuilder().serializeNulls().create();

    private GenerationEpochContractFactory() {
    }

    public static GenerationEpoch.DimensionContract create(
            IrisDimension dimension,
            String dimensionKey,
            String dimensionTypeKey
    ) {
        return create(
                dimension,
                dimensionKey,
                dimensionTypeKey,
                CURRENT_DIMENSION_TYPE_FINGERPRINT_SCHEMA
        );
    }

    public static GenerationEpoch.DimensionContract create(
            IrisDimension dimension,
            String dimensionKey,
            String dimensionTypeKey,
            int dimensionTypeFingerprintSchema
    ) {
        return createContract(dimension, dimensionKey, dimensionTypeKey,
                dimensionTypeFingerprintSchema, GenerationPackFingerprint.CURRENT_VERSION);
    }

    public static GenerationEpoch.DimensionContract createForEpoch(
            IrisDimension dimension,
            String dimensionTypeKey,
            GenerationEpoch epoch
    ) {
        GenerationEpoch recorded = Objects.requireNonNull(epoch, "epoch");
        return createContract(dimension, dimension.getLoadKey(), dimensionTypeKey,
                recorded.dimensionContract().dimensionTypeFingerprintSchema(), recorded.packFingerprintVersion());
    }

    private static GenerationEpoch.DimensionContract createContract(
            IrisDimension dimension,
            String dimensionKey,
            String dimensionTypeKey,
            int dimensionTypeFingerprintSchema,
            int packFingerprintVersion
    ) {
        requireSupportedDimensionTypeFingerprintSchema(dimensionTypeFingerprintSchema);
        IrisDimension requiredDimension = Objects.requireNonNull(dimension, "dimension");
        IrisEnvironment environment = Objects.requireNonNull(
                requiredDimension.getEnvironment(),
                "Dimension environment"
        );
        IrisDimensionType dimensionType = requiredDimension.getDimensionType();
        requireDimensionAgreement(requiredDimension, dimensionType);
        IrisDimensionMode configuredMode = requiredDimension.getMode();
        IrisDimensionModeType mode = configuredMode == null || configuredMode.getType() == null
                ? IrisDimensionModeType.OVERWORLD
                : configuredMode.getType();
        boolean upperTerrainEnabled = requiredDimension.hasUpperDimension();
        String upperDimensionKey = upperTerrainEnabled
                ? requireUpperDimensionKey(requiredDimension)
                : "none";

        return new GenerationEpoch.DimensionContract(
                dimensionKey,
                dimensionTypeKey,
                environment.name(),
                mode.name(),
                requiredDimension.getFluidHeight(),
                dimensionType.minY(),
                dimensionType.height(),
                dimensionType.logicalHeight(),
                effectiveCoordinateScale(dimensionType),
                upperTerrainEnabled,
                upperDimensionKey,
                upperTerrainEnabled ? requiredDimension.getUpperDimensionGap() : 0,
                upperTerrainEnabled
                        ? requireUpperTerrainPackFingerprint(requiredDimension, upperDimensionKey, packFingerprintVersion)
                        : NO_UPPER_TERRAIN_FINGERPRINT,
                dimensionTypeFingerprintSchema,
                fingerprintDimensionType(dimensionType, dimensionTypeFingerprintSchema)
        );
    }

    public static String fingerprintDimensionType(IrisDimensionType dimensionType) {
        return fingerprintDimensionType(
                dimensionType,
                CURRENT_DIMENSION_TYPE_FINGERPRINT_SCHEMA
        );
    }

    public static String dimensionTypeSemanticJson(IrisDimensionType dimensionType) {
        IrisDimensionType requiredType = Objects.requireNonNull(dimensionType, "dimensionType");
        JsonObject semantic = new JsonObject();
        semantic.addProperty("base", requiredType.base().name());
        semantic.addProperty("logicalHeight", requiredType.logicalHeight());
        semantic.addProperty("height", requiredType.height());
        semantic.addProperty("minY", requiredType.minY());
        semantic.add("options", SEMANTIC_JSON.toJsonTree(requiredType.options()));
        return semantic.toString();
    }

    public static String fingerprintDimensionType(
            IrisDimensionType dimensionType,
            int fingerprintSchema
    ) {
        requireSupportedDimensionTypeFingerprintSchema(fingerprintSchema);
        IrisDimensionType requiredType = Objects.requireNonNull(dimensionType, "dimensionType");
        MessageDigest digest = sha256();
        updateString(digest, FINGERPRINT_DOMAIN);
        updateInt(digest, fingerprintSchema);
        updateString(digest, dimensionTypeSemanticJson(requiredType));
        return HexFormat.of().formatHex(digest.digest());
    }

    public static void requireSupportedDimensionTypeFingerprintSchema(int fingerprintSchema) {
        if (fingerprintSchema != DIMENSION_TYPE_FINGERPRINT_SCHEMA_VERSION_ONE) {
            throw new IllegalArgumentException(
                    "Unsupported dimension-type fingerprint schema " + fingerprintSchema + "."
            );
        }
    }

    private static double effectiveCoordinateScale(IrisDimensionType dimensionType) {
        IrisDimensionTypeOptions options = dimensionType.options();
        double configured = options.coordinateScale();
        if (configured != -1D) {
            return configured;
        }
        return dimensionType.base() == IDataFixer.Dimension.NETHER ? 8D : 1D;
    }

    private static String requireUpperDimensionKey(IrisDimension dimension) {
        String key = Objects.requireNonNull(
                dimension.getUpperDimension(),
                "Upper dimension key"
        ).trim().toLowerCase(Locale.ROOT);
        if (key.isEmpty() || key.equals("none")) {
            throw new IllegalStateException("Enabled upper terrain requires a dimension key.");
        }
        return key;
    }

    private static String requireUpperTerrainPackFingerprint(
            IrisDimension dimension,
            String upperDimensionKey,
            int packFingerprintVersion
    ) {
        IrisData data = Objects.requireNonNull(
                dimension.getLoader(),
                "Upper terrain dimension loader"
        );
        if (!upperDimensionKey.equals(dimension.getLoadKey())) {
            IrisDimension upperDimension = data.getDimensionLoader().load(upperDimensionKey, false);
            if (upperDimension == null || upperDimension.getLoader() != data) {
                throw new IllegalStateException("Upper dimension '" + upperDimensionKey
                        + "' must exist in the same immutable Iris pack.");
            }
        }
        try {
            return GenerationPackFingerprint.compute(
                    data.getDataFolder().toPath(),
                    packFingerprintVersion
            );
        } catch (IOException failure) {
            throw new IllegalStateException("Unable to fingerprint the immutable upper terrain pack.", failure);
        }
    }

    private static void requireDimensionAgreement(
            IrisDimension dimension,
            IrisDimensionType dimensionType
    ) {
        int declaredMinHeight = dimension.getMinHeight();
        int declaredHeight = Math.subtractExact(dimension.getMaxHeight(), declaredMinHeight);
        int declaredLogicalHeight = dimension.getLogicalHeight();
        if (dimensionType.minY() == declaredMinHeight
                && dimensionType.height() == declaredHeight
                && dimensionType.logicalHeight() == declaredLogicalHeight) {
            return;
        }
        throw new IllegalStateException(
                "Iris dimension-type semantics disagree with the dimension contract."
        );
    }

    private static MessageDigest sha256() {
        try {
            return MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable.", exception);
        }
    }

    private static void updateString(MessageDigest digest, String value) {
        byte[] bytes = value.getBytes(StandardCharsets.UTF_8);
        updateInt(digest, bytes.length);
        digest.update(bytes);
    }

    private static void updateInt(MessageDigest digest, int value) {
        digest.update(ByteBuffer.allocate(Integer.BYTES).putInt(value).array());
    }
}

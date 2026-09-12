package art.arcane.iris.world.history;

import com.google.gson.JsonObject;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Objects;
import java.util.regex.Pattern;

public final class GenerationEpoch {
    public static final int SEED_DERIVATION_VERSION_ONE = 1;
    public static final int CURRENT_SEED_DERIVATION_VERSION = SEED_DERIVATION_VERSION_ONE;

    private static final int IDENTITY_SCHEMA = 1;
    private static final Pattern SHA_256_PATTERN = Pattern.compile("[0-9a-f]{64}");
    private static final Pattern DIMENSION_KEY_PATTERN = Pattern.compile("[a-z0-9_.-]+(?:/[a-z0-9_.-]+)*");
    private static final Pattern RESOURCE_KEY_PATTERN = Pattern.compile("[a-z0-9_.-]+:[a-z0-9/._-]+");
    private static final Pattern ENVIRONMENT_PATTERN = Pattern.compile("[A-Za-z0-9_.:-]+");

    private final String epochId;
    private final Spec spec;

    private GenerationEpoch(String epochId, Spec spec) {
        this.spec = Objects.requireNonNull(spec, "spec");
        this.epochId = requireSha256(epochId, "Epoch ID");
        String expectedEpochId = identity(spec);
        if (!this.epochId.equals(expectedEpochId)) {
            throw new IllegalArgumentException("Epoch ID does not match its generation inputs.");
        }
    }

    public static GenerationEpoch create(Spec spec) {
        Spec requiredSpec = Objects.requireNonNull(spec, "spec");
        return new GenerationEpoch(identity(requiredSpec), requiredSpec);
    }

    static GenerationEpoch restore(String epochId, Spec spec) {
        return new GenerationEpoch(epochId, spec);
    }

    public String epochId() {
        return epochId;
    }

    public String packFingerprint() {
        return spec.packFingerprint();
    }

    public int generatorAbi() {
        return spec.generatorAbi();
    }

    public int packFingerprintVersion() {
        return spec.packFingerprintVersion();
    }

    public int rngVersion() {
        return spec.rngVersion();
    }

    public String kernelImplementationFingerprint() {
        return spec.kernelImplementationFingerprint();
    }

    public long worldSeed() {
        return spec.worldSeed();
    }

    public int seedDerivationVersion() {
        return spec.seedDerivationVersion();
    }

    public GenerationKernelRegistry.Version kernelVersion() {
        return new GenerationKernelRegistry.Version(
                generatorAbi(),
                rngVersion(),
                seedDerivationVersion()
        );
    }

    public DimensionContract dimensionContract() {
        return spec.dimensionContract();
    }

    public GenerationRegistryContract registryContract() {
        return spec.registryContract();
    }

    public Spec spec() {
        return spec;
    }

    JsonObject toJson() {
        JsonObject json = new JsonObject();
        json.addProperty("epochId", epochId);
        json.addProperty("packFingerprint", packFingerprint());
        json.addProperty("packFingerprintVersion", packFingerprintVersion());
        json.addProperty("worldSeed", worldSeed());
        json.addProperty("seedDerivationVersion", seedDerivationVersion());
        json.addProperty("generatorAbi", generatorAbi());
        json.addProperty("rngVersion", rngVersion());
        json.addProperty("kernelImplementationFingerprint", kernelImplementationFingerprint());
        json.add("dimensionContract", dimensionContract().toJson());
        json.add("registryContract", registryContract().toJson());
        return json;
    }

    static GenerationEpoch fromJson(JsonObject json) {
        GenerationManifest.JsonSchema.requireFields(
                json,
                "epoch",
                "epochId",
                "packFingerprint",
                "packFingerprintVersion",
                "worldSeed",
                "seedDerivationVersion",
                "generatorAbi",
                "rngVersion",
                "kernelImplementationFingerprint",
                "dimensionContract",
                "registryContract"
        );
        String epochId = GenerationManifest.JsonSchema.requireString(json, "epochId", "epoch");
        String packFingerprint = GenerationManifest.JsonSchema.requireString(json, "packFingerprint", "epoch");
        int packFingerprintVersion = GenerationManifest.JsonSchema.requireInt(
                json,
                "packFingerprintVersion",
                "epoch"
        );
        long worldSeed = GenerationManifest.JsonSchema.requireLong(json, "worldSeed", "epoch");
        int seedDerivationVersion = GenerationManifest.JsonSchema.requireInt(
                json,
                "seedDerivationVersion",
                "epoch"
        );
        int generatorAbi = GenerationManifest.JsonSchema.requireInt(json, "generatorAbi", "epoch");
        int rngVersion = GenerationManifest.JsonSchema.requireInt(json, "rngVersion", "epoch");
        String kernelImplementationFingerprint = GenerationManifest.JsonSchema.requireString(
                json,
                "kernelImplementationFingerprint",
                "epoch"
        );
        JsonObject contractJson = GenerationManifest.JsonSchema.requireObject(json, "dimensionContract", "epoch");
        DimensionContract contract = DimensionContract.fromJson(contractJson);
        JsonObject registryContractJson = GenerationManifest.JsonSchema.requireObject(
                json,
                "registryContract",
                "epoch"
        );
        GenerationRegistryContract registryContract = GenerationRegistryContract.fromJson(registryContractJson);
        return restore(epochId, new Spec(
                packFingerprint,
                packFingerprintVersion,
                worldSeed,
                seedDerivationVersion,
                generatorAbi,
                rngVersion,
                kernelImplementationFingerprint,
                contract,
                registryContract
        ));
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof GenerationEpoch epoch)) {
            return false;
        }
        return epochId.equals(epoch.epochId) && spec.equals(epoch.spec);
    }

    @Override
    public int hashCode() {
        return Objects.hash(epochId, spec);
    }

    @Override
    public String toString() {
        return "GenerationEpoch[epochId=" + epochId + ", spec=" + spec + "]";
    }

    private static String identity(Spec spec) {
        MessageDigest digest = sha256();
        updateString(digest, "iris-generation-epoch");
        updateInt(digest, IDENTITY_SCHEMA);
        updateString(digest, spec.packFingerprint());
        updateInt(digest, spec.packFingerprintVersion());
        updateLong(digest, spec.worldSeed());
        updateInt(digest, spec.seedDerivationVersion());
        updateInt(digest, spec.generatorAbi());
        updateInt(digest, spec.rngVersion());
        updateString(digest, spec.kernelImplementationFingerprint());
        DimensionContract contract = spec.dimensionContract();
        updateString(digest, contract.dimensionKey());
        updateString(digest, contract.dimensionTypeKey());
        updateString(digest, contract.environment());
        updateString(digest, contract.generationMode());
        updateInt(digest, contract.internalFluidHeight());
        updateInt(digest, contract.minHeight());
        updateInt(digest, contract.height());
        updateInt(digest, contract.logicalHeight());
        updateLong(digest, Double.doubleToLongBits(contract.coordinateScale()));
        digest.update((byte) (contract.upperTerrainEnabled() ? 1 : 0));
        updateString(digest, contract.upperDimensionKey());
        updateInt(digest, contract.upperDimensionGap());
        updateString(digest, contract.upperTerrainPackFingerprint());
        updateInt(digest, contract.dimensionTypeFingerprintSchema());
        updateString(digest, contract.dimensionTypeFingerprint());
        GenerationRegistryContract registryContract = spec.registryContract();
        updateInt(digest, registryContract.fingerprintSchema());
        updateString(digest, registryContract.fingerprint());
        return HexFormat.of().formatHex(digest.digest());
    }

    private static String requireSha256(String value, String label) {
        String requiredValue = Objects.requireNonNull(value, label);
        if (!SHA_256_PATTERN.matcher(requiredValue).matches()) {
            throw new IllegalArgumentException(label + " must be a lowercase SHA-256 value.");
        }
        return requiredValue;
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

    private static void updateLong(MessageDigest digest, long value) {
        digest.update(ByteBuffer.allocate(Long.BYTES).putLong(value).array());
    }

    public record Spec(
            String packFingerprint,
            int packFingerprintVersion,
            long worldSeed,
            int seedDerivationVersion,
            int generatorAbi,
            int rngVersion,
            String kernelImplementationFingerprint,
            DimensionContract dimensionContract,
            GenerationRegistryContract registryContract
    ) {
        public Spec {
            packFingerprint = requireSha256(packFingerprint, "Pack fingerprint");
            if (packFingerprintVersion < 1) {
                throw new IllegalArgumentException("Pack fingerprint version must be positive.");
            }
            if (seedDerivationVersion < 1) {
                throw new IllegalArgumentException("Seed derivation version must be positive.");
            }
            if (generatorAbi < 1) {
                throw new IllegalArgumentException("Generator ABI must be positive.");
            }
            if (rngVersion < 1) {
                throw new IllegalArgumentException("RNG version must be positive.");
            }
            kernelImplementationFingerprint = requireSha256(
                    kernelImplementationFingerprint,
                    "Generation kernel implementation fingerprint"
            );
            dimensionContract = Objects.requireNonNull(dimensionContract, "dimensionContract");
            registryContract = Objects.requireNonNull(registryContract, "registryContract");
        }
    }

    public record DimensionContract(
            String dimensionKey,
            String dimensionTypeKey,
            String environment,
            String generationMode,
            int internalFluidHeight,
            int minHeight,
            int height,
            int logicalHeight,
            double coordinateScale,
            boolean upperTerrainEnabled,
            String upperDimensionKey,
            int upperDimensionGap,
            String upperTerrainPackFingerprint,
            int dimensionTypeFingerprintSchema,
            String dimensionTypeFingerprint
    ) {
        public DimensionContract {
            dimensionKey = requirePattern(dimensionKey, "Dimension key", DIMENSION_KEY_PATTERN);
            dimensionTypeKey = requirePattern(dimensionTypeKey, "Dimension type key", RESOURCE_KEY_PATTERN);
            environment = requirePattern(environment, "Dimension environment", ENVIRONMENT_PATTERN);
            generationMode = requirePattern(generationMode, "Generation mode", ENVIRONMENT_PATTERN);
            if (height < 1) {
                throw new IllegalArgumentException("Dimension height must be positive.");
            }
            Math.addExact(minHeight, height);
            if (logicalHeight < 0 || logicalHeight > height) {
                throw new IllegalArgumentException("Logical height must be inside the dimension height.");
            }
            if (!Double.isFinite(coordinateScale) || coordinateScale <= 0D || coordinateScale > 30000000D) {
                throw new IllegalArgumentException("Coordinate scale must be finite and inside (0, 30000000].");
            }
            upperDimensionKey = requirePattern(
                    upperDimensionKey,
                    "Upper dimension key",
                    DIMENSION_KEY_PATTERN
            );
            if (upperDimensionGap < 0) {
                throw new IllegalArgumentException("Upper dimension gap must not be negative.");
            }
            upperTerrainPackFingerprint = requireSha256(
                    upperTerrainPackFingerprint,
                    "Upper terrain pack fingerprint"
            );
            if (upperTerrainEnabled == upperDimensionKey.equals("none")) {
                throw new IllegalArgumentException("Upper terrain enablement and dimension key disagree.");
            }
            if (upperTerrainEnabled == upperTerrainPackFingerprint.equals("0".repeat(64))) {
                throw new IllegalArgumentException("Upper terrain enablement and pack fingerprint disagree.");
            }
            if (!upperTerrainEnabled && upperDimensionGap != 0) {
                throw new IllegalArgumentException("Disabled upper terrain must have a zero gap.");
            }
            GenerationEpochContractFactory.requireSupportedDimensionTypeFingerprintSchema(
                    dimensionTypeFingerprintSchema
            );
            dimensionTypeFingerprint = requireSha256(dimensionTypeFingerprint, "Dimension type fingerprint");
        }

        public boolean hasSameLayout(DimensionContract other) {
            Objects.requireNonNull(other, "Dimension contract");
            return dimensionKey.equals(other.dimensionKey)
                    && dimensionTypeKey.equals(other.dimensionTypeKey)
                    && environment.equals(other.environment)
                    && minHeight == other.minHeight
                    && height == other.height
                    && logicalHeight == other.logicalHeight
                    && Double.compare(coordinateScale, other.coordinateScale) == 0
                    && dimensionTypeFingerprintSchema == other.dimensionTypeFingerprintSchema
                    && dimensionTypeFingerprint.equals(other.dimensionTypeFingerprint);
        }

        JsonObject toJson() {
            JsonObject json = new JsonObject();
            json.addProperty("dimensionKey", dimensionKey);
            json.addProperty("dimensionTypeKey", dimensionTypeKey);
            json.addProperty("environment", environment);
            json.addProperty("generationMode", generationMode);
            json.addProperty("internalFluidHeight", internalFluidHeight);
            json.addProperty("minHeight", minHeight);
            json.addProperty("height", height);
            json.addProperty("logicalHeight", logicalHeight);
            json.addProperty("coordinateScale", coordinateScale);
            json.addProperty("upperTerrainEnabled", upperTerrainEnabled);
            json.addProperty("upperDimensionKey", upperDimensionKey);
            json.addProperty("upperDimensionGap", upperDimensionGap);
            json.addProperty("upperTerrainPackFingerprint", upperTerrainPackFingerprint);
            json.addProperty("dimensionTypeFingerprintSchema", dimensionTypeFingerprintSchema);
            json.addProperty("dimensionTypeFingerprint", dimensionTypeFingerprint);
            return json;
        }

        static DimensionContract fromJson(JsonObject json) {
            GenerationManifest.JsonSchema.requireFields(
                    json,
                    "dimension contract",
                    "dimensionKey",
                    "dimensionTypeKey",
                    "environment",
                    "generationMode",
                    "internalFluidHeight",
                    "minHeight",
                    "height",
                    "logicalHeight",
                    "coordinateScale",
                    "upperTerrainEnabled",
                    "upperDimensionKey",
                    "upperDimensionGap",
                    "upperTerrainPackFingerprint",
                    "dimensionTypeFingerprintSchema",
                    "dimensionTypeFingerprint"
            );
            return new DimensionContract(
                    GenerationManifest.JsonSchema.requireString(json, "dimensionKey", "dimension contract"),
                    GenerationManifest.JsonSchema.requireString(json, "dimensionTypeKey", "dimension contract"),
                    GenerationManifest.JsonSchema.requireString(json, "environment", "dimension contract"),
                    GenerationManifest.JsonSchema.requireString(json, "generationMode", "dimension contract"),
                    GenerationManifest.JsonSchema.requireInt(json, "internalFluidHeight", "dimension contract"),
                    GenerationManifest.JsonSchema.requireInt(json, "minHeight", "dimension contract"),
                    GenerationManifest.JsonSchema.requireInt(json, "height", "dimension contract"),
                    GenerationManifest.JsonSchema.requireInt(json, "logicalHeight", "dimension contract"),
                    GenerationManifest.JsonSchema.requireDouble(json, "coordinateScale", "dimension contract"),
                    GenerationManifest.JsonSchema.requireBoolean(
                            json,
                            "upperTerrainEnabled",
                            "dimension contract"
                    ),
                    GenerationManifest.JsonSchema.requireString(json, "upperDimensionKey", "dimension contract"),
                    GenerationManifest.JsonSchema.requireInt(json, "upperDimensionGap", "dimension contract"),
                    GenerationManifest.JsonSchema.requireString(
                            json,
                            "upperTerrainPackFingerprint",
                            "dimension contract"
                    ),
                    GenerationManifest.JsonSchema.requireInt(
                            json,
                            "dimensionTypeFingerprintSchema",
                            "dimension contract"
                    ),
                    GenerationManifest.JsonSchema.requireString(
                            json,
                            "dimensionTypeFingerprint",
                            "dimension contract"
                    )
            );
        }

        public int maxHeight() {
            return Math.addExact(minHeight, height);
        }

        private static String requirePattern(String value, String label, Pattern pattern) {
            String requiredValue = Objects.requireNonNull(value, label);
            if (!pattern.matcher(requiredValue).matches()) {
                throw new IllegalArgumentException(label + " is invalid: " + requiredValue);
            }
            return requiredValue;
        }
    }
}

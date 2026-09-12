package art.arcane.iris.world.history;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonNull;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.NavigableMap;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;

public final class GenerationManifest {
    public static final int CURRENT_SCHEMA_VERSION = 2;

    private final int schemaVersion;
    private final NavigableMap<String, GenerationEpoch> epochs;
    private final NavigableMap<Long, GenerationActivation> activations;
    private final long activeActivationId;
    private final Long pendingActivationId;

    private GenerationManifest(State state) {
        State requiredState = Objects.requireNonNull(state, "state");
        this.schemaVersion = requiredState.schemaVersion();
        this.epochs = immutableEpochs(requiredState.epochs());
        this.activations = immutableActivations(requiredState.activations());
        this.activeActivationId = requiredState.activeActivationId();
        this.pendingActivationId = requiredState.pendingActivationId();
        validate();
    }

    public static GenerationManifest initial(GenerationEpoch epoch, long createdAtEpochMillis) {
        GenerationEpoch requiredEpoch = Objects.requireNonNull(epoch, "epoch");
        GenerationActivation activation = GenerationActivation.initial(
                requiredEpoch.epochId(),
                createdAtEpochMillis
        );
        return new GenerationManifest(new State(
                CURRENT_SCHEMA_VERSION,
                Map.of(requiredEpoch.epochId(), requiredEpoch),
                Map.of(activation.activationId(), activation),
                activation.activationId(),
                null
        ));
    }

    static GenerationManifest restore(State state) {
        return new GenerationManifest(state);
    }

    public int schemaVersion() {
        return schemaVersion;
    }

    public Collection<GenerationEpoch> epochs() {
        return epochs.values();
    }

    public Collection<GenerationActivation> activations() {
        return activations.values();
    }

    public Optional<GenerationEpoch> epoch(String epochId) {
        return Optional.ofNullable(epochs.get(epochId));
    }

    public Optional<GenerationActivation> activation(long activationId) {
        return Optional.ofNullable(activations.get(activationId));
    }

    public GenerationActivation activeActivation() {
        return activations.get(activeActivationId);
    }

    public GenerationEpoch activeEpoch() {
        return epochs.get(activeActivation().epochId());
    }

    public Optional<GenerationActivation> pendingActivation() {
        if (pendingActivationId == null) {
            return Optional.empty();
        }
        return Optional.of(activations.get(pendingActivationId));
    }

    public Optional<GenerationEpoch> pendingEpoch() {
        return pendingActivation().map(activation -> epochs.get(activation.epochId()));
    }

    GenerationManifest preparePending(
            GenerationEpoch epoch,
            long createdAtEpochMillis,
            int transitionWidthBlocks
    ) {
        GenerationEpoch requiredEpoch = Objects.requireNonNull(epoch, "epoch");
        Optional<GenerationActivation> pending = pendingActivation();
        if (pending.isPresent()) {
            if (pending.get().epochId().equals(requiredEpoch.epochId())) {
                GenerationEpoch storedEpoch = epochs.get(requiredEpoch.epochId());
                if (!requiredEpoch.equals(storedEpoch)) {
                    throw new IllegalStateException("Pending epoch identity is inconsistent.");
                }
                return this;
            }
            throw new IllegalStateException("A different generation activation is already pending.");
        }
        requireCompatibleDimension(requiredEpoch);

        TreeMap<String, GenerationEpoch> nextEpochs = new TreeMap<>(epochs);
        GenerationEpoch storedEpoch = nextEpochs.putIfAbsent(requiredEpoch.epochId(), requiredEpoch);
        if (storedEpoch != null && !storedEpoch.equals(requiredEpoch)) {
            throw new IllegalStateException("Epoch identity collision detected.");
        }

        long activationId = Math.addExact(activations.lastKey(), 1L);
        GenerationActivation activation = GenerationActivation.next(
                activationId,
                requiredEpoch.epochId(),
                activeActivationId,
                createdAtEpochMillis,
                transitionWidthBlocks
        );
        TreeMap<Long, GenerationActivation> nextActivations = new TreeMap<>(activations);
        nextActivations.put(activationId, activation);
        return new GenerationManifest(new State(
                schemaVersion,
                nextEpochs,
                nextActivations,
                activeActivationId,
                activationId
        ));
    }

    GenerationManifest activatePending(long expectedActivationId) {
        GenerationActivation pending = pendingActivation()
                .orElseThrow(() -> new IllegalStateException("No generation activation is pending."));
        if (pending.activationId() != expectedActivationId) {
            throw new IllegalStateException("Pending activation changed before publication.");
        }
        return new GenerationManifest(new State(
                schemaVersion,
                epochs,
                activations,
                pending.activationId(),
                null
        ));
    }

    GenerationManifest completePendingTransition(
            long expectedActivationId,
            String boundaryIdentity,
            String terrainSignatureIdentity
    ) {
        GenerationActivation pending = pendingActivation()
                .orElseThrow(() -> new IllegalStateException("No generation activation is pending."));
        if (pending.activationId() != expectedActivationId) {
            throw new IllegalStateException("Pending activation changed before transition publication.");
        }
        GenerationActivation completed = pending.completeTransition(
                boundaryIdentity,
                terrainSignatureIdentity
        );
        if (completed == pending) {
            return this;
        }
        TreeMap<Long, GenerationActivation> nextActivations = new TreeMap<>(activations);
        nextActivations.put(completed.activationId(), completed);
        return new GenerationManifest(new State(
                schemaVersion,
                epochs,
                nextActivations,
                activeActivationId,
                pendingActivationId
        ));
    }

    JsonObject toJson() {
        JsonObject json = new JsonObject();
        json.addProperty("schemaVersion", schemaVersion);

        JsonArray epochArray = new JsonArray(epochs.size());
        for (GenerationEpoch epoch : epochs.values()) {
            epochArray.add(epoch.epochId());
        }
        json.add("epochs", epochArray);

        JsonArray activationArray = new JsonArray(activations.size());
        for (GenerationActivation activation : activations.values()) {
            activationArray.add(activation.toJson());
        }
        json.add("activations", activationArray);
        json.addProperty("activeActivationId", activeActivationId);
        if (pendingActivationId == null) {
            json.add("pendingActivationId", JsonNull.INSTANCE);
        } else {
            json.addProperty("pendingActivationId", pendingActivationId);
        }
        return json;
    }

    static GenerationManifest fromJson(JsonObject json, List<GenerationEpoch> epochRecords) {
        JsonSchema.requireFields(
                json,
                "generation manifest",
                "schemaVersion",
                "epochs",
                "activations",
                "activeActivationId",
                "pendingActivationId"
        );
        int schemaVersion = JsonSchema.requireInt(json, "schemaVersion", "generation manifest");
        JsonArray epochArray = JsonSchema.requireArray(json, "epochs", "generation manifest");
        JsonArray activationArray = JsonSchema.requireArray(json, "activations", "generation manifest");
        long activeActivationId = JsonSchema.requireLong(
                json,
                "activeActivationId",
                "generation manifest"
        );
        Long pendingActivationId = JsonSchema.requireNullableLong(
                json,
                "pendingActivationId",
                "generation manifest"
        );

        if (epochArray.size() != epochRecords.size()) {
            throw new IllegalArgumentException("Generation manifest epoch references do not match metadata.");
        }
        TreeMap<String, GenerationEpoch> epochs = new TreeMap<>();
        for (int index = 0; index < epochArray.size(); index++) {
            GenerationEpoch epoch = epochRecords.get(index);
            if (!epochArray.get(index).isJsonPrimitive()
                    || !epochArray.get(index).getAsJsonPrimitive().isString()
                    || !epoch.epochId().equals(epochArray.get(index).getAsString())) {
                throw new IllegalArgumentException("Generation manifest epoch reference does not match metadata.");
            }
            if (epochs.putIfAbsent(epoch.epochId(), epoch) != null) {
                throw new IllegalArgumentException("Generation manifest contains duplicate epoch IDs.");
            }
        }

        TreeMap<Long, GenerationActivation> activations = new TreeMap<>();
        for (int index = 0; index < activationArray.size(); index++) {
            JsonObject activationJson = JsonSchema.requireObject(
                    activationArray.get(index),
                    "activations[" + index + "]"
            );
            GenerationActivation activation = GenerationActivation.fromJson(activationJson);
            if (activations.putIfAbsent(activation.activationId(), activation) != null) {
                throw new IllegalArgumentException("Generation manifest contains duplicate activation IDs.");
            }
        }

        return restore(new State(
                schemaVersion,
                epochs,
                activations,
                activeActivationId,
                pendingActivationId
        ));
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof GenerationManifest manifest)) {
            return false;
        }
        return schemaVersion == manifest.schemaVersion
                && activeActivationId == manifest.activeActivationId
                && epochs.equals(manifest.epochs)
                && activations.equals(manifest.activations)
                && Objects.equals(pendingActivationId, manifest.pendingActivationId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(schemaVersion, epochs, activations, activeActivationId, pendingActivationId);
    }

    private static NavigableMap<String, GenerationEpoch> immutableEpochs(
            Map<String, GenerationEpoch> source
    ) {
        Objects.requireNonNull(source, "epochs");
        TreeMap<String, GenerationEpoch> copy = new TreeMap<>();
        for (Map.Entry<String, GenerationEpoch> entry : source.entrySet()) {
            String epochId = Objects.requireNonNull(entry.getKey(), "epoch ID");
            GenerationEpoch epoch = Objects.requireNonNull(entry.getValue(), "epoch");
            if (!epochId.equals(epoch.epochId())) {
                throw new IllegalArgumentException("Epoch map key does not match the epoch ID.");
            }
            copy.put(epochId, epoch);
        }
        return Collections.unmodifiableNavigableMap(copy);
    }

    private static NavigableMap<Long, GenerationActivation> immutableActivations(
            Map<Long, GenerationActivation> source
    ) {
        Objects.requireNonNull(source, "activations");
        TreeMap<Long, GenerationActivation> copy = new TreeMap<>();
        for (Map.Entry<Long, GenerationActivation> entry : source.entrySet()) {
            Long activationId = Objects.requireNonNull(entry.getKey(), "activation ID");
            GenerationActivation activation = Objects.requireNonNull(entry.getValue(), "activation");
            if (activationId.longValue() != activation.activationId()) {
                throw new IllegalArgumentException("Activation map key does not match the activation ID.");
            }
            copy.put(activationId, activation);
        }
        return Collections.unmodifiableNavigableMap(copy);
    }

    private void validate() {
        if (schemaVersion != CURRENT_SCHEMA_VERSION) {
            throw new IllegalArgumentException("Unsupported generation manifest schema: " + schemaVersion);
        }
        if (epochs.isEmpty()) {
            throw new IllegalArgumentException("Generation manifest must contain an epoch.");
        }
        if (activations.isEmpty()) {
            throw new IllegalArgumentException("Generation manifest must contain an activation.");
        }

        GenerationEpoch.DimensionContract contract = epochs.firstEntry().getValue().dimensionContract();
        long worldSeed = epochs.firstEntry().getValue().worldSeed();
        for (GenerationEpoch epoch : epochs.values()) {
            if (!contract.hasSameLayout(epoch.dimensionContract())) {
                throw new IllegalArgumentException("Generation epochs use incompatible dimension contracts.");
            }
            if (worldSeed != epoch.worldSeed()) {
                throw new IllegalArgumentException("Generation epochs use different world seeds.");
            }
        }

        long expectedActivationId = 1L;
        Set<String> referencedEpochs = new LinkedHashSet<>();
        for (GenerationActivation activation : activations.values()) {
            if (activation.activationId() != expectedActivationId) {
                throw new IllegalArgumentException("Generation activation IDs must be contiguous and monotonic.");
            }
            Long expectedParent = expectedActivationId == 1L ? null : expectedActivationId - 1L;
            if (!Objects.equals(activation.parentActivationId(), expectedParent)) {
                throw new IllegalArgumentException("Generation activation history is not a linear chain.");
            }
            if (!epochs.containsKey(activation.epochId())) {
                throw new IllegalArgumentException("Generation activation references a missing epoch.");
            }
            referencedEpochs.add(activation.epochId());
            if (activation.parentActivationId() == null) {
                if (activation.transition() != null) {
                    throw new IllegalArgumentException("Initial activation must not define a transition.");
                }
            } else if (activation.transition() == null) {
                throw new IllegalArgumentException("Generation activation is missing its transition policy.");
            }
            expectedActivationId = Math.addExact(expectedActivationId, 1L);
        }
        if (!referencedEpochs.equals(epochs.keySet())) {
            throw new IllegalArgumentException("Generation manifest contains an unreferenced epoch.");
        }
        if (!activations.containsKey(activeActivationId)) {
            throw new IllegalArgumentException("Active generation activation is missing.");
        }

        long latestActivationId = activations.lastKey();
        if (pendingActivationId == null) {
            if (activeActivationId != latestActivationId) {
                throw new IllegalArgumentException("Active generation activation is not the latest activation.");
            }
            requireCompletedTransitions();
            return;
        }
        if (!activations.containsKey(pendingActivationId)) {
            throw new IllegalArgumentException("Pending generation activation is missing.");
        }
        if (pendingActivationId != latestActivationId) {
            throw new IllegalArgumentException("Pending generation activation is not the latest activation.");
        }
        GenerationActivation pending = activations.get(pendingActivationId);
        if (!Objects.equals(pending.parentActivationId(), activeActivationId)) {
            throw new IllegalArgumentException("Pending generation activation does not follow the active activation.");
        }
        for (GenerationActivation activation : activations.values()) {
            if (activation.activationId() != pendingActivationId
                    && activation.transition() != null
                    && !activation.transition().isComplete()) {
                throw new IllegalArgumentException("Published generation transition is incomplete.");
            }
        }
    }

    private void requireCompletedTransitions() {
        for (GenerationActivation activation : activations.values()) {
            if (activation.transition() != null && !activation.transition().isComplete()) {
                throw new IllegalArgumentException("Published generation transition is incomplete.");
            }
        }
    }

    private void requireCompatibleDimension(GenerationEpoch epoch) {
        if (!activeEpoch().dimensionContract().hasSameLayout(epoch.dimensionContract())) {
            throw new IllegalArgumentException("Generation epoch changes the immutable dimension contract.");
        }
        if (activeEpoch().worldSeed() != epoch.worldSeed()) {
            throw new IllegalArgumentException("Generation epoch changes the immutable world seed.");
        }
    }

    record State(
            int schemaVersion,
            Map<String, GenerationEpoch> epochs,
            Map<Long, GenerationActivation> activations,
            long activeActivationId,
            Long pendingActivationId
    ) {
    }

    static final class JsonSchema {
        private JsonSchema() {
        }

        static void requireFields(JsonObject json, String context, String... expectedNames) {
            Objects.requireNonNull(json, context);
            Set<String> expected = Set.of(expectedNames);
            Set<String> actual = json.keySet();
            if (!actual.equals(expected)) {
                ArrayList<String> missing = new ArrayList<>(expected);
                missing.removeAll(actual);
                ArrayList<String> unknown = new ArrayList<>(actual);
                unknown.removeAll(expected);
                Collections.sort(missing);
                Collections.sort(unknown);
                throw new IllegalArgumentException(
                        "Invalid " + context + " fields. Missing=" + missing + ", unknown=" + unknown + "."
                );
            }
        }

        static JsonArray requireArray(JsonObject json, String name, String context) {
            JsonElement value = json.get(name);
            if (value == null || !value.isJsonArray()) {
                throw new IllegalArgumentException(context + "." + name + " must be an array.");
            }
            return value.getAsJsonArray();
        }

        static JsonObject requireObject(JsonObject json, String name, String context) {
            JsonElement value = json.get(name);
            if (value == null || !value.isJsonObject()) {
                throw new IllegalArgumentException(context + "." + name + " must be an object.");
            }
            return value.getAsJsonObject();
        }

        static JsonObject requireNullableObject(JsonObject json, String name, String context) {
            JsonElement value = json.get(name);
            if (value == null) {
                throw new IllegalArgumentException(context + "." + name + " is required.");
            }
            if (value.isJsonNull()) {
                return null;
            }
            if (!value.isJsonObject()) {
                throw new IllegalArgumentException(context + "." + name + " must be an object or null.");
            }
            return value.getAsJsonObject();
        }

        static JsonObject requireObject(JsonElement value, String context) {
            if (value == null || !value.isJsonObject()) {
                throw new IllegalArgumentException(context + " must be an object.");
            }
            return value.getAsJsonObject();
        }

        static String requireString(JsonObject json, String name, String context) {
            JsonPrimitive value = requirePrimitive(json, name, context);
            if (!value.isString()) {
                throw new IllegalArgumentException(context + "." + name + " must be a string.");
            }
            return value.getAsString();
        }

        static int requireInt(JsonObject json, String name, String context) {
            try {
                return requireNumber(json, name, context).intValueExact();
            } catch (ArithmeticException exception) {
                throw new IllegalArgumentException(context + "." + name + " must be an exact integer.", exception);
            }
        }

        static long requireLong(JsonObject json, String name, String context) {
            try {
                return requireNumber(json, name, context).longValueExact();
            } catch (ArithmeticException exception) {
                throw new IllegalArgumentException(context + "." + name + " must be an exact long.", exception);
            }
        }

        static Long requireNullableLong(JsonObject json, String name, String context) {
            JsonElement value = json.get(name);
            if (value == null) {
                throw new IllegalArgumentException(context + "." + name + " is required.");
            }
            if (value.isJsonNull()) {
                return null;
            }
            return requireLong(json, name, context);
        }

        static String requireNullableString(JsonObject json, String name, String context) {
            JsonElement value = json.get(name);
            if (value == null) {
                throw new IllegalArgumentException(context + "." + name + " is required.");
            }
            if (value.isJsonNull()) {
                return null;
            }
            if (!value.isJsonPrimitive() || !value.getAsJsonPrimitive().isString()) {
                throw new IllegalArgumentException(context + "." + name + " must be a string or null.");
            }
            return value.getAsString();
        }

        static double requireDouble(JsonObject json, String name, String context) {
            return requireNumber(json, name, context).doubleValue();
        }

        static boolean requireBoolean(JsonObject json, String name, String context) {
            JsonPrimitive value = requirePrimitive(json, name, context);
            if (!value.isBoolean()) {
                throw new IllegalArgumentException(context + "." + name + " must be a boolean.");
            }
            return value.getAsBoolean();
        }

        private static BigDecimal requireNumber(JsonObject json, String name, String context) {
            JsonPrimitive value = requirePrimitive(json, name, context);
            if (!value.isNumber()) {
                throw new IllegalArgumentException(context + "." + name + " must be a number.");
            }
            try {
                return new BigDecimal(value.getAsString());
            } catch (NumberFormatException exception) {
                throw new IllegalArgumentException(context + "." + name + " is not a valid number.", exception);
            }
        }

        private static JsonPrimitive requirePrimitive(JsonObject json, String name, String context) {
            JsonElement value = json.get(name);
            if (value == null || value.isJsonNull() || !value.isJsonPrimitive()) {
                throw new IllegalArgumentException(context + "." + name + " must be a primitive value.");
            }
            return value.getAsJsonPrimitive();
        }
    }
}

package art.arcane.iris.world.history;

import com.google.gson.JsonObject;
import com.google.gson.JsonNull;

import java.util.Objects;
import java.util.regex.Pattern;

public final class GenerationActivation {
    private static final Pattern EPOCH_ID_PATTERN = Pattern.compile("[0-9a-f]{64}");

    private final long activationId;
    private final String epochId;
    private final Long parentActivationId;
    private final long createdAtEpochMillis;
    private final GenerationTransition transition;

    private GenerationActivation(State state) {
        State requiredState = Objects.requireNonNull(state, "state");
        this.activationId = requiredState.activationId();
        this.epochId = requiredState.epochId();
        this.parentActivationId = requiredState.parentActivationId();
        this.createdAtEpochMillis = requiredState.createdAtEpochMillis();
        this.transition = requiredState.transition();
    }

    static GenerationActivation initial(String epochId, long createdAtEpochMillis) {
        return new GenerationActivation(new State(1L, epochId, null, createdAtEpochMillis, null));
    }

    static GenerationActivation next(
            long activationId,
            String epochId,
            long parentActivationId,
            long createdAtEpochMillis,
            int transitionWidthBlocks
    ) {
        return new GenerationActivation(new State(
                activationId,
                epochId,
                parentActivationId,
                createdAtEpochMillis,
                GenerationTransition.pending(transitionWidthBlocks)
        ));
    }

    static GenerationActivation restore(State state) {
        return new GenerationActivation(state);
    }

    public long activationId() {
        return activationId;
    }

    public String epochId() {
        return epochId;
    }

    public Long parentActivationId() {
        return parentActivationId;
    }

    public long createdAtEpochMillis() {
        return createdAtEpochMillis;
    }

    public GenerationTransition transition() {
        return transition;
    }

    public boolean isInitial() {
        return parentActivationId == null;
    }

    GenerationActivation completeTransition(String boundaryIdentity, String terrainSignatureIdentity) {
        if (transition == null) {
            throw new IllegalStateException("The initial activation has no generation transition.");
        }
        return new GenerationActivation(new State(
                activationId,
                epochId,
                parentActivationId,
                createdAtEpochMillis,
                transition.complete(boundaryIdentity, terrainSignatureIdentity)
        ));
    }

    State state() {
        return new State(activationId, epochId, parentActivationId, createdAtEpochMillis, transition);
    }

    JsonObject toJson() {
        JsonObject json = new JsonObject();
        json.addProperty("activationId", activationId);
        json.addProperty("epochId", epochId);
        if (parentActivationId == null) {
            json.add("parentActivationId", JsonNull.INSTANCE);
        } else {
            json.addProperty("parentActivationId", parentActivationId);
        }
        json.addProperty("createdAtEpochMillis", createdAtEpochMillis);
        if (transition == null) {
            json.add("transition", JsonNull.INSTANCE);
        } else {
            json.add("transition", transition.toJson());
        }
        return json;
    }

    static GenerationActivation fromJson(JsonObject json) {
        GenerationManifest.JsonSchema.requireFields(
                json,
                "activation",
                "activationId",
                "epochId",
                "parentActivationId",
                "createdAtEpochMillis",
                "transition"
        );
        long activationId = GenerationManifest.JsonSchema.requireLong(json, "activationId", "activation");
        String epochId = GenerationManifest.JsonSchema.requireString(json, "epochId", "activation");
        Long parentActivationId = GenerationManifest.JsonSchema.requireNullableLong(
                json,
                "parentActivationId",
                "activation"
        );
        long createdAtEpochMillis = GenerationManifest.JsonSchema.requireLong(
                json,
                "createdAtEpochMillis",
                "activation"
        );
        JsonObject transitionJson = GenerationManifest.JsonSchema.requireNullableObject(
                json,
                "transition",
                "activation"
        );
        GenerationTransition transition = transitionJson == null
                ? null
                : GenerationTransition.fromJson(transitionJson);
        return restore(new State(
                activationId,
                epochId,
                parentActivationId,
                createdAtEpochMillis,
                transition
        ));
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof GenerationActivation activation)) {
            return false;
        }
        return activationId == activation.activationId
                && createdAtEpochMillis == activation.createdAtEpochMillis
                && epochId.equals(activation.epochId)
                && Objects.equals(parentActivationId, activation.parentActivationId)
                && Objects.equals(transition, activation.transition);
    }

    @Override
    public int hashCode() {
        return Objects.hash(activationId, epochId, parentActivationId, createdAtEpochMillis, transition);
    }

    @Override
    public String toString() {
        return "GenerationActivation[activationId=" + activationId
                + ", epochId=" + epochId
                + ", parentActivationId=" + parentActivationId
                + ", createdAtEpochMillis=" + createdAtEpochMillis
                + ", transition=" + transition + "]";
    }

    record State(
            long activationId,
            String epochId,
            Long parentActivationId,
            long createdAtEpochMillis,
            GenerationTransition transition
    ) {
        State {
            if (activationId < 1L) {
                throw new IllegalArgumentException("Activation ID must be positive.");
            }
            epochId = Objects.requireNonNull(epochId, "epochId");
            if (!EPOCH_ID_PATTERN.matcher(epochId).matches()) {
                throw new IllegalArgumentException("Activation epoch ID must be a lowercase SHA-256 value.");
            }
            if (parentActivationId != null
                    && (parentActivationId < 1L || parentActivationId >= activationId)) {
                throw new IllegalArgumentException("Parent activation ID must identify an earlier activation.");
            }
            if (createdAtEpochMillis < 0L) {
                throw new IllegalArgumentException("Activation creation time cannot be negative.");
            }
            if ((parentActivationId == null) != (transition == null)) {
                throw new IllegalArgumentException(
                        "Only the initial activation may omit a generation transition."
                );
            }
        }
    }
}

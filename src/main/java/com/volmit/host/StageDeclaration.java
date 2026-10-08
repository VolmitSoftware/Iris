package com.volmit.host;

import com.volmit.IrisFeature;
import com.volmit.mantle.MantleKey;
import com.volmit.resource.ResourceId;
import com.volmit.stage.PackReach;
import com.volmit.stage.Pipeline;
import com.volmit.stage.Stage;
import com.volmit.stage.StageRef;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;

/** One stage as declared during install. It becomes immutable once its body is set. */
final class StageDeclaration {
    private final Stage<?> stage;
    private final ResourceId module;
    private final List<Stage<?>> requirements = new ArrayList<>();
    private final List<StageRef> softAfter = new ArrayList<>();
    private final List<StageRef> softBefore = new ArrayList<>();
    private FeatureDescriptor<?> settings;
    private PackReach reach;
    private final List<MantleKey<?>> mantleLayers = new ArrayList<>();
    private StageBody body;

    StageDeclaration(Stage<?> stage, ResourceId module) {
        this.stage = Objects.requireNonNull(stage, "stage");
        this.module = Objects.requireNonNull(module, "module");
    }

    void require(Stage<?>... stages) {
        checkOpen();
        for (var required : stages) {
            Objects.requireNonNull(required, "required stage");
            rejectSelfReference(required.id());
            requirements.add(required);
        }
    }

    void softAfter(StageRef... references) {
        addSoftReferences(softAfter, references);
    }

    void softBefore(StageRef... references) {
        addSoftReferences(softBefore, references);
    }

    void condition(Class<? extends IrisFeature> settingsType) {
        checkOpen();
        if (settings != null) {
            throw new IllegalStateException("Stage " + this + " already has a when(...) condition");
        }
        settings = FeatureDescriptor.of(settingsType);
    }

    void reach(int blocks) {
        checkOpen();
        if (blocks > 0) requireOutsideChunkPipeline("a reach of " + blocks + " blocks");
        if (blocks < 0) {
            throw new IllegalArgumentException("Stage " + this + " declares a negative reach of " + blocks + " blocks");
        }
        declareReach(new FixedReach(blocks));
    }

    void reach(PackReach computed) {
        checkOpen();
        Objects.requireNonNull(computed, "reach");
        if (stage.pipeline() != Pipeline.MANTLE) {
            throw new IllegalArgumentException("Stage " + this + " is a " + stage.pipeline().name().toLowerCase(Locale.ROOT)
                    + " stage and declares a reach worked out when the pack loads, which only mantle stages may;"
                    + " declare a number of blocks with reach(blocks)");
        }
        declareReach(computed);
    }

    private void declareReach(PackReach declared) {
        if (reach != null) {
            throw new IllegalStateException("Stage " + this + " already declares " + describe(reach));
        }
        reach = declared;
    }

    private static String describe(PackReach reach) {
        return reach instanceof FixedReach fixed
                ? "a reach of " + fixed.blocks() + " blocks"
                : "a reach worked out when the pack loads";
    }

    void writesMantle(MantleKey<?>... layers) {
        checkOpen();
        requireOutsideChunkPipeline("mantle writes");
        for (var layer : layers) {
            Objects.requireNonNull(layer, "mantle layer");
            if (mantleLayers.stream().anyMatch(declared -> declared.id().equals(layer.id()))) {
                throw new IllegalArgumentException("Stage " + this + " declares mantle layer " + layer.id() + " twice");
            }
            mantleLayers.add(layer);
        }
    }

    /** Sets the only body. alternativeMethod names the builder method for the other output kind. */
    void complete(StageBody body, boolean bodyProducesOutput, String alternativeMethod) {
        checkOpen();
        Objects.requireNonNull(body, "body");
        if (bodyProducesOutput != stage.producesOutput()) {
            throw new IllegalStateException(bodyMismatch(alternativeMethod));
        }
        this.body = body;
    }

    boolean isComplete() {
        return body != null;
    }

    Stage<?> stage() {
        return stage;
    }

    ResourceId module() {
        return module;
    }

    List<Stage<?>> requirements() {
        return List.copyOf(requirements);
    }

    List<StageRef> softAfter() {
        return List.copyOf(softAfter);
    }

    List<StageRef> softBefore() {
        return List.copyOf(softBefore);
    }

    Optional<FeatureDescriptor<?>> settings() {
        return Optional.ofNullable(settings);
    }

    StageBody body() {
        return body;
    }

    StageReach reach() {
        return new StageReach(reach == null ? FixedReach.NONE : reach, mantleLayers);
    }

    @Override
    public String toString() {
        return stage.id() + " (module " + module + ")";
    }

    private void addSoftReferences(List<StageRef> target, StageRef... references) {
        checkOpen();
        for (var reference : references) {
            Objects.requireNonNull(reference, "stage reference");
            rejectSelfReference(reference.id());
            target.add(reference);
        }
    }

    private void rejectSelfReference(ResourceId id) {
        if (stage.id().equals(id)) {
            throw new IllegalArgumentException("Stage " + this + " cannot depend on itself");
        }
    }

    private String bodyMismatch(String alternativeMethod) {
        var declared = stage.producesOutput()
                ? "declares output " + stage.outputType().getSimpleName()
                : "declares no output";
        return "Stage " + this + " " + declared + "; define it with " + alternativeMethod + "(...)";
    }

    /** Chunk stages write only their own chunk: the host may already have generated its neighbors. */
    private void requireOutsideChunkPipeline(String declared) {
        if (stage.pipeline() != Pipeline.CHUNK) return;
        throw new IllegalArgumentException("Stage " + this + " is a chunk stage and declares " + declared
                + ", but chunk stages write only their own chunk and read the mantle; define work that writes the"
                + " mantle or reaches beyond its chunk as a mantle stage (Stage.mantle), which runs before the chunks it reaches");
    }

    private void checkOpen() {
        if (body != null) {
            throw new IllegalStateException("Stage " + this + " is already complete");
        }
    }
}

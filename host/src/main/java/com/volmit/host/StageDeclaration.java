package com.volmit.host;

import com.volmit.IrisFeature;
import com.volmit.ModuleLog;
import com.volmit.mantle.MantleKey;
import com.volmit.resource.ResourceId;
import com.volmit.stage.PackReach;
import com.volmit.stage.Stage;
import com.volmit.stage.StageAction;
import com.volmit.stage.StageFunction;
import com.volmit.stage.StageRef;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/** One stage as declared during setup. It becomes immutable once its body is set. */
final class StageDeclaration {
    private final Stage<?> stage;
    private final ResourceId module;
    private final ModuleLog log;
    private final List<Stage<?>> requirements = new ArrayList<>();
    private final List<StageRef> softAfter = new ArrayList<>();
    private final List<StageRef> softBefore = new ArrayList<>();
    private FeatureDescriptor<?> settings;
    private PackReach reach;
    private final List<MantleKey<?>> mantleLayers = new ArrayList<>();
    private StageBody body;

    StageDeclaration(Stage<?> stage, ResourceId module, ModuleLog log) {
        this.stage = Objects.requireNonNull(stage, "stage");
        this.module = Objects.requireNonNull(module, "module");
        this.log = Objects.requireNonNull(log, "log");
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

    void softAfter(Stage<?>... stages) {
        addSoftReferences(softAfter, references(stages));
    }

    void softBefore(StageRef... references) {
        addSoftReferences(softBefore, references);
    }

    void softBefore(Stage<?>... stages) {
        addSoftReferences(softBefore, references(stages));
    }

    private static StageRef[] references(Stage<?>... stages) {
        var references = new StageRef[stages.length];
        for (int index = 0; index < stages.length; index++) {
            references[index] = Objects.requireNonNull(stages[index], "stage").ref();
        }
        return references;
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
        if (blocks < 0) {
            throw new IllegalArgumentException("Stage " + this + " declares a negative reach of " + blocks + " blocks");
        }
        declareReach(new FixedReach(blocks));
    }

    void reach(PackReach computed) {
        checkOpen();
        declareReach(Objects.requireNonNull(computed, "reach"));
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
        for (var layer : layers) {
            Objects.requireNonNull(layer, "mantle layer");
            if (mantleLayers.stream().anyMatch(declared -> declared.id().equals(layer.id()))) {
                throw new IllegalArgumentException("Stage " + this + " declares mantle layer " + layer.id() + " twice");
            }
            mantleLayers.add(layer);
        }
    }

    /** Sets an invocation body without output. */
    void run(StageAction<?> action) {
        Objects.requireNonNull(action, "action");
        complete(new StageBody.Invocation(context -> {
            action.run(cast(context));
            return null;
        }), false, "produce");
    }

    /** Sets an invocation body with output. */
    void produce(StageFunction<?, ?> function) {
        Objects.requireNonNull(function, "function");
        complete(new StageBody.Invocation(context -> function.run(cast(context))), true, "run");
    }

    @SuppressWarnings("unchecked") // The executor passes the context of the pipeline this stage was declared in.
    private static <T> T cast(Object value) {
        return (T) value;
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

    ModuleLog log() {
        return log;
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

    private void checkOpen() {
        if (body != null) {
            throw new IllegalStateException("Stage " + this + " is already complete");
        }
    }
}

package com.volmit.host;

import com.volmit.IrisFeature;
import com.volmit.mantle.MantleKey;
import com.volmit.stage.ConditionalStageDefinition;
import com.volmit.stage.SettingsAction;
import com.volmit.stage.SettingsFunction;
import com.volmit.stage.Stage;
import com.volmit.stage.StageAction;
import com.volmit.stage.StageContext;
import com.volmit.stage.StageDefinition;
import com.volmit.stage.StageFunction;
import com.volmit.stage.StageRef;
import java.util.Objects;

/** Builder behind both invocation stage definition views; S is unused until when(...). */
final class InvocationStageDraft<C extends StageContext, S extends IrisFeature, O>
        implements StageDefinition<C, O>, ConditionalStageDefinition<C, S, O> {
    private final StageDeclaration declaration;

    InvocationStageDraft(StageDeclaration declaration) {
        this.declaration = declaration;
    }

    @Override
    public InvocationStageDraft<C, S, O> requires(Stage<?>... stages) {
        declaration.require(stages);
        return this;
    }

    @Override
    public InvocationStageDraft<C, S, O> softAfter(StageRef... stages) {
        declaration.softAfter(stages);
        return this;
    }

    @Override
    public InvocationStageDraft<C, S, O> softBefore(StageRef... stages) {
        declaration.softBefore(stages);
        return this;
    }

    @Override
    public InvocationStageDraft<C, S, O> reach(int blocks) {
        declaration.reach(blocks);
        return this;
    }

    @Override
    public InvocationStageDraft<C, S, O> writesMantle(MantleKey<?>... layers) {
        declaration.writesMantle(layers);
        return this;
    }

    @Override
    @SuppressWarnings("unchecked") // S is a phantom parameter until the condition is recorded here.
    public <N extends IrisFeature> InvocationStageDraft<C, N, O> when(Class<N> settingsType) {
        declaration.condition(settingsType);
        return (InvocationStageDraft<C, N, O>) (InvocationStageDraft<?, ?, ?>) this;
    }

    @Override
    public void run(StageAction<C> action) {
        Objects.requireNonNull(action, "action");
        complete(false, "produce", (context, settings) -> {
            action.run(cast(context));
            return null;
        });
    }

    @Override
    public void produce(StageFunction<C, O> function) {
        Objects.requireNonNull(function, "function");
        complete(true, "run", (context, settings) -> function.run(cast(context)));
    }

    @Override
    public void run(SettingsAction<C, S> action) {
        Objects.requireNonNull(action, "action");
        complete(false, "produce", (context, settings) -> {
            action.run(cast(context), cast(settings));
            return null;
        });
    }

    @Override
    public void produce(SettingsFunction<C, S, O> function) {
        Objects.requireNonNull(function, "function");
        complete(true, "run", (context, settings) -> function.run(cast(context), cast(settings)));
    }

    private void complete(boolean producesOutput, String alternativeMethod, StageBody.InvocationWork work) {
        declaration.complete(new StageBody.Invocation(work), producesOutput, alternativeMethod);
    }

    @SuppressWarnings("unchecked") // The executor passes the context and settings this stage was declared with.
    private static <T> T cast(Object value) {
        return (T) value;
    }
}

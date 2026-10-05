package com.volmit.host;

import com.volmit.IrisFeature;
import com.volmit.stage.BlockCollector;
import com.volmit.stage.BlockStageContext;
import com.volmit.stage.BlockStageDefinition;
import com.volmit.stage.ConditionalBlockStageDefinition;
import com.volmit.stage.SettingsAction;
import com.volmit.stage.SettingsBlockCollector;
import com.volmit.stage.Stage;
import com.volmit.stage.StageAction;
import com.volmit.stage.StageRef;
import java.util.Objects;
import java.util.function.Supplier;

/** Builder behind both block stage definition views; S is unused until when(...). */
final class BlockStageDraft<S extends IrisFeature, O>
        implements BlockStageDefinition<O>, ConditionalBlockStageDefinition<S, O> {
    private final StageDeclaration declaration;

    BlockStageDraft(StageDeclaration declaration) {
        this.declaration = declaration;
    }

    @Override
    public BlockStageDraft<S, O> requires(Stage<?>... stages) {
        declaration.require(stages);
        return this;
    }

    @Override
    public BlockStageDraft<S, O> softAfter(StageRef... stages) {
        declaration.softAfter(stages);
        return this;
    }

    @Override
    public BlockStageDraft<S, O> softBefore(StageRef... stages) {
        declaration.softBefore(stages);
        return this;
    }

    @Override
    @SuppressWarnings("unchecked") // S is a phantom parameter until the condition is recorded here.
    public <N extends IrisFeature> BlockStageDraft<N, O> when(Class<N> settingsType) {
        declaration.condition(settingsType);
        return (BlockStageDraft<N, O>) (BlockStageDraft<?, ?>) this;
    }

    @Override
    public void run(StageAction<BlockStageContext> action) {
        Objects.requireNonNull(action, "action");
        complete(null, (context, settings, accumulator) -> action.run(context));
    }

    @Override
    public void accumulate(Supplier<O> initial, BlockCollector<O> collector) {
        Objects.requireNonNull(collector, "collector");
        complete(Objects.requireNonNull(initial, "initial"),
                (context, settings, accumulator) -> collector.collect(context, cast(accumulator)));
    }

    @Override
    public void run(SettingsAction<BlockStageContext, S> action) {
        Objects.requireNonNull(action, "action");
        complete(null, (context, settings, accumulator) -> action.run(context, cast(settings)));
    }

    @Override
    public void accumulate(Supplier<O> initial, SettingsBlockCollector<S, O> collector) {
        Objects.requireNonNull(collector, "collector");
        complete(Objects.requireNonNull(initial, "initial"),
                (context, settings, accumulator) -> collector.collect(context, cast(settings), cast(accumulator)));
    }

    private void complete(Supplier<?> initial, StageBody.BlockWork work) {
        boolean accumulates = initial != null;
        declaration.complete(new StageBody.Block(initial, work), accumulates, accumulates ? "run" : "accumulate");
    }

    @SuppressWarnings("unchecked") // The executor passes the settings and accumulator this stage was declared with.
    private static <T> T cast(Object value) {
        return (T) value;
    }
}

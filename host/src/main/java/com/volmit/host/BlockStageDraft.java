package com.volmit.host;

import com.volmit.IrisFeature;
import com.volmit.mantle.MantleKey;
import com.volmit.stage.BlockCollector;
import com.volmit.stage.BlockStageContext;
import com.volmit.stage.BlockStageDefinition;
import com.volmit.stage.Stage;
import com.volmit.stage.StageAction;
import com.volmit.stage.StageRef;
import java.util.Objects;
import java.util.function.Supplier;

/** Builder behind {@link BlockStageDefinition}. */
final class BlockStageDraft<O> implements BlockStageDefinition<O> {
    private final StageDeclaration declaration;

    BlockStageDraft(StageDeclaration declaration) {
        this.declaration = declaration;
    }

    @Override
    public BlockStageDraft<O> requires(Stage<?>... stages) {
        declaration.require(stages);
        return this;
    }

    @Override
    public BlockStageDraft<O> softAfter(Stage<?>... stages) {
        declaration.softAfter(stages);
        return this;
    }

    @Override
    public BlockStageDraft<O> softAfter(StageRef... stages) {
        declaration.softAfter(stages);
        return this;
    }

    @Override
    public BlockStageDraft<O> softBefore(Stage<?>... stages) {
        declaration.softBefore(stages);
        return this;
    }

    @Override
    public BlockStageDraft<O> softBefore(StageRef... stages) {
        declaration.softBefore(stages);
        return this;
    }

    @Override
    public BlockStageDraft<O> reach(int blocks) {
        declaration.reach(blocks);
        return this;
    }

    @Override
    public BlockStageDraft<O> writesMantle(MantleKey<?>... layers) {
        declaration.writesMantle(layers);
        return this;
    }

    @Override
    public BlockStageDraft<O> when(Class<? extends IrisFeature> settingsType) {
        declaration.condition(settingsType);
        return this;
    }

    @Override
    public void run(StageAction<BlockStageContext> action) {
        Objects.requireNonNull(action, "action");
        complete(null, (context, accumulator) -> action.run(context));
    }

    @Override
    public void accumulate(Supplier<O> initial, BlockCollector<O> collector) {
        Objects.requireNonNull(collector, "collector");
        complete(Objects.requireNonNull(initial, "initial"),
                (context, accumulator) -> collector.collect(context, cast(accumulator)));
    }

    private void complete(Supplier<?> initial, StageBody.BlockWork work) {
        boolean accumulates = initial != null;
        declaration.complete(new StageBody.Block(initial, work), accumulates, accumulates ? "run" : "accumulate");
    }

    @SuppressWarnings("unchecked") // The executor passes the accumulator this stage was declared with.
    private static <T> T cast(Object value) {
        return (T) value;
    }
}

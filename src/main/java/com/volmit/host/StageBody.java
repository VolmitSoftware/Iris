package com.volmit.host;

import com.volmit.stage.BlockStageContext;
import java.util.function.Supplier;

/** Type-erased stage work. Drafts build the body matching their stage kind, so executors may
 * rely on invocation stages carrying Invocation bodies and block stages carrying Block bodies.
 */
sealed interface StageBody {
    record Invocation(InvocationWork work) implements StageBody {}

    /** initial is null for block stages without an accumulator. */
    record Block(Supplier<?> initial, BlockWork work) implements StageBody {}

    @FunctionalInterface
    interface InvocationWork {
        Object run(Object context);
    }

    @FunctionalInterface
    interface BlockWork {
        void run(BlockStageContext context, Object accumulator);
    }
}

package com.volmit.host;

import com.volmit.stage.StageKind;
import java.util.PriorityQueue;

/** Choice among stages whose predecessors have all run. Node numbers follow lexical stage id
 * order, so each queue yields the lexically smallest ready stage. To keep block passes fused,
 * an open pass continues while a block stage is ready, and ready invocation stages run before
 * a new pass opens. The choice depends only on ids and edges, never on install order.
 */
final class ReadyStages {
    private final PriorityQueue<Integer> invocation = new PriorityQueue<>();
    private final PriorityQueue<Integer> block = new PriorityQueue<>();
    private boolean blockPassOpen;

    void add(int node, StageKind kind) {
        queue(kind).add(node);
    }

    boolean isEmpty() {
        return invocation.isEmpty() && block.isEmpty();
    }

    int next() {
        if (blockPassOpen && !block.isEmpty()) {
            return block.remove();
        }
        if (!invocation.isEmpty()) {
            blockPassOpen = false;
            return invocation.remove();
        }
        blockPassOpen = true;
        return block.remove();
    }

    private PriorityQueue<Integer> queue(StageKind kind) {
        return kind == StageKind.BLOCK ? block : invocation;
    }
}

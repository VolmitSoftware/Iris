package com.volmit.host;

import com.volmit.ModuleLog;
import com.volmit.stage.Stage;

/** Execution data of one resolved stage. Indices are positions in the resolved order.
 * readableStages/readablePositions are the transitive requirements whose outputs it may read.
 * settingsSlot indexes the graph's distinct settings types, or is NO_SETTINGS. idHash keys the
 * stage's random stream ({@link StageStreams}), computed once per pack load. log is the log of the module that
 * defined the stage.
 */
record ExecutableStage(
        Stage<?> stage,
        StageBody body,
        int[] requirements,
        Stage<?>[] readableStages,
        int[] readablePositions,
        int settingsSlot,
        long idHash,
        ModuleLog log) {
    static final int NO_SETTINGS = -1;
    static final int NOT_READABLE = -1;

    int readablePosition(Stage<?> requested) {
        for (int index = 0; index < readableStages.length; index++) {
            if (readableStages[index] == requested) {
                return readablePositions[index];
            }
        }
        return NOT_READABLE;
    }
}

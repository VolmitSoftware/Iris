package com.volmit.host;

import com.volmit.stage.Stage;

/** Execution data of one resolved stage. Indices are positions in the resolved order.
 * readableStages/readablePositions are the transitive requirements whose outputs it may read.
 * settingsSlot indexes the graph's distinct settings types, or is NO_SETTINGS. idHash keys the
 * stage's random stream ({@link StageStreams}), computed once per pack load. worlds gives the world as the module
 * that defined the stage sees it, shared by that module's stages.
 */
record ExecutableStage(
        Stage<?> stage,
        StageBody body,
        int[] requirements,
        Stage<?>[] readableStages,
        int[] readablePositions,
        int settingsSlot,
        long idHash,
        ScopedWorlds worlds) {
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

package com.volmit.stage;

import com.volmit.IrisFeature;

/** Declares one chunk stage. A chunk stage writes only its own chunk, its blocks and its columns of the mantle, and
 * reads the mantle around it, so it declares neither a reach nor mantle layers: work that writes the mantle beyond its
 * chunk is a {@link MantleStage}. Edges work as on {@link PlacementStageDefinition}.
 */
public interface ChunkStageDefinition<O> {
    ChunkStageDefinition<O> requires(Stage<?>... stages);

    ChunkStageDefinition<O> softAfter(Stage<?>... stages);

    ChunkStageDefinition<O> softAfter(StageRef... stages);

    ChunkStageDefinition<O> softBefore(Stage<?>... stages);

    ChunkStageDefinition<O> softBefore(StageRef... stages);

    /** Activates the stage only where any column of the chunk configures the given settings; the body reads every
     * distinct entry of those columns with {@link StageContext#configured}. See {@link StageGraph} for who may name a
     * settings type.
     */
    ChunkStageDefinition<O> when(Class<? extends IrisFeature> settingsType);

    /** Body for a stage without output. */
    void run(StageAction<ChunkStageContext> action);

    /** Body for a stage with output; a null result fails the invocation. */
    void produce(StageFunction<ChunkStageContext, O> function);
}

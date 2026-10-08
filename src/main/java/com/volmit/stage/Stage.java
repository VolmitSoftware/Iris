package com.volmit.stage;

import com.volmit.resource.ResourceId;
import java.util.Objects;

/** A unit of pipeline work, declared as a constant by the module that defines it.
 * The id starts with the defining module's id and '/', such as {@code iris:stilt/fill}. Identity is this
 * declaration object: other modules require or read the owner's constant, never a copy.
 * The output type is Void for stages that only act; otherwise dependents read it through
 * {@link StageContext#output(Stage)}. Block stages accumulate their output across the pass.
 */
public abstract sealed class Stage<O> permits PlacementStage, BlockStage, ChunkStage, MantleStage {
    private final ResourceId id;
    private final Class<O> outputType;

    Stage(String id, Class<O> outputType) {
        this.id = ResourceId.parse(id);
        this.outputType = Objects.requireNonNull(outputType, "outputType");
        if (outputType.isPrimitive()) {
            throw new IllegalArgumentException("Stage output requires a reference type: " + id);
        }
    }

    public static PlacementStage<Void> placement(String id) {
        return new PlacementStage<>(id, Void.class);
    }

    public static <O> PlacementStage<O> placement(String id, Class<O> outputType) {
        return new PlacementStage<>(id, outputType);
    }

    public static BlockStage<Void> placementBlock(String id) {
        return new BlockStage<>(id, Void.class);
    }

    /** The accumulator type is created once per placement and becomes the stage output. */
    public static <O> BlockStage<O> placementBlock(String id, Class<O> accumulatorType) {
        return new BlockStage<>(id, accumulatorType);
    }

    public static ChunkStage<Void> chunk(String id) {
        return new ChunkStage<>(id, Void.class);
    }

    public static <O> ChunkStage<O> chunk(String id, Class<O> outputType) {
        return new ChunkStage<>(id, outputType);
    }

    /** Mantle stages have no output: each runs as its own pass, so they pass data on through mantle layers. */
    public static MantleStage<Void> mantle(String id) {
        return new MantleStage<>(id, Void.class);
    }

    public final ResourceId id() {
        return id;
    }

    public final Class<O> outputType() {
        return outputType;
    }

    public final boolean producesOutput() {
        return outputType != Void.class;
    }

    public final StageRef ref() {
        return new StageRef(id);
    }

    public abstract Pipeline pipeline();

    public abstract StageKind kind();

    @Override
    public final String toString() {
        return id.toString();
    }
}

package com.volmit.host;

import com.volmit.config.FeatureSettings;
import com.volmit.object.ObjectBlock;
import com.volmit.object.ObjectTransformer;
import com.volmit.placement.PlacementAccess;
import com.volmit.placement.PlacementContext;
import com.volmit.placement.PlacementRequest;
import com.volmit.random.WorldRandom;
import com.volmit.resource.PackResources;
import com.volmit.stage.BlockStageContext;
import com.volmit.stage.Stage;
import com.volmit.world.BlockView;
import com.volmit.world.TerrainSampler;
import java.util.Objects;

/** The context every stage of one placement attempt receives. Block accessors work only while
 * a block pass is running.
 */
final class PlacementInvocation implements BlockStageContext {
    private enum Progress { RUNNING, REJECTED }

    private enum BlockStatus { OUTSIDE_PASS, VISIBLE, SKIPPED }

    private final StageInvocation stages;
    private final PlacementRequest request;
    private final PlacementContext inputs;
    private final PlacementAccess access;
    private Progress progress = Progress.RUNNING;
    private String rejection;
    private BlockStatus blockStatus = BlockStatus.OUTSIDE_PASS;
    private ObjectBlock block;

    PlacementInvocation(StageInvocation stages, PlacementRequest request, PlacementContext inputs, PlacementAccess access) {
        this.stages = stages;
        this.request = Objects.requireNonNull(request, "request");
        this.inputs = Objects.requireNonNull(inputs, "inputs");
        this.access = access;
    }

    StageInvocation stages() {
        return stages;
    }

    boolean isRejected() {
        return progress == Progress.REJECTED;
    }

    String rejection() {
        return rejection;
    }

    void beginBlock(ObjectBlock next) {
        block = next;
        blockStatus = BlockStatus.VISIBLE;
    }

    boolean isBlockSkipped() {
        return blockStatus == BlockStatus.SKIPPED;
    }

    void endBlockPass() {
        block = null;
        blockStatus = BlockStatus.OUTSIDE_PASS;
    }

    @Override
    public <O> O output(Stage<O> stage) {
        return stages.output(stage);
    }

    @Override
    public PlacementRequest request() {
        return request;
    }

    @Override
    public PlacementAccess access() {
        return access;
    }

    @Override
    public void reject(String reason) {
        Objects.requireNonNull(reason, "reason");
        if (progress == Progress.RUNNING) {
            progress = Progress.REJECTED;
            rejection = reason;
        }
    }

    @Override
    public ObjectBlock block() {
        requireBlockPass();
        return block;
    }

    @Override
    public void replace(ObjectBlock replacement) {
        requireBlockPass();
        block = Objects.requireNonNull(replacement, "block");
    }

    @Override
    public void skip() {
        requireBlockPass();
        blockStatus = BlockStatus.SKIPPED;
    }

    @Override
    public long worldSeed() {
        return inputs.worldSeed();
    }

    @Override
    public PackResources resources() {
        return inputs.resources();
    }

    @Override
    public WorldRandom random() {
        return stages.random();
    }

    @Override
    public TerrainSampler terrain() {
        return inputs.terrain();
    }

    @Override
    public FeatureSettings settings() {
        return inputs.settings();
    }

    @Override
    public ObjectTransformer objects() {
        return inputs.objects();
    }

    @Override
    public BlockView blocks() {
        return inputs.blocks();
    }

    private void requireBlockPass() {
        if (blockStatus == BlockStatus.OUTSIDE_PASS) {
            throw new IllegalStateException("The current block is only available to block stages");
        }
    }
}

package art.arcane.iris.engine.object;

import art.arcane.iris.engine.object.PlacementContext.TransformedBounds;
import art.arcane.iris.engine.object.PlacementContext.WorldBounds;
import art.arcane.iris.util.common.math.IrisBlockVector;
import art.arcane.volmlib.util.collection.KMap;
import art.arcane.volmlib.util.math.Position2;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.Setter;

import java.util.Objects;

/** Results and accumulators owned by one placement, never by a shared module or plan. */
@Getter
@Setter
public final class PlacementState {
    IrisObjectPlacement config;
    IObjectPlacer placer;
    ObjectSpinKernel spin;
    int spinX;
    int spinY;
    int spinZ;
    IrisBlockVector translateOffset;
    int rty;
    int ty;
    int yrand;
    int warpMargin;
    int y;
    TransformedBounds bounds;
    WorldBounds worldBounds;
    int stiltLowest = Integer.MAX_VALUE;
    int stiltHighest = Integer.MIN_VALUE;
    int vacuumLowest = Integer.MAX_VALUE;
    int vacuumHighest = Integer.MIN_VALUE;
    KMap<Position2, Integer> snowHeights;

    @Setter(AccessLevel.NONE)
    private String rejection;

    PlacementState(PlacementContext context) {
        this(-1, null);
        config = context.config;
        placer = context.placer;
    }

    PlacementState(int y, TransformedBounds bounds) {
        this.y = y;
        this.bounds = bounds;
    }

    /** The first rejection is terminal. Each guard decides whether forcePlace exempts its rule. */
    public void reject(String reason) {
        Objects.requireNonNull(reason, "Rejection reason");
        if (rejection == null) {
            rejection = reason;
        }
    }

    public boolean isRejected() {
        return rejection != null;
    }

    public int heightOrRejected() {
        return isRejected() ? -1 : y;
    }
}

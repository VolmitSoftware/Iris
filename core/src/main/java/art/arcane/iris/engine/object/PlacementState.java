package art.arcane.iris.engine.object;

import art.arcane.iris.engine.object.PlacementContext.TransformedBounds;

final class PlacementState {
    int y;
    TransformedBounds bounds;
    boolean bail;

    PlacementState(int y, TransformedBounds bounds) {
        this.y = y;
        this.bounds = bounds;
    }
}

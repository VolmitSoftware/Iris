package com.volmit.example.placement;

import com.volmit.placement.PlacementRequest;
import com.volmit.geometry.BlockPosition;

/** The world position the object's local anchor is placed at. */
public record AnchorResult(BlockPosition anchor) {
    public BlockPosition worldPosition(PlacementRequest request, BlockPosition local) {
        return new PlacementRequest(request.object(), anchor).worldPosition(local);
    }
}

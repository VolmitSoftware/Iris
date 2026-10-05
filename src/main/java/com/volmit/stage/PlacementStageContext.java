package com.volmit.stage;

import com.volmit.placement.PlacementAccess;
import com.volmit.placement.PlacementContext;
import com.volmit.placement.PlacementRequest;

/** One placement attempt. Writes go to a buffered edit committed only if no stage rejects
 * and none throws. All stages of an attempt share its inputs, including the RNG, in resolved order.
 */
public interface PlacementStageContext extends PlacementContext, StageContext {
    PlacementRequest request();

    /** Buffered access bounded by the prepared edit; reads include earlier writes. */
    PlacementAccess access();

    /** Stops the attempt after the current stage (or current block); nothing is committed.
     * The first reason is kept.
     */
    void reject(String reason);
}

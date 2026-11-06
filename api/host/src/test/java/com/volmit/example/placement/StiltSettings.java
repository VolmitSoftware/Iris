package com.volmit.example.placement;

import com.volmit.IrisFeature;
import com.volmit.config.Desc;
import com.volmit.config.Feature;
import com.volmit.config.Level;
import com.volmit.config.MinNumber;

@Desc("Fills the space below an object down to the ground")
@Feature(id = "iris:stilt", name = "Stilt", key = "stilt", targets = Level.PLACEMENT)
public final class StiltSettings implements IrisFeature {
    @Desc("Maximum blocks filled below each lowest object block")
    @MinNumber(1)
    private int maxDepth = 16;

    public StiltSettings() {}

    public StiltSettings(int maxDepth) {
        this.maxDepth = maxDepth;
    }

    public int maxDepth() {
        return maxDepth;
    }
}

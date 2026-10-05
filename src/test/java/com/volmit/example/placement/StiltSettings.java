package com.volmit.example.placement;

import com.volmit.FeatureAttachment;
import com.volmit.IrisFeature;
import com.volmit.config.Desc;
import com.volmit.config.Feature;
import com.volmit.config.MinNumber;

@Feature(id = "iris:stilt", name = "Stilt", field = "stilt", targets = FeatureAttachment.Target.OBJECT_PLACEMENT)
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

package com.volmit.example.placement;

import com.volmit.FeatureAttachment;
import com.volmit.IrisFeature;
import com.volmit.config.Feature;
import com.volmit.config.MinNumber;

@Feature(id = "example:moss", name = "Moss", targets = FeatureAttachment.Target.OBJECT_PLACEMENT)
public final class MossSettings implements IrisFeature {
    @MinNumber(0)
    private int radius;

    public MossSettings() {}

    public MossSettings(int radius) {
        this.radius = radius;
    }

    public int radius() {
        return radius;
    }
}

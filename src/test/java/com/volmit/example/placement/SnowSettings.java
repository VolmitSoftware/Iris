package com.volmit.example.placement;

import com.volmit.FeatureAttachment;
import com.volmit.IrisFeature;
import com.volmit.config.Feature;
import com.volmit.config.MaxNumber;
import com.volmit.config.MinNumber;

@Feature(id = "iris:snow", name = "Snow", field = "snow", targets = FeatureAttachment.Target.OBJECT_PLACEMENT)
public final class SnowSettings implements IrisFeature {
    @MinNumber(1)
    @MaxNumber(8)
    private int layers = 1;

    public SnowSettings() {}

    public SnowSettings(int layers) {
        this.layers = layers;
    }

    public int layers() {
        return layers;
    }
}

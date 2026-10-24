package com.volmit.example.placement;

import com.volmit.IrisFeature;
import com.volmit.config.Feature;
import com.volmit.config.Level;
import com.volmit.config.MinNumber;

@Feature(id = "example:moss", name = "Moss", key = "moss", targets = Level.PLACEMENT)
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

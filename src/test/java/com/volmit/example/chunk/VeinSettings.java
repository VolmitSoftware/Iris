package com.volmit.example.chunk;

import com.volmit.FeatureAttachment;
import com.volmit.IrisFeature;
import com.volmit.config.Desc;
import com.volmit.config.Feature;

@Desc("Plans a horizontal ore vein from the centre of each chunk, which may run into the next chunk")
@Feature(id = "example:veins", name = "Veins", targets = {FeatureAttachment.Target.DIMENSION, FeatureAttachment.Target.BIOME})
public final class VeinSettings implements IrisFeature {
    @Desc("Blocks along X, starting at the chunk's centre column")
    private int length = 12;

    @Desc("Height of the vein above the dimension's bottom")
    private int y = 4;

    public VeinSettings() {}

    public VeinSettings(int length, int y) {
        this.length = length;
        this.y = y;
    }

    public int length() {
        return length;
    }

    public int y() {
        return y;
    }
}

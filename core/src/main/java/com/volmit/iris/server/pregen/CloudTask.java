package com.volmit.iris.server.pregen;

import com.volmit.iris.core.pregenerator.PregenTask;
import com.volmit.iris.util.collection.KList;
import com.volmit.iris.util.math.Position2;
import com.volmit.iris.util.math.Spiraled;
import lombok.Builder;
import lombok.EqualsAndHashCode;
import lombok.ToString;

import java.util.Comparator;
import java.util.Map;

@ToString
@Builder(builderMethodName = "couldBuilder")
@EqualsAndHashCode(callSuper = true)
public class CloudTask extends PregenTask {
    @Builder.Default
    private boolean resetCache = false;
    @Builder.Default
    private boolean gui = false;
    @Builder.Default
    private Position2 center = new Position2(0, 0);
    @Builder.Default
    private int width = 1;
    @Builder.Default
    private int height = 1;
    private int distance;

    private CloudTask(boolean resetCache, boolean gui, Position2 center, int width, int height, int distance) {
        super(resetCache, gui, center, width, height);
        this.resetCache = resetCache;
        this.gui = gui;
        this.center = center;
        this.width = width;
        this.height = height;

        int d = distance & 31;
        if (d > 0) d = 32 - d;
        this.distance = 32 + d + distance >> 5;
    }

    @Override
    public void iterateRegions(Spiraled s) {
        var c = Comparator.comparingInt(DPos2::distance);
        for (int oX = 0; oX < distance; oX++) {
            for (int oZ = 0; oZ < distance; oZ++) {
                var p = new KList<DPos2>();
                for (int x = -width; x <= width - oX; x+=distance) {
                    for (int z = -height; z <= height - oZ; z+=distance) {
                        s.on(x + oX, z + oZ);
                        //p.add(new DPos2(x + oX, z + oZ));
                    }
                }
                p.sort(c);
                p.forEach(i -> i.on(s));
            }
        }
    }

    private record DPos2(int x, int z, int distance) {
        private DPos2(int x, int z) {
            this(x, z, x * x + z * z);
        }

        public void on(Spiraled s) {
            s.on(x, z);
        }
    }
}

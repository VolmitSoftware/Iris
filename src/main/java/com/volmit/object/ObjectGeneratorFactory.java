package com.volmit.object;

import com.volmit.IrisFeature;

@FunctionalInterface
public interface ObjectGeneratorFactory<F extends IrisFeature> {
    ObjectGenerator create(F feature);
}

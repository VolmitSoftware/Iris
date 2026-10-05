package com.volmit.placement;

import com.volmit.IrisFeature;

@FunctionalInterface
public interface PlacementModifierFactory<F extends IrisFeature> {
    PlacementModifier create(F feature);
}

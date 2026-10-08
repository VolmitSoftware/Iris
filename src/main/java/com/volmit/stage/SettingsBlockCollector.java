package com.volmit.stage;

import com.volmit.IrisFeature;
import com.volmit.config.FeatureSettings.Entry;
import java.util.List;

/** Body of a conditional accumulating block stage. {@code settings} holds every entry configured for
 * the placement, in level order, and is never empty.
 */
@FunctionalInterface
public interface SettingsBlockCollector<S extends IrisFeature, O> {
    void collect(BlockStageContext context, List<Entry<S>> settings, O accumulator);
}

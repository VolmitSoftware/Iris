package com.volmit.stage;

import com.volmit.IrisFeature;
import com.volmit.config.FeatureSettings.Entry;
import java.util.List;

/** Body of a conditional stage with output. {@code settings} holds every entry configured for the
 * invocation, in level order, and is never empty.
 */
@FunctionalInterface
public interface SettingsFunction<C, S extends IrisFeature, O> {
    O run(C context, List<Entry<S>> settings);
}

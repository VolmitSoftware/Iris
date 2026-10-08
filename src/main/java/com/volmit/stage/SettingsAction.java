package com.volmit.stage;

import com.volmit.IrisFeature;
import com.volmit.config.FeatureSettings.Entry;
import java.util.List;

/** Body of a conditional stage without output. {@code settings} holds every entry configured for the
 * invocation, in level order, and is never empty.
 */
@FunctionalInterface
public interface SettingsAction<C, S extends IrisFeature> {
    void run(C context, List<Entry<S>> settings);
}

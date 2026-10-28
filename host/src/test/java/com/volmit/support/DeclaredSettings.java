package com.volmit.support;

import com.volmit.IrisFeature;
import com.volmit.config.FeatureSettings.Entry;
import com.volmit.config.PackSettings;
import com.volmit.config.SettingsStack;
import com.volmit.resource.PackResources;
import java.util.List;

/** A pack's settings as a fixed list of declarations, for reach tests. */
public final class DeclaredSettings implements PackSettings {
    public static final DeclaredSettings NONE = new DeclaredSettings();

    private final List<Entry<?>> entries;

    public DeclaredSettings(Entry<?>... entries) {
        this.entries = List.of(entries);
    }

    @Override
    @SuppressWarnings("unchecked") // Filtered by the requested type.
    public <F extends IrisFeature> SettingsStack<F> declared(Class<F> featureType) {
        return SettingsStack.of(entries.stream()
                .filter(entry -> featureType.isInstance(entry.value()))
                .map(entry -> (Entry<F>) entry)
                .toList());
    }

    @Override
    public PackResources resources() {
        return new EmptyResources();
    }
}

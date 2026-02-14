package art.arcane.iris.util.matter.slices.container;

import art.arcane.iris.core.loader.IrisData;
import art.arcane.iris.core.loader.IrisRegistrant;

public abstract class RegistrantContainer<T extends IrisRegistrant> {
    private final Class<T> type;
    private final String loadKey;

    public RegistrantContainer(Class<T> type, String loadKey) {
        this.type = type;
        this.loadKey = loadKey;
    }

    public T load(IrisData data) {
        return (T) data.getLoaders().get(type).load(loadKey);
    }

    public String getLoadKey() {
        return loadKey;
    }
}

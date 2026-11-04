package art.arcane.iris.generation.mantle;

import art.arcane.iris.structure.object.IObjectPlacer;
import art.arcane.iris.structure.object.ObjectWaterRule;
import org.jetbrains.annotations.Nullable;

public interface ObjectPassPlacer extends IObjectPlacer {
    <T> @Nullable T getDataIfPresent(int x, int y, int z, Class<T> type);

    byte[] getCarvedColumn(int x, int z, int height);

    default int beginObjectPlacement(ObjectWaterRule rule) {
        return -1;
    }

    default void recordObjectPlacement(int checkpoint) {
    }

    default void endObjectPlacement() {
    }

    @Override
    default <T> @Nullable T getData(int x, int y, int z, Class<T> type) {
        return getDataIfPresent(x, y, z, type);
    }
}

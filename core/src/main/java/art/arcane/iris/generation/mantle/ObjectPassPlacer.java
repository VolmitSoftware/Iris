package art.arcane.iris.generation.mantle;

import art.arcane.iris.structure.object.IObjectPlacer;
import org.jetbrains.annotations.Nullable;

public interface ObjectPassPlacer extends IObjectPlacer {
    <T> @Nullable T getDataIfPresent(int x, int y, int z, Class<T> type);

    byte[] getCarvedColumn(int x, int z, int height);

    @Override
    default <T> @Nullable T getData(int x, int y, int z, Class<T> type) {
        return getDataIfPresent(x, y, z, type);
    }
}

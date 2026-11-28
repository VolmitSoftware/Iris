package art.arcane.iris.testsupport;

import art.arcane.iris.generation.biome.PaletteLayerRuntime;
import art.arcane.iris.generation.decoration.DecoratorRuntime;
import art.arcane.iris.generation.runtime.PackRuntime;
import art.arcane.iris.pack.loading.IrisData;

import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.withSettings;

/**
 * Mocked pack data that still owns a standalone runtime scope, the way loaded pack data does.
 */
public final class MockPackData {
    private MockPackData() {
    }

    public static IrisData create() {
        return withStandaloneRuntime(mock(IrisData.class));
    }

    public static IrisData stubOnly() {
        return withStandaloneRuntime(mock(IrisData.class, withSettings().stubOnly()));
    }

    /** Matches a runtime scope of this pack data, without touching the data mock while stubbing. */
    public static PackRuntime runtimeOf(IrisData data) {
        return argThat(runtime -> runtime != null && runtime.data() == data);
    }

    /** Matches a palette layer runtime built in a runtime scope of this pack data. */
    public static PaletteLayerRuntime paletteLayerRuntimeOf(IrisData data) {
        return argThat(runtime -> runtime != null && runtime.packRuntime().data() == data);
    }

    /** Matches a decorator runtime built in a runtime scope of this pack data. */
    public static DecoratorRuntime decoratorRuntimeOf(IrisData data) {
        return argThat(runtime -> runtime != null && runtime.packRuntime().data() == data);
    }

    private static IrisData withStandaloneRuntime(IrisData data) {
        doReturn(PackRuntime.standalone(data)).when(data).standalonePackRuntime();
        return data;
    }
}

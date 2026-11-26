package art.arcane.iris.testsupport;

import art.arcane.iris.generation.runtime.PackRuntime;
import art.arcane.iris.pack.loading.IrisData;

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

    private static IrisData withStandaloneRuntime(IrisData data) {
        doReturn(PackRuntime.standalone(data)).when(data).standalonePackRuntime();
        return data;
    }
}

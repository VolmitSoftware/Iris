package art.arcane.iris.core.modules;

import art.arcane.iris.core.loader.IrisData;
import com.volmit.noise.NoiseFactory;

import java.io.File;
import java.util.List;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/** Iris's own noise factory over an empty pack, for tests that need the very noise generation reads. */
public final class IrisTestNoise {
    private IrisTestNoise() {
    }

    public static NoiseFactory factory() {
        IrisData data = mock(IrisData.class);
        when(data.getDataFolder()).thenReturn(new File("test-pack"));
        return new IrisNoiseFactory(data, new IrisPackResources(data, List::of));
    }
}

package art.arcane.iris.testsupport;

import art.arcane.iris.generation.runtime.Engine;
import art.arcane.iris.generation.runtime.PackRuntime;
import art.arcane.iris.pack.loading.IrisData;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public final class RunningEnginePackData {
    private RunningEnginePackData() {
    }

    public static IrisData create() {
        IrisData packData = mock(IrisData.class);
        Engine engine = mock(Engine.class);
        when(engine.getPackRuntime()).thenReturn(PackRuntime.forEngine(engine, packData));
        when(packData.getEngine()).thenReturn(engine);
        return packData;
    }
}

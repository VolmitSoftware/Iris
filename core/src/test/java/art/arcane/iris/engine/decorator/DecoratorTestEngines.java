package art.arcane.iris.engine.decorator;

import art.arcane.iris.core.loader.IrisData;
import art.arcane.iris.engine.framework.Engine;
import art.arcane.iris.engine.framework.EngineGeneration;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/** Gives a mocked engine the world state decorators ask for, with the decorators a test chooses. */
public final class DecoratorTestEngines {
    private DecoratorTestEngines() {
    }

    /** Gives {@code engine} a generation, so that the things built for it can ask for the world's state; sets mocked data when it has none. */
    public static EngineGeneration withGeneration(Engine engine) {
        if (engine.getData() == null) {
            when(engine.getData()).thenReturn(mock(IrisData.class));
        }
        EngineGeneration generation = new EngineGeneration(engine);
        when(engine.getGeneration()).thenReturn(generation);
        return generation;
    }

    /** Makes {@code engine} keep {@code decorators} as its world's decorators. */
    static void keepDecorators(Engine engine, BiomeDecorators decorators) {
        withGeneration(engine).state(BiomeDecorators.class, ignored -> decorators);
    }
}

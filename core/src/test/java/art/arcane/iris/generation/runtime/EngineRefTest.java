package art.arcane.iris.generation.runtime;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.mock;

public class EngineRefTest {
    @Test
    public void refsToTheSameEngineAreEqual() {
        Engine engine = mock(Engine.class);

        EngineRef first = EngineRef.of(engine);
        EngineRef second = EngineRef.of(engine);

        assertEquals(first, second);
        assertEquals(first.hashCode(), second.hashCode());
        assertTrue(first.refersTo(engine));
    }

    @Test
    public void refsToDifferentEnginesDiffer() {
        Engine engine = mock(Engine.class);
        Engine otherEngine = mock(Engine.class);

        assertNotEquals(EngineRef.of(engine), EngineRef.of(otherEngine));
        assertFalse(EngineRef.of(engine).refersTo(otherEngine));
    }

    @Test
    public void missingEngineOnlyMatchesMissingEngine() {
        Engine engine = mock(Engine.class);

        assertEquals(EngineRef.of(null), EngineRef.of(null));
        assertTrue(EngineRef.of(null).refersTo(null));
        assertFalse(EngineRef.of(null).refersTo(engine));
        assertFalse(EngineRef.of(engine).refersTo(null));
        assertNotEquals(EngineRef.of(null), EngineRef.of(engine));
    }
}

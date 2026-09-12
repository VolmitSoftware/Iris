package art.arcane.iris.command;

import art.arcane.volmlib.util.director.compat.DirectorEngineFactory;
import art.arcane.volmlib.util.director.runtime.DirectorInvocation;
import art.arcane.volmlib.util.director.runtime.DirectorRuntimeEngine;
import art.arcane.volmlib.util.director.runtime.DirectorSender;
import org.junit.Test;

import java.util.List;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class CommandPregenCompletionContractTest {
    @Test
    public void startCompletesEveryConfigurableValueWithItsCanonicalKey() {
        DirectorRuntimeEngine engine = DirectorEngineFactory.create(new CommandPregen());
        DirectorInvocation invocation = new DirectorInvocation(new TestSender(), "iris", List.of("start", ""));

        List<String> suggestions = engine.tabComplete(invocation);

        assertTrue(suggestions.contains("radius="));
        assertTrue(suggestions.contains("world="));
        assertTrue(suggestions.contains("center="));
        assertTrue(suggestions.contains("gui=false"));
        assertTrue(suggestions.contains("gui=true"));
        assertTrue(suggestions.contains("serial=false"));
        assertTrue(suggestions.contains("serial=true"));
        assertFalse(suggestions.contains("true"));
        assertFalse(suggestions.contains("false"));
    }

    private static final class TestSender implements DirectorSender {
        @Override
        public String getName() {
            return "test";
        }

        @Override
        public boolean isPlayer() {
            return false;
        }

        @Override
        public void sendMessage(String message) {
        }
    }
}

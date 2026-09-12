package art.arcane.iris.command.handlers;

import art.arcane.volmlib.util.director.exceptions.DirectorParsingException;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.junit.Test;

import java.lang.reflect.Proxy;
import java.util.List;

import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertThrows;

public class WorldHandlerTest {
    @Test
    public void resolvesOwnedIrisWorldByLogicalNameAndCanonicalKey() throws DirectorParsingException {
        World world = world("world_iris_irisworld", new NamespacedKey("iris", "irisworld"));
        WorldHandler handler = new TestWorldHandler(List.of(world));

        assertSame(world, handler.parse("irisworld", false));
        assertSame(world, handler.parse("iris:irisworld", false));
        assertThrows(DirectorParsingException.class, () -> handler.parse("minecraft:irisworld", false));
    }

    private static World world(String name, NamespacedKey key) {
        return (World) Proxy.newProxyInstance(
                World.class.getClassLoader(),
                new Class<?>[]{World.class},
                (proxy, method, arguments) -> switch (method.getName()) {
                    case "getName" -> name;
                    case "getKey" -> key;
                    case "equals" -> proxy == arguments[0];
                    case "hashCode" -> System.identityHashCode(proxy);
                    default -> defaultValue(method.getReturnType());
                }
        );
    }

    private static Object defaultValue(Class<?> type) {
        if (!type.isPrimitive()) {
            return null;
        }
        if (type == boolean.class) {
            return false;
        }
        if (type == char.class) {
            return '\0';
        }
        return 0;
    }

    private static final class TestWorldHandler extends WorldHandler {
        private final List<World> worlds;

        private TestWorldHandler(List<World> worlds) {
            this.worlds = worlds;
        }

        @Override
        protected List<World> worldOptions() {
            return worlds;
        }
    }
}

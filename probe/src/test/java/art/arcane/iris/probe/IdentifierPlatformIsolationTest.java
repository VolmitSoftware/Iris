package art.arcane.iris.probe;

import art.arcane.iris.integration.Identifier;
import org.junit.Test;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

public final class IdentifierPlatformIsolationTest {
    @Test
    public void equalityDoesNotResolveBukkitOnTheProbeRuntime() {
        ClassLoader classLoader = IdentifierPlatformIsolationTest.class.getClassLoader();
        assertThrows(ClassNotFoundException.class,
                () -> Class.forName("org.bukkit.NamespacedKey", false, classLoader));

        Identifier identifier = new Identifier("minecraft", "stone");
        assertTrue(identifier.equals(new Identifier("minecraft", "stone")));
        assertFalse(identifier.equals("minecraft:stone"));
    }
}

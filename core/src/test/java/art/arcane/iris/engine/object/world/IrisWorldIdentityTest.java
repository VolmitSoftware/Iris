package art.arcane.iris.engine.object.world;

import art.arcane.iris.engine.object.world.IrisWorld;
import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class IrisWorldIdentityTest {
    @Test
    public void usesPlatformIdentity() {
        IrisWorld world = IrisWorld.builder()
                .platformIdentity("iris:bukkit")
                .build();

        assertEquals("iris:bukkit", world.identity());
    }

    @Test
    public void preservesModdedIdentity() {
        IrisWorld world = IrisWorld.builder()
                .platformIdentity("minecraft:the_nether")
                .build();

        assertEquals("minecraft:the_nether", world.identity());
    }
}

package art.arcane.iris.world;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;

import java.util.Collection;
import java.util.List;

import static org.junit.Assert.assertEquals;

@RunWith(Parameterized.class)
public class IrisWorldIdentityTest {
    @Parameters(name = "{0}")
    public static Collection<Object[]> identities() {
        return List.of(
                new Object[]{"iris:bukkit"},
                new Object[]{"minecraft:the_nether"}
        );
    }

    private final String platformIdentity;

    public IrisWorldIdentityTest(String platformIdentity) {
        this.platformIdentity = platformIdentity;
    }

    @Test
    public void preservesThePlatformIdentity() {
        IrisWorld world = IrisWorld.builder()
                .platformIdentity(platformIdentity)
                .build();

        assertEquals(platformIdentity, world.identity());
    }
}

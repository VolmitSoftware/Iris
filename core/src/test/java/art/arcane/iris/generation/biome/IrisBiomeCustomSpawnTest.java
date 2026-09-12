package art.arcane.iris.generation.biome;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;

import java.util.Collection;
import java.util.List;

import static org.junit.Assert.assertEquals;

@RunWith(Parameterized.class)
public class IrisBiomeCustomSpawnTest {
    @Parameters(name = "{0} -> {1}")
    public static Collection<Object[]> keys() {
        return List.of(
                new Object[]{" Minecraft:Slime ", "minecraft:slime"},
                new Object[]{"Slime", "minecraft:slime"},
                new Object[]{"  ", null}
        );
    }

    private final String authored;
    private final String expectedKey;

    public IrisBiomeCustomSpawnTest(String authored, String expectedKey) {
        this.authored = authored;
        this.expectedKey = expectedKey;
    }

    @Test
    public void normalizesTheEntityKey() {
        IrisBiomeCustomSpawn spawn = new IrisBiomeCustomSpawn().setType(authored);

        assertEquals(expectedKey, spawn.getTypeKey());
    }
}

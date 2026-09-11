package art.arcane.iris.platform;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThrows;

public class BlockStateKeyTest {
    @Test
    public void namespaceIgnoresPropertyValues() {
        assertEquals("minecraft", BlockStateKey.namespace("stone"));
        assertEquals("minecraft", BlockStateKey.namespace("stone[variant=custom:stone]"));
        assertEquals("custom", BlockStateKey.namespace("custom:stone[variant=minecraft:stone]"));
        assertEquals("", BlockStateKey.namespace(":stone"));
    }

    @Test
    public void addsAndReplacesPropertiesInEncounterOrder() {
        assertEquals("minecraft:stairs[facing=east]",
                BlockStateKey.withProperty("minecraft:stairs", "facing", "east"));
        assertEquals("minecraft:stairs[facing=east,half=top]",
                BlockStateKey.withProperty("minecraft:stairs[facing=north,half=top]", "facing", "east"));
        assertEquals("minecraft:stairs[facing=north,half=top,waterlogged=true]",
                BlockStateKey.withProperty("minecraft:stairs[facing=north,half=top]", "waterlogged", "true"));
    }

    @Test
    public void retainsDuplicateAndMalformedEntryHandling() {
        assertEquals("custom:block[a=last,b=left=right,c=value]",
                BlockStateKey.withProperty("custom:block[ a = first ,invalid,b=left=right,a=last]", "c", "value"));
        assertEquals("stone[x=y]", BlockStateKey.withProperty("stone[]", "x", "y"));
        assertThrows(StringIndexOutOfBoundsException.class,
                () -> BlockStateKey.withProperty("stone[x=y", "x", "z"));
        assertThrows(NullPointerException.class, () -> BlockStateKey.namespace(null));
    }
}

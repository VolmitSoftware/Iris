package art.arcane.iris.world.entity;

import art.arcane.volmlib.util.math.RNG;
import org.bukkit.attribute.AttributeModifier;
import org.bukkit.inventory.meta.ItemMeta;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;

public class IrisAttributeModifierSeedTest {
    @Test
    public void repeatedSeedProducesIdenticalModifierKeyAmountAndOperation() {
        IrisAttributeModifier definition = new IrisAttributeModifier().setName("Custom Bonus")
                .setMinAmount(-3).setMaxAmount(7).setOperation("ADD_SCALAR");
        AttributeModifier first = definition.createModifier(new RNG(1337L));
        AttributeModifier repeated = definition.createModifier(new RNG(1337L));
        assertEquals(first.getKey(), repeated.getKey());
        assertEquals("minecraft:custom_bonus_a8f1288a2c8a93b8_b072351edfd5484c", first.getKey().toString());
        assertEquals(first.getAmount(), repeated.getAmount(), 0D);
        assertEquals(5.832726771624211D, first.getAmount(), 0D);
        assertEquals(AttributeModifier.Operation.ADD_SCALAR, first.getOperation());
        assertTrue(first.getKey().getKey().startsWith("custom_bonus_"));
        assertTrue(first.getAmount() >= -3D && first.getAmount() <= 7D);
    }

    @Test
    public void sameNamedModifiersRemainDistinctWithinOneSeededSequence() {
        IrisAttributeModifier definition = new IrisAttributeModifier().setName("Bonus");
        RNG rng = new RNG(1337L);
        AttributeModifier first = definition.createModifier(rng);
        AttributeModifier second = definition.createModifier(rng);
        assertNotEquals(first.getKey(), second.getKey());
        assertNotEquals(first.getKey(), definition.createModifier(new RNG(1338L)).getKey());
    }

    @Test
    public void zeroChanceNeverAppliesAModifier() {
        ItemMeta metadata = mock(ItemMeta.class);
        IrisAttributeModifier definition = new IrisAttributeModifier().setChance(0D);
        definition.apply(new RNG(1337L), metadata);
        verifyNoInteractions(metadata);
    }
}

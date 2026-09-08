package art.arcane.iris.engine.object;

import org.junit.Test;

import static org.junit.Assert.assertEquals;

/**
 * The gate walker drops a {@code vanillaDerivative} whose biome key is missing (policy table: entry DROPPED, falls back
 * to derivative). This pins the fallback the drop relies on; the drop itself is simulated by clearing the field, which
 * is exactly what the walker does.
 */
public class IrisBiomeDerivativeCompatTest {
    @Test
    public void droppedVanillaDerivativeFallsBackToDerivative() {
        IrisBiome biome = PackCompatFixtures.biome("cave/sulfur-grotto");
        biome.setDerivative("minecraft:dripstone_caves");
        biome.setVanillaDerivative("minecraft:sulfur_caves");

        assertEquals("minecraft:sulfur_caves", biome.getVanillaDerivativeKey());

        biome.setVanillaDerivative(null);

        assertEquals("minecraft:dripstone_caves", biome.getVanillaDerivativeKey());
    }

    @Test
    public void blankVanillaDerivativeFallsBackToDerivative() {
        IrisBiome biome = PackCompatFixtures.biome("cave/sulfur-grotto");
        biome.setDerivative("minecraft:dripstone_caves");
        biome.setVanillaDerivative("");

        assertEquals("minecraft:dripstone_caves", biome.getVanillaDerivativeKey());
    }
}

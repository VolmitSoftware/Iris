package art.arcane.iris.generation.biome;

import art.arcane.iris.generation.terrain.InferredType;

import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class IrisBiomeStructureDerivativeTest {
    @Test
    public void seaBiomeRejectsLandOnlyVanillaDerivativeForStructures() {
        IrisBiome biome = new IrisBiome()
                .setDerivative("minecraft:warm_ocean")
                .setVanillaDerivative("minecraft:desert")
                .setInferredType(InferredType.SEA);

        assertEquals("minecraft:desert", biome.getVanillaDerivativeKey());
        assertEquals("minecraft:the_void", biome.getStructureDerivativeKey());
    }

    @Test
    public void shoreBiomeRejectsLandOnlyVanillaDerivativeForStructures() {
        IrisBiome biome = new IrisBiome()
                .setDerivative("minecraft:warm_ocean")
                .setVanillaDerivative("minecraft:savanna")
                .setInferredType(InferredType.SHORE);

        assertEquals("minecraft:beach", biome.getStructureDerivativeKey());
    }

    @Test
    public void waterStructureDerivativesKeepTheirExactVariant() {
        IrisBiome deepOcean = new IrisBiome()
                .setVanillaDerivative("minecraft:deep_cold_ocean")
                .setInferredType(InferredType.SEA);
        IrisBiome snowyBeach = new IrisBiome()
                .setVanillaDerivative("minecraft:snowy_beach")
                .setInferredType(InferredType.SHORE);

        assertEquals("minecraft:deep_cold_ocean", deepOcean.getStructureDerivativeKey());
        assertEquals("minecraft:snowy_beach", snowyBeach.getStructureDerivativeKey());
    }

    @Test
    public void seaAndShoreDoNotBorrowEachOthersStructureBiomes() {
        IrisBiome seaBeach = new IrisBiome()
                .setVanillaDerivative("minecraft:beach")
                .setInferredType(InferredType.SEA);
        IrisBiome shoreOcean = new IrisBiome()
                .setVanillaDerivative("minecraft:ocean")
                .setInferredType(InferredType.SHORE);

        assertEquals("minecraft:the_void", seaBeach.getStructureDerivativeKey());
        assertEquals("minecraft:beach", shoreOcean.getStructureDerivativeKey());
    }

    @Test
    public void landAndModdedDerivativesRemainAuthoritative() {
        IrisBiome land = new IrisBiome()
                .setVanillaDerivative("minecraft:desert")
                .setInferredType(InferredType.LAND);
        IrisBiome moddedSea = new IrisBiome()
                .setVanillaDerivative("example:abyss")
                .setInferredType(InferredType.SEA);

        assertEquals("minecraft:desert", land.getStructureDerivativeKey());
        assertEquals("example:abyss", moddedSea.getStructureDerivativeKey());
    }
}

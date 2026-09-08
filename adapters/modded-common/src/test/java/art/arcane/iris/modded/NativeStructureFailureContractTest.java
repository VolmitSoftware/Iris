package art.arcane.iris.modded;

import art.arcane.iris.nativegen.NativeStructureGenerationException;
import net.minecraft.SharedConstants;
import net.minecraft.server.Bootstrap;
import org.junit.BeforeClass;
import org.junit.Test;

import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

public class NativeStructureFailureContractTest {
    @BeforeClass
    public static void bootstrapMinecraftRegistries() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    @Test
    public void globalStructureDisableRefusesGeneratorBinding() {
        IrisModdedChunkGenerator.requireGlobalStructureGeneration(true, "overworld:overworld");

        IllegalStateException error = assertThrows(
                IllegalStateException.class,
                () -> IrisModdedChunkGenerator.requireGlobalStructureGeneration(
                        false, "overworld:overworld"));

        assertTrue(error.getMessage().contains("overworld:overworld"));
        assertTrue(error.getMessage().contains("generate-structures=false"));
        assertTrue(error.getMessage().contains("importedStructures.disabled"));
        assertTrue(error.getMessage().contains("importedStructures.disabledExact"));
    }

    @Test
    public void structureFailurePreservesPhaseIdentityChunkAndCause() {
        IllegalArgumentException cause = new IllegalArgumentException("broken placement");
        NativeStructureGenerationException error = NativeStructureGenerationException.failure(
                "placement", "minecraft:monument", 12, -8, cause);

        assertSame(cause, error.getCause());
        assertTrue(error.getMessage().contains("placement"));
        assertTrue(error.getMessage().contains("minecraft:monument"));
        assertTrue(error.getMessage().contains("12,-8"));
        assertTrue(error.getMessage().contains("aborted"));
    }
}

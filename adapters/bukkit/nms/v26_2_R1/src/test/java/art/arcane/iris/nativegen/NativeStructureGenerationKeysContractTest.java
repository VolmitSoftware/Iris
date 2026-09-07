package art.arcane.iris.nativegen;

import net.minecraft.SharedConstants;
import net.minecraft.core.Vec3i;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.level.levelgen.structure.placement.RandomSpreadStructurePlacement;
import net.minecraft.world.level.levelgen.structure.placement.RandomSpreadType;
import net.minecraft.world.level.levelgen.structure.placement.StructurePlacement;
import org.junit.BeforeClass;
import org.junit.Test;

import java.util.Optional;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class NativeStructureGenerationKeysContractTest {
    @BeforeClass
    public static void bootstrapMinecraft() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    @Test
    public void frequencyGateUsesMappedAccessorAndRejectsNonPositivePlacements() {
        assertFalse(NativeStructureGenerationKeys.isEnabledPlacement(placement(0.0F)));
        assertFalse(NativeStructureGenerationKeys.isEnabledPlacement(placement(-0.1F)));
        assertFalse(NativeStructureGenerationKeys.isEnabledPlacement(placement(Float.NaN)));
        assertTrue(NativeStructureGenerationKeys.isEnabledPlacement(placement(0.25F)));
    }

    private static RandomSpreadStructurePlacement placement(float frequency) {
        return new RandomSpreadStructurePlacement(
                Vec3i.ZERO,
                StructurePlacement.FrequencyReductionMethod.DEFAULT,
                frequency,
                1,
                Optional.empty(),
                2,
                1,
                RandomSpreadType.LINEAR);
    }
}

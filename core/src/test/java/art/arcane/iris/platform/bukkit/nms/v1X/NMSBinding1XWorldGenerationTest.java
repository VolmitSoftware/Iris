package art.arcane.iris.platform.bukkit.nms.v1X;

import art.arcane.iris.platform.bukkit.nms.INMSBinding;
import org.bukkit.World;
import org.junit.Test;

import java.util.concurrent.CompletableFuture;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

public class NMSBinding1XWorldGenerationTest {
    @Test
    public void limitedBindingRefusesIrisWorldInitialization() {
        NMSBinding1X binding = new NMSBinding1X();

        assertFalse(binding.supportsIrisWorldGeneration());
        IllegalStateException error = assertThrows(
                IllegalStateException.class,
                () -> binding.inject(1L, null, null));

        assertTrue(error.getMessage().contains("general.disableNMS=true"));
    }

    @Test
    public void structureRegistryHooksAreRequiredBindingMethods() throws NoSuchMethodException {
        assertFalse(INMSBinding.class.getMethod("getStructureKeys").isDefault());
        assertFalse(INMSBinding.class.getMethod("getStructureSetKeys").isDefault());
        assertFalse(INMSBinding.class.getMethod("getReachableStructureKeys", World.class).isDefault());
        assertFalse(INMSBinding.class.getMethod("getStructureBiomeKeys", String.class).isDefault());
        assertFalse(INMSBinding.class.getMethod("getPossibleBiomeKeys", World.class).isDefault());
    }

    @Test
    public void limitedBindingReportsCompletedStudioStructureBootstrap() throws NoSuchMethodException {
        NMSBinding1X binding = new NMSBinding1X();

        CompletableFuture<Void> completion = binding.completeStudioStructureBootstrap(null);

        assertEquals(CompletableFuture.class, INMSBinding.class
                .getMethod("completeStudioStructureBootstrap", World.class)
                .getReturnType());
        assertTrue(completion.isDone());
        assertFalse(completion.isCompletedExceptionally());
    }

    @Test
    public void limitedBindingRefusesStructureRegistryHooks() {
        NMSBinding1X binding = new NMSBinding1X();

        IllegalStateException structureKeys = assertThrows(
                IllegalStateException.class, binding::getStructureKeys);
        IllegalStateException structureSetKeys = assertThrows(
                IllegalStateException.class, binding::getStructureSetKeys);
        IllegalStateException reachableKeys = assertThrows(
                IllegalStateException.class, () -> binding.getReachableStructureKeys(null));
        IllegalStateException structureBiomeKeys = assertThrows(
                IllegalStateException.class, () -> binding.getStructureBiomeKeys("minecraft:village"));
        IllegalStateException possibleBiomeKeys = assertThrows(
                IllegalStateException.class, () -> binding.getPossibleBiomeKeys(null));

        assertTrue(structureKeys.getMessage().contains("read registered structure keys"));
        assertTrue(structureSetKeys.getMessage().contains("read registered structure-set keys"));
        assertTrue(reachableKeys.getMessage().contains("resolve reachable structures"));
        assertTrue(structureBiomeKeys.getMessage().contains("resolve structure biome keys"));
        assertTrue(possibleBiomeKeys.getMessage().contains("resolve possible biome keys"));
    }
}

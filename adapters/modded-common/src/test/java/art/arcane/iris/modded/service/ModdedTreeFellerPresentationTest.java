package art.arcane.iris.modded.service;

import net.minecraft.SharedConstants;
import net.minecraft.core.component.DataComponentMap;
import net.minecraft.core.component.DataComponents;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.item.ItemStack;
import art.arcane.volmlib.nativelib.minecraft26_2.modded.NativeItemStack;
import net.minecraft.world.item.Items;
import org.junit.BeforeClass;
import org.junit.Test;

import java.util.List;

import static org.junit.Assert.assertEquals;

public class ModdedTreeFellerPresentationTest {
    @BeforeClass
    public static void bootstrapMinecraftRegistries() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
        DataComponentMap stackComponents = DataComponentMap.builder()
                .set(DataComponents.MAX_STACK_SIZE, 64)
                .build();
        if (!Items.OAK_LOG.builtInRegistryHolder().areComponentsBound()) {
            Items.OAK_LOG.builtInRegistryHolder().bindComponents(stackComponents);
        }
        if (!Items.BIRCH_LOG.builtInRegistryHolder().areComponentsBound()) {
            Items.BIRCH_LOG.builtInRegistryHolder().bindComponents(stackComponents);
        }
    }

    @Test
    public void pulseSizingKeepsErosionReadableAndBounded() {
        assertEquals(4, ModdedTreeFellerPresentation.blocksPerPulse(1));
        assertEquals(4, ModdedTreeFellerPresentation.blocksPerPulse(240));
        assertEquals(10, ModdedTreeFellerPresentation.blocksPerPulse(600));
        assertEquals(64, ModdedTreeFellerPresentation.blocksPerPulse(100_000));
        assertEquals(4, ModdedTreeFellerPresentation.effectStride(64));
    }

    @Test
    public void compatibleDropsConsolidateWithoutExceedingStackLimits() {
        List<NativeItemStack> drops = ModdedTreeFellerPresentation.consolidateDrops(List.of(
                NativeItemStack.of(new ItemStack(Items.OAK_LOG, 48)),
                NativeItemStack.of(new ItemStack(Items.OAK_LOG, 48)),
                NativeItemStack.of(new ItemStack(Items.BIRCH_LOG, 3))
        ));

        assertEquals(3, drops.size());
        assertEquals(64, drops.get(0).count());
        assertEquals(32, drops.get(1).count());
        assertEquals(3, drops.get(2).count());
    }
}

package art.arcane.volmlib.nativelib.minecraft26_2.modded;

import art.arcane.volmlib.nativelib.terrain.NativeWorld;
import net.minecraft.SharedConstants;
import net.minecraft.core.component.DataComponentMap;
import net.minecraft.core.component.DataComponents;
import net.minecraft.server.Bootstrap;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.InventoryMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.junit.BeforeClass;
import org.junit.Test;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Collection;
import net.minecraft.core.Holder;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class NativeHarvestSessionTest {
    @BeforeClass
    public static void bootstrap() throws ReflectiveOperationException {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
        if (!Items.DIAMOND_AXE.builtInRegistryHolder().areComponentsBound()) {
            Items.DIAMOND_AXE.builtInRegistryHolder().bindComponents(DataComponentMap.builder()
                    .set(DataComponents.MAX_STACK_SIZE, 1).set(DataComponents.MAX_DAMAGE, 1561)
                    .set(DataComponents.DAMAGE, 0).build());
        }
        Method bindTags = Holder.Reference.class.getDeclaredMethod("bindTags", Collection.class);
        bindTags.setAccessible(true);
        bindTags.invoke(Items.DIAMOND_AXE.builtInRegistryHolder(), List.of(ItemTags.AXES));
    }

    @Test
    public void normalizesVanillaOriginDamageAndHonorsPreservation() throws Exception {
        Fixture fixture = Fixture.create(8);
        fixture.item().get().setDamageValue(9);
        assertTrue(fixture.session().normalizeOriginTool(true));
        assertEquals(8, fixture.item().get().getDamageValue());
    }

    @Test
    public void failedDestructionRefundsOnlyTheReservedTool() throws Exception {
        Fixture fixture = Fixture.create(8);
        assertTrue(fixture.session().normalizeOriginTool(false));
        NativeHarvestSession.Reservation reservation = fixture.session().reserveToolDamage(false);
        assertNotNull(reservation);
        assertEquals(10, fixture.item().get().getDamageValue());
        fixture.session().refundToolDamage(reservation);
        assertEquals(9, fixture.item().get().getDamageValue());

        NativeHarvestSession.Reservation second = fixture.session().reserveToolDamage(false);
        ItemStack replacement = new ItemStack(Items.DIAMOND_AXE);
        replacement.setDamageValue(20);
        fixture.item().set(replacement);
        fixture.session().refundToolDamage(second);
        assertEquals(20, fixture.item().get().getDamageValue());
        assertFalse(fixture.session().active());
    }

    @Test
    public void breakingReservationCanBeRefundedAfterFailedDestruction() throws Exception {
        Fixture fixture = Fixture.create(1559);
        assertTrue(fixture.session().normalizeOriginTool(false));
        NativeHarvestSession.Reservation reservation = fixture.session().reserveToolDamage(false);
        assertNotNull(reservation);
        assertTrue(reservation.broke());
        assertTrue(fixture.item().get().isEmpty());
        fixture.session().refundToolDamage(reservation);
        assertEquals(1560, fixture.item().get().getDamageValue());
    }

    private record Fixture(NativeHarvestSession session, AtomicReference<ItemStack> item) {
        private static Fixture create(int damage) throws Exception {
            ServerLevel level = mock(ServerLevel.class);
            ServerPlayer player = mock(ServerPlayer.class);
            Inventory inventory = mock(Inventory.class);
            NativeWorld world = mock(NativeWorld.class);
            when(world.nativeHandle()).thenReturn(level);
            when(player.level()).thenReturn(level);
            when(player.getInventory()).thenReturn(inventory);
            ItemStack tool = new ItemStack(Items.DIAMOND_AXE);
            tool.setDamageValue(damage);
            AtomicReference<ItemStack> item = new AtomicReference<>(tool);
            when(inventory.getSelectedItem()).thenAnswer(invocation -> item.get());
            when(inventory.getItem(0)).thenAnswer(invocation -> item.get());
            doAnswer(invocation -> { item.set(invocation.getArgument(0)); return null; })
                    .when(inventory).setSelectedItem(any(ItemStack.class));
            doAnswer(invocation -> { item.set(invocation.getArgument(1)); return null; })
                    .when(inventory).setItem(eq(0), any(ItemStack.class));
            Field menu = player.getClass().getField("inventoryMenu");
            menu.setAccessible(true);
            menu.set(player, mock(InventoryMenu.class));
            return new Fixture(new NativeHarvestSession(world, NativeProtocolPlayer.fromHandle(player)), item);
        }
    }
}

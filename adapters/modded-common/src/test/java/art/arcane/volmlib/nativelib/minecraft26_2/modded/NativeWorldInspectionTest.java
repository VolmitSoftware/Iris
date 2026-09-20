package art.arcane.volmlib.nativelib.minecraft26_2.modded;

import art.arcane.volmlib.nativelib.terrain.NativeBlockPoint;
import art.arcane.volmlib.nativelib.terrain.NativeWorld;
import net.minecraft.SharedConstants;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.component.DataComponentMap;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.Bootstrap;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.entity.SpawnerBlockEntity;
import org.junit.BeforeClass;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.doReturn;

public class NativeWorldInspectionTest {
    @BeforeClass
    public static void bootstrap() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
        if (!Items.STONE.builtInRegistryHolder().areComponentsBound()) {
            Items.STONE.builtInRegistryHolder().bindComponents(DataComponentMap.builder()
                    .set(DataComponents.MAX_STACK_SIZE, 64).build());
        }
    }

    @Test
    public void preservesSpawnerEntityMetadataAndMissingTileState() {
        ServerLevel level = mock(ServerLevel.class);
        NativeWorld world = mock(NativeWorld.class);
        when(world.nativeHandle()).thenReturn(level);
        when(level.registryAccess()).thenReturn(mock(RegistryAccess.class));
        SpawnerBlockEntity spawner = mock(SpawnerBlockEntity.class);
        doReturn(BuiltInRegistries.BLOCK_ENTITY_TYPE.getValue(Identifier.parse("minecraft:mob_spawner"))).when(spawner).getType();
        CompoundTag entity = new CompoundTag();
        entity.putString("id", "minecraft:zombie");
        CompoundTag spawnData = new CompoundTag();
        spawnData.put("entity", entity);
        CompoundTag tile = new CompoundTag();
        tile.put("SpawnData", spawnData);
        when(spawner.saveWithoutMetadata(any(HolderLookup.Provider.class))).thenReturn(tile);
        when(level.getBlockEntity(BlockPos.ZERO)).thenReturn(spawner);
        NativeWorldInspection inspection = new NativeWorldInspection(world);
        NativeWorldInspection.BlockEntityDetails details = inspection.blockEntity(new NativeBlockPoint(0, 0, 0));
        assertEquals("minecraft:mob_spawner", details.type());
        assertEquals("minecraft:zombie", details.spawnedEntity());
        assertNull(details.lootTable());
        assertNull(inspection.blockEntity(new NativeBlockPoint(1, 0, 0)));
    }

    @Test
    public void distinguishesEmptyHandAndBlockItemState() {
        ServerPlayer handle = mock(ServerPlayer.class);
        NativeProtocolPlayer player = NativeProtocolPlayer.fromHandle(handle);
        when(handle.getMainHandItem()).thenReturn(ItemStack.EMPTY);
        assertNull(NativeWorldInspection.heldItem(player));
        ItemStack stone = new ItemStack(Items.STONE, 3);
        when(handle.getMainHandItem()).thenReturn(stone);
        NativeWorldInspection.HeldItem item = NativeWorldInspection.heldItem(player);
        assertEquals("minecraft:stone", item.key());
        assertEquals("minecraft:stone", item.blockState());
        assertEquals(3, item.count());
    }
}

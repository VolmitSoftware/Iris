/*
 * Iris is a World Generator for Minecraft Servers
 * Copyright (c) 2026 Arcane Arts (Volmit Software)
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

package art.arcane.iris.modded;

import art.arcane.iris.core.content.ContentLoot;
import art.arcane.iris.core.content.ContentServices;
import art.arcane.iris.engine.framework.Engine;
import art.arcane.iris.engine.framework.LootResolver;
import art.arcane.iris.spi.IrisLogging;
import art.arcane.volmlib.util.collection.KList;
import art.arcane.volmlib.util.mantle.runtime.MantleChunk;
import art.arcane.volmlib.util.math.RNG;
import art.arcane.volmlib.util.matter.Matter;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Container;
import net.minecraft.world.RandomizableContainer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.ChestBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.ChestType;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.level.storage.loot.LootTable;
import net.minecraft.world.level.storage.loot.parameters.LootContextParamSets;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;
import net.minecraft.world.phys.Vec3;

import java.util.List;
import java.util.function.Predicate;

public final class ModdedLootApplier {
    private ModdedLootApplier() {
    }

    public static void apply(Engine engine, ServerLevel level, BlockPos pos, BlockState state, MantleChunk<Matter> mc) {
        if (!isCanonicalContainer(pos, state)) {
            return;
        }
        boolean nativeLootDefined = hasNativeLootTable(level, pos, state);
        String containerKey = ModdedBlockResolution.isStorageChest(state) ? ModdedBlockState.serialize(state) : null;
        List<ContentLoot.Part> parts = ContentServices.containerLoot(engine, pos.getX(), pos.getY(), pos.getZ(), containerKey, nativeLootDefined);
        if (parts.isEmpty()) {
            return;
        }

        Container container = resolveContainer(level, pos, state);
        if (container == null) {
            IrisLogging.debug("Iris loot: no container at " + pos);
            return;
        }

        KList<ItemStack> items = new KList<>();
        LootParams nativeParams = null;
        for (ContentLoot.Part part : parts) {
            if (part instanceof ContentLoot.Items drawn) {
                items.addAll(ModdedItemTranslator.stacks(drawn.items(), level));
                continue;
            }
            if (part instanceof ContentLoot.PlatformTable platform) {
                LootTable table = nativeTable(level, platform.key());
                if (table == null) {
                    IrisLogging.warn("Unknown vanilla loot table " + platform.key());
                    continue;
                }
                if (nativeParams == null) {
                    nativeParams = new LootParams.Builder(level)
                            .withParameter(LootContextParams.ORIGIN, Vec3.atCenterOf(pos))
                            .create(LootContextParamSets.CHEST);
                }
                items.addAll(table.getRandomItems(nativeParams, platform.seed()));
            }
        }

        fillContainer(container, items, LootResolver.containerRng(engine.getSeedManager().getLoot(), pos.getX(), pos.getY(), pos.getZ()));
    }

    private static LootTable nativeTable(ServerLevel level, String name) {
        ResourceKey<LootTable> key = resolveNativeKey(name, candidate -> hasNativeTable(level, candidate));
        if (key == null) {
            return null;
        }
        return level.getServer().reloadableRegistries().lookup()
                .lookupOrThrow(Registries.LOOT_TABLE)
                .getOrThrow(key)
                .value();
    }

    static ResourceKey<LootTable> resolveNativeKey(String name, Predicate<ResourceKey<LootTable>> registryContains) {
        Identifier id = name == null ? null : Identifier.tryParse(name);
        if (id == null) {
            return null;
        }
        ResourceKey<LootTable> key = ResourceKey.create(Registries.LOOT_TABLE, id);
        return registryContains.test(key) ? key : null;
    }

    static void fillContainer(Container container, List<ItemStack> items, RNG rng) {
        for (ItemStack item : items) {
            addItem(container, item);
        }
        scramble(container, rng);
        container.setChanged();
    }

    private static boolean hasNativeTable(ServerLevel level, ResourceKey<LootTable> key) {
        return level.getServer().reloadableRegistries().lookup()
                .lookup(Registries.LOOT_TABLE)
                .flatMap(registry -> registry.get(key))
                .isPresent();
    }

    static boolean isCanonicalContainer(BlockPos pos, BlockState state) {
        if (!(state.getBlock() instanceof ChestBlock) || state.getValue(ChestBlock.TYPE) == ChestType.SINGLE) {
            return true;
        }
        BlockPos connected = ChestBlock.getConnectedBlockPos(pos, state);
        if (connected.equals(pos)) {
            return true;
        }
        return isCanonicalPair(pos, connected);
    }

    static boolean isCanonicalPair(BlockPos pos, BlockPos connected) {
        return pos.getX() < connected.getX()
                || pos.getX() == connected.getX() && pos.getZ() <= connected.getZ();
    }

    static boolean hasNativeLootTable(ServerLevel level, BlockPos pos, BlockState state) {
        if (hasNativeLootTableAt(level, pos)) {
            return true;
        }
        if (!(state.getBlock() instanceof ChestBlock) || state.getValue(ChestBlock.TYPE) == ChestType.SINGLE) {
            return false;
        }
        BlockPos connected = ChestBlock.getConnectedBlockPos(pos, state);
        return level.hasChunkAt(connected) && hasNativeLootTableAt(level, connected);
    }

    private static boolean hasNativeLootTableAt(ServerLevel level, BlockPos pos) {
        return hasNativeLootTable(level.getBlockEntity(pos));
    }

    static boolean hasNativeLootTable(BlockEntity blockEntity) {
        return blockEntity instanceof RandomizableContainer container && hasNativeLootTable(container);
    }

    static boolean hasNativeLootTable(RandomizableContainer container) {
        return container != null && container.getLootTable() != null;
    }

    private static Container resolveContainer(ServerLevel level, BlockPos pos, BlockState state) {
        if (state.getBlock() instanceof ChestBlock chestBlock) {
            Container combined = ChestBlock.getContainer(chestBlock, state, level, pos, true);
            if (combined != null) {
                return combined;
            }
        }
        BlockEntity blockEntity = level.getBlockEntity(pos);
        return blockEntity instanceof Container container ? container : null;
    }

    private static void addItem(Container container, ItemStack stack) {
        if (stack == null || stack.isEmpty()) {
            return;
        }
        for (int i = 0; i < container.getContainerSize() && !stack.isEmpty(); i++) {
            ItemStack existing = container.getItem(i);
            if (existing.isEmpty()) {
                container.setItem(i, stack.split(container.getMaxStackSize(stack)));
                continue;
            }
            if (ItemStack.isSameItemSameComponents(existing, stack)) {
                int limit = container.getMaxStackSize(existing);
                if (existing.getCount() < limit) {
                    int move = Math.min(stack.getCount(), limit - existing.getCount());
                    existing.grow(move);
                    stack.shrink(move);
                }
            }
        }
        if (!stack.isEmpty()) {
            IrisLogging.debug("Iris loot: container full, dropped " + stack.getCount() + "x " + stack.getItem());
        }
    }

    private static void scramble(Container container, RNG rng) {
        int size = container.getContainerSize();
        ItemStack[] items = new ItemStack[size];
        for (int i = 0; i < size; i++) {
            items[i] = container.getItem(i);
        }
        boolean packedFull = false;

        splitting:
        for (int i = 0; i < items.length; i++) {
            ItemStack is = items[i];

            if (!is.isEmpty() && is.getCount() > 1 && !packedFull) {
                for (int j = 0; j < items.length; j++) {
                    if (items[j].isEmpty()) {
                        int take = rng.nextInt(is.getCount());
                        take = take == 0 ? 1 : take;
                        is.setCount(is.getCount() - take);
                        items[j] = is.copyWithCount(take);
                        continue splitting;
                    }
                }

                packedFull = true;
            }
        }

        for (int i = items.length; i > 1; i--) {
            int j = rng.nextInt(i);
            ItemStack tmp = items[i - 1];
            items[i - 1] = items[j];
            items[j] = tmp;
        }

        for (int i = 0; i < size; i++) {
            container.setItem(i, items[i]);
        }
    }
}

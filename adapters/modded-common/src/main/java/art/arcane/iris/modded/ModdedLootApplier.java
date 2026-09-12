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

import art.arcane.iris.generation.runtime.Engine;
import art.arcane.iris.structure.placement.LootResolver;
import art.arcane.iris.structure.placement.PlacedObject;
import art.arcane.iris.structure.object.IObjectLoot;
import art.arcane.iris.world.loot.InventorySlotType;
import art.arcane.iris.world.loot.IrisLootTable;
import art.arcane.iris.structure.object.IrisObjectLoot;
import art.arcane.iris.structure.object.IrisObjectPlacement;
import art.arcane.iris.structure.object.IrisObjectVanillaLoot;
import art.arcane.iris.spi.IrisLogging;
import art.arcane.iris.spi.PlatformBlockState;
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

import java.util.ArrayList;
import java.util.List;
import java.util.function.Predicate;

public final class ModdedLootApplier {
    private ModdedLootApplier() {
    }

    public static void apply(Engine engine, ServerLevel level, BlockPos pos, BlockState state, MantleChunk<Matter> mc) {
        if (!isCanonicalContainer(pos, state)) {
            return;
        }
        RNG rng = LootResolver.containerRng(engine.getSeedManager().getLoot(), pos.getX(), pos.getY(), pos.getZ());
        boolean nativeLootDefined = hasNativeLootTable(level, pos, state);
        List<LootSource> sources = resolveSources(engine, level, rng, pos, state, mc, nativeLootDefined);
        if (sources.isEmpty()) {
            return;
        }

        Container container = resolveContainer(level, pos, state);
        if (container == null) {
            IrisLogging.debug("Iris loot: no container at " + pos);
            return;
        }

        KList<ItemStack> items = new KList<>();
        LootParams nativeParams = null;
        for (LootSource source : sources) {
            if (source instanceof IrisLootSource irisSource) {
                IrisLootTable table = irisSource.table();
                if (table == null) {
                    continue;
                }
                items.addAll(ModdedItemTranslator.loot(table, engine.getSeedManager().getLoot(), InventorySlotType.STORAGE, level, pos.getX(), pos.getY(), pos.getZ()));
                continue;
            }
            if (source instanceof NativeLootSource nativeSource) {
                if (nativeParams == null) {
                    nativeParams = new LootParams.Builder(level)
                            .withParameter(LootContextParams.ORIGIN, Vec3.atCenterOf(pos))
                            .create(LootContextParamSets.CHEST);
                }
                items.addAll(nativeSource.table().getRandomItems(nativeParams, rng.nextLong()));
            }
        }

        fillContainer(container, items, rng);
    }

    private static List<LootSource> resolveSources(Engine engine, ServerLevel level, RNG rng, BlockPos pos, BlockState state, MantleChunk<Matter> mc, boolean nativeLootDefined) {
        int rx = pos.getX();
        int rz = pos.getZ();
        int ry = pos.getY() - engine.getWorld().minHeight();
        List<LootSource> sources = new ArrayList<>();
        boolean objectLootDefined = nativeLootDefined;

        PlacedObject po = engine.getObjectPlacement(rx, ry, rz, mc);
        if (po != null && po.getPlacement() != null && ModdedBlockResolution.isStorageChest(state)) {
            objectLootDefined = objectLootDefined
                    || po.getPlacement().getLoot().isNotEmpty()
                    || po.getPlacement().getVanillaLoot().isNotEmpty();
            Candidate picked = pickPlacementTable(engine, level, po.getPlacement(), state, rng);
            if (picked != null) {
                sources.add(picked.source());
                if (po.getPlacement().isOverrideGlobalLoot()) {
                    return sources;
                }
            }
        }

        LootResolver.resolveEnvironmentSources(
                sources,
                engine,
                rng,
                rx,
                ry,
                rz,
                objectLootDefined,
                IrisLootSource::new
        );
        return sources;
    }

    private static Candidate pickPlacementTable(Engine engine, ServerLevel level, IrisObjectPlacement placement, BlockState state, RNG rng) {
        List<Candidate> exact = new ArrayList<>();
        List<Candidate> basic = new ArrayList<>();
        List<Candidate> global = new ArrayList<>();

        for (IrisObjectLoot loot : placement.getLoot()) {
            if (loot == null || loot.getWeight() <= 0) {
                continue;
            }
            IrisLootTable table = engine.getData().getLootLoader().load(loot.getName());
            if (table == null) {
                IrisLogging.warn("Couldn't find loot table " + loot.getName());
                continue;
            }
            bucket(engine, loot, new Candidate(new IrisLootSource(table), loot.getWeight()), state, exact, basic, global);
        }

        for (IrisObjectVanillaLoot loot : placement.getVanillaLoot()) {
            if (loot == null || loot.getWeight() <= 0) {
                continue;
            }
            ResourceKey<LootTable> key = resolveNativeKey(loot.getName(), candidate -> hasNativeTable(level, candidate));
            if (key == null) {
                IrisLogging.warn("Couldn't find vanilla loot table " + loot.getName());
                continue;
            }
            LootTable table = level.getServer().reloadableRegistries().lookup()
                    .lookupOrThrow(Registries.LOOT_TABLE)
                    .getOrThrow(key)
                    .value();
            bucket(engine, loot, new Candidate(new NativeLootSource(table), loot.getWeight()), state, exact, basic, global);
        }

        List<Candidate> pool = !exact.isEmpty() ? exact : !basic.isEmpty() ? basic : global;
        return LootResolver.pickWeighted(pool, Candidate::weight, rng);
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

    private static void bucket(Engine engine, IObjectLoot loot, Candidate candidate, BlockState state, List<Candidate> exact, List<Candidate> basic, List<Candidate> global) {
        if (loot.getFilter().isEmpty()) {
            global.add(candidate);
            return;
        }

        for (PlatformBlockState filterState : loot.getFilter(engine.getData())) {
            BlockState filter = (BlockState) filterState.nativeHandle();
            if (loot.isExact() ? filter == state : filter.getBlock() == state.getBlock()) {
                (loot.isExact() ? exact : basic).add(candidate);
                return;
            }
        }
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

    private interface LootSource {
    }

    private record IrisLootSource(IrisLootTable table) implements LootSource {
    }

    private record NativeLootSource(LootTable table) implements LootSource {
    }

    private record Candidate(LootSource source, int weight) {
    }
}

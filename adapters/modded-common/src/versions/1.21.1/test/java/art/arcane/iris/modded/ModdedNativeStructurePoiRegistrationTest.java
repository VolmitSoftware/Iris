package art.arcane.iris.modded;

import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.core.registries.Registries;
import net.minecraft.core.RegistrationInfo;
import net.minecraft.core.Registry;
import net.minecraft.core.MappedRegistry;
import com.mojang.serialization.Lifecycle;
import art.arcane.volmlib.nativelib.minecraft26_2.modded.NativeModdedStructureStage;

import com.mojang.serialization.Codec;
import net.minecraft.SharedConstants;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.IdMapper;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.entity.ai.village.poi.PoiSection;
import net.minecraft.world.entity.ai.village.poi.PoiManager;
import net.minecraft.world.entity.ai.village.poi.PoiType;
import net.minecraft.world.entity.ai.village.poi.PoiTypes;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.LevelHeightAccessor;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.BiomeGenerationSettings;
import net.minecraft.world.level.biome.BiomeSpecialEffects;
import net.minecraft.world.level.biome.MobSpawnSettings;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.PalettedContainer;
import net.minecraft.world.level.chunk.PalettedContainerRO;
import net.minecraft.world.level.chunk.ProtoChunk;
import net.minecraft.world.level.chunk.UpgradeData;
import org.junit.BeforeClass;
import org.junit.Test;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

public class ModdedNativeStructurePoiRegistrationTest {
    @BeforeClass
    public static void bootstrapMinecraftRegistries() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    @Test
    public void visitsOnlyExistingPoiBlocksBeforeNativePlacement() {
        ProtoChunk chunk = newChunk();
        BlockPos poiPosition = new BlockPos(3, 70, 5);
        BlockPos ordinaryPosition = new BlockPos(4, 70, 5);
        BlockState poiState = Blocks.BARREL.defaultBlockState();
        chunk.setBlockState(poiPosition, poiState, false);
        chunk.setBlockState(ordinaryPosition, Blocks.STONE.defaultBlockState(), false);
        Map<BlockPos, BlockState> visited = new LinkedHashMap<>();

        NativeModdedStructureStage.visitExistingPois(
                chunk, (position, state) -> visited.put(position.immutable(), state));

        assertEquals(poiState, visited.get(poiPosition));
        assertFalse(visited.containsKey(ordinaryPosition));
        assertEquals(1, visited.size());
    }

    @Test
    public void duplicatePrimingIsIdempotentForAnAlreadyRegisteredPoi() {
        BlockPos position = new BlockPos(3, 70, 5);
        Holder<PoiType> type = PoiTypes.forState(
                Blocks.BARREL.defaultBlockState()).orElseThrow();
        PoiSection section = new PoiSection(() -> { });

        section.add(position, type);
        section.add(position, type);
        assertEquals(1L, section.getRecords(candidate -> true, PoiManager.Occupancy.ANY).count());
        assertEquals(type, section.getType(position).orElseThrow());

        section.remove(position);

        assertTrue(section.getType(position).isEmpty());
    }

    @Test
    public void worldgenPrimingQueuesBeforeTheLaterVanillaRemoval() {
        ProtoChunk chunk = newChunk();
        BlockPos position = new BlockPos(3, 70, 5);
        BlockState poiState = Blocks.BARREL.defaultBlockState();
        chunk.setBlockState(position, poiState, false);
        PoiSection section = new PoiSection(() -> { });
        Deque<Runnable> serverQueue = new ArrayDeque<>();
        AtomicInteger missingRemovals = new AtomicInteger();

        NativeModdedStructureStage.visitExistingPois(chunk,
                (poiPosition, state) -> queuePoiTransition(
                        serverQueue, section, poiPosition,
                        Blocks.AIR.defaultBlockState(), state, missingRemovals));
        assertTrue(section.getType(position).isEmpty());
        queuePoiTransition(serverQueue, section, position,
                poiState, Blocks.AIR.defaultBlockState(), missingRemovals);

        while (!serverQueue.isEmpty()) {
            serverQueue.removeFirst().run();
        }

        assertEquals(0, missingRemovals.get());
        assertTrue(section.getType(position).isEmpty());
    }

    private static void queuePoiTransition(Deque<Runnable> serverQueue,
                                           PoiSection section,
                                           BlockPos position,
                                           BlockState oldState,
                                           BlockState newState,
                                           AtomicInteger missingRemovals) {
        Optional<Holder<PoiType>> oldType = PoiTypes.forState(oldState);
        Optional<Holder<PoiType>> newType = PoiTypes.forState(newState);
        if (Objects.equals(oldType, newType)) {
            return;
        }
        BlockPos immutablePosition = position.immutable();
        oldType.ifPresent(type -> serverQueue.addLast(() -> {
            if (section.getType(immutablePosition).isEmpty()) {
                missingRemovals.incrementAndGet();
                return;
            }
            section.remove(immutablePosition);
        }));
        newType.ifPresent(type -> serverQueue.addLast(
                () -> section.add(immutablePosition, type)));
    }

    private static ProtoChunk newChunk() {
        return new ProtoChunk(
                new ChunkPos(0, 0),
                UpgradeData.EMPTY,
                LevelHeightAccessor.create(-64, 384),
                biomeRegistry(),
                null);
    }

    private static Registry<Biome> biomeRegistry() {
        Biome biome = new Biome.BiomeBuilder()
                .hasPrecipitation(false)
                .temperature(0.8F)
                .downfall(0.4F)
                .specialEffects(new BiomeSpecialEffects.Builder().fogColor(0).waterColor(0x3F76E4)
                        .waterFogColor(0).skyColor(0).build())
                .mobSpawnSettings(MobSpawnSettings.EMPTY)
                .generationSettings(BiomeGenerationSettings.EMPTY)
                .build();
        MappedRegistry<Biome> registry = new MappedRegistry<>(Registries.BIOME, Lifecycle.stable());
        registry.register(ResourceKey.create(Registries.BIOME, ResourceLocation.withDefaultNamespace("plains")),
                biome, RegistrationInfo.BUILT_IN);
        return registry.freeze();
    }
}

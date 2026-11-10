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

package art.arcane.iris.forge;

import art.arcane.iris.modded.IrisModdedChunkGenerator;
import art.arcane.iris.modded.ModdedBlockBreakHandler;
import art.arcane.iris.modded.ModdedEngineBootstrap;
import art.arcane.iris.modded.ModdedForcedDatapack;
import art.arcane.iris.modded.ModdedProtocolHandler;
import art.arcane.iris.modded.command.IrisModdedCommands;
import art.arcane.iris.modded.command.ModdedWandService;
import com.mojang.serialization.MapCodec;
import net.minecraft.core.registries.Registries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.packs.PackType;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraftforge.event.AddPackFindersEvent;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.event.level.BlockEvent;
import net.minecraftforge.event.level.LevelEvent;
import net.minecraftforge.event.server.ServerAboutToStartEvent;
import net.minecraftforge.event.server.ServerStartedEvent;
import net.minecraftforge.event.server.ServerStartingEvent;
import net.minecraftforge.event.server.ServerStoppedEvent;
import net.minecraftforge.event.server.ServerStoppingEvent;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.common.loot.IGlobalLootModifier;
import net.minecraftforge.common.util.BlockSnapshot;
import net.minecraftforge.eventbus.api.listener.Priority;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import net.minecraftforge.fml.loading.FMLEnvironment;
import net.minecraftforge.fml.loading.FMLLoader;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.server.permission.events.PermissionGatherEvent;

import java.util.function.Predicate;

@Mod("irisworldgen")
public final class IrisForgeBootstrap {
    public IrisForgeBootstrap(FMLJavaModLoadingContext context) {
        ModdedEngineBootstrap.bootCommon(new ForgeModdedLoader(), "Forge " + FMLLoader.versionInfo().forgeVersion(), () -> {
            DeferredRegister<MapCodec<? extends ChunkGenerator>> chunkGenerators = DeferredRegister.create(Registries.CHUNK_GENERATOR, "irisworldgen");
            chunkGenerators.register("iris", () -> IrisModdedChunkGenerator.CODEC);
            chunkGenerators.register(context.getModBusGroup());
            DeferredRegister<MapCodec<? extends IGlobalLootModifier>> lootModifiers = DeferredRegister.create(ForgeRegistries.GLOBAL_LOOT_MODIFIER_SERIALIZERS, "irisworldgen");
            lootModifiers.register("block_drops", () -> IrisForgeBlockLootModifier.CODEC);
            lootModifiers.register(context.getModBusGroup());
        });

        ForgeProtocolNetworking.register();

        // Dist gate for every client-tainted class. DistExecutor is gone in Forge 26.2 (65.x) - only
        // net.minecraftforge.api.distmarker.Dist survives, from mergetool-api - so the guard is this branch.
        // It is equally safe: IrisForgeClient appears only as an invokestatic target, so the JVM resolves the
        // constant-pool entry lazily on first execution and a dedicated server never loads the class.
        if (FMLEnvironment.dist == Dist.CLIENT) {
            IrisForgeClient.init();
        }

        ServerAboutToStartEvent.BUS.addListener((ServerAboutToStartEvent event) -> ModdedEngineBootstrap.serverAboutToStart(event.getServer()));
        ServerStartingEvent.BUS.addListener((ServerStartingEvent event) -> ModdedEngineBootstrap.start(event.getServer()));
        ServerStartedEvent.BUS.addListener((ServerStartedEvent event) -> ModdedEngineBootstrap.serverStarted(event.getServer()));
        ServerStoppingEvent.BUS.addListener((ServerStoppingEvent event) -> ModdedEngineBootstrap.stop());
        ServerStoppedEvent.BUS.addListener((ServerStoppedEvent event) -> ModdedEngineBootstrap.stopped());
        LevelEvent.Load.BUS.addListener((LevelEvent.Load event) -> {
            if (event.getLevel() instanceof ServerLevel level) {
                ModdedEngineBootstrap.levelLoaded(level);
            }
        });
        LevelEvent.Unload.BUS.addListener((LevelEvent.Unload event) -> {
            if (event.getLevel() instanceof ServerLevel level) {
                ModdedEngineBootstrap.levelUnloaded(level);
            }
        });
        PlayerEvent.PlayerLoggedInEvent.BUS.addListener((PlayerEvent.PlayerLoggedInEvent event) -> {
            if (event.getEntity() instanceof ServerPlayer player) {
                ModdedProtocolHandler.onPlayerJoin(player);
            }
        });
        PlayerEvent.PlayerLoggedOutEvent.BUS.addListener((PlayerEvent.PlayerLoggedOutEvent event) -> {
            if (event.getEntity() instanceof ServerPlayer player) {
                ModdedProtocolHandler.onPlayerDisconnect(player);
            }
        });
        AddPackFindersEvent.BUS.addListener((AddPackFindersEvent event) -> {
            if (event.getPackType() == PackType.SERVER_DATA) {
                event.addRepositorySource(ModdedForcedDatapack.repositorySource());
            }
        });
        RegisterCommandsEvent.BUS.addListener((RegisterCommandsEvent event) -> IrisModdedCommands.register(event.getDispatcher()));
        PermissionGatherEvent.Nodes.BUS.addListener((PermissionGatherEvent.Nodes event) ->
                event.addNodes(ForgeModdedLoader.TREE_FELLER_PERMISSION));
        BlockEvent.BreakEvent.BUS.addListener((BlockEvent.BreakEvent event) -> {
            if (event.getLevel() instanceof ServerLevel level
                    && event.getPlayer() instanceof ServerPlayer player
                    && !event.getResult().isDenied()) {
                ModdedBlockBreakHandler.prepare(level, player, event.getPos(), event.getState());
            }
        });
        // EventBus 7: the (priority, boolean, consumer) overload's boolean means "always cancels"; passing
        // false is rejected at registration for listeners that never cancel. Use (priority, consumer).
        BlockEvent.EntityPlaceEvent.BUS.addListener(Priority.MONITOR, (BlockEvent.EntityPlaceEvent event) -> {
            if (!(event instanceof BlockEvent.EntityMultiPlaceEvent)
                    && event.getLevel() instanceof ServerLevel level) {
                ModdedBlockBreakHandler.clearPlacedProvenance(level, event.getPos());
            }
        });
        BlockEvent.EntityMultiPlaceEvent.BUS.addListener(
                Priority.MONITOR,
                (BlockEvent.EntityMultiPlaceEvent event) -> {
                    for (BlockSnapshot snapshot : event.getReplacedBlockSnapshots()) {
                        if (snapshot.getLevel() instanceof ServerLevel level) {
                            ModdedBlockBreakHandler.clearPlacedProvenance(level, snapshot.getPos());
                        }
                    }
                }
        );
        PlayerInteractEvent.LeftClickBlock.BUS.addListener((Predicate<PlayerInteractEvent.LeftClickBlock>) (PlayerInteractEvent.LeftClickBlock event) ->
                ModdedWandService.attackBlock(event.getEntity(), event.getLevel(), event.getHand(), event.getPos()));
        PlayerInteractEvent.RightClickBlock.BUS.addListener((Predicate<PlayerInteractEvent.RightClickBlock>) (PlayerInteractEvent.RightClickBlock event) ->
                ModdedWandService.useBlock(event.getEntity(), event.getLevel(), event.getHand(), event.getPos()));
        TickEvent.ServerTickEvent.Post.BUS.addListener((TickEvent.ServerTickEvent.Post event) -> {
            ModdedEngineBootstrap.tick(event.server());
            ModdedWandService.serverTick(event.server());
        });
    }
}

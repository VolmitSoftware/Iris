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

package art.arcane.iris.fabric;

import art.arcane.iris.modded.IrisModdedChunkGenerator;
import art.arcane.iris.modded.ModdedBlockBreakHandler;
import art.arcane.iris.modded.ModdedEngineBootstrap;
import art.arcane.iris.modded.command.IrisModdedCommands;
import art.arcane.iris.modded.command.ModdedWandService;
import com.mojang.brigadier.CommandDispatcher;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLevelEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.event.player.AttackBlockCallback;
import net.fabricmc.fabric.api.event.player.PlayerBlockBreakEvents;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.minecraft.commands.CommandBuildContext;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;

public final class IrisFabricBootstrap implements ModInitializer {
    @Override
    public void onInitialize() {
        ModdedEngineBootstrap.bootCommon(new FabricModdedLoader(), "Fabric",
            () -> Registry.register(BuiltInRegistries.CHUNK_GENERATOR, Identifier.fromNamespaceAndPath("irisworldgen", "iris"), IrisModdedChunkGenerator.CODEC));
        FabricProtocolNetworking.install();
        ServerLifecycleEvents.SERVER_STARTING.register((MinecraftServer server) -> ModdedEngineBootstrap.start(server));
        ServerLifecycleEvents.SERVER_STARTED.register((MinecraftServer server) -> ModdedEngineBootstrap.serverStarted(server));
        ServerLifecycleEvents.SERVER_STOPPING.register((MinecraftServer server) -> ModdedEngineBootstrap.stop());
        ServerLifecycleEvents.SERVER_STOPPED.register((MinecraftServer server) -> ModdedEngineBootstrap.stopped());
        ServerLevelEvents.LOAD.register((MinecraftServer server, ServerLevel level) -> ModdedEngineBootstrap.levelLoaded(level));
        ServerLevelEvents.UNLOAD.register((MinecraftServer server, ServerLevel level) -> ModdedEngineBootstrap.levelUnloaded(level));
        CommandRegistrationCallback.EVENT.register((CommandDispatcher<CommandSourceStack> dispatcher, CommandBuildContext buildContext, Commands.CommandSelection selection) -> IrisModdedCommands.register(dispatcher));
        PlayerBlockBreakEvents.BEFORE.register((Level level, Player player, BlockPos pos, BlockState state, BlockEntity blockEntity) -> {
            if (level instanceof ServerLevel serverLevel && player instanceof ServerPlayer serverPlayer) {
                ModdedBlockBreakHandler.prepare(serverLevel, serverPlayer, pos, state);
            }
            return true;
        });
        PlayerBlockBreakEvents.CANCELED.register((Level level, Player player, BlockPos pos, BlockState state, BlockEntity blockEntity) -> {
            if (level instanceof ServerLevel serverLevel) {
                ModdedBlockBreakHandler.cancel(serverLevel, pos);
            }
        });
        AttackBlockCallback.EVENT.register((Player player, Level level, InteractionHand hand, BlockPos pos, Direction direction) ->
                ModdedWandService.attackBlock(player, level, hand, pos) ? InteractionResult.SUCCESS : InteractionResult.PASS);
        UseBlockCallback.EVENT.register((Player player, Level level, InteractionHand hand, BlockHitResult hit) ->
                ModdedWandService.useBlock(player, level, hand, hit.getBlockPos()) ? InteractionResult.SUCCESS : InteractionResult.PASS);
        ServerTickEvents.END_SERVER_TICK.register((MinecraftServer server) -> {
            ModdedEngineBootstrap.tick(server);
            ModdedWandService.serverTick(server);
        });
    }
}

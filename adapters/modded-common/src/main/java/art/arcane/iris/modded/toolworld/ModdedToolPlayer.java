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

package art.arcane.iris.modded.toolworld;

import art.arcane.iris.modded.ModdedDimensionManager;
import art.arcane.iris.modded.ModdedEngineBootstrap;
import art.arcane.iris.modded.command.ModdedToolingSender;
import com.volmit.geometry.BlockPosition;
import com.volmit.resource.ResourceId;
import com.volmit.tooling.Sender;
import com.volmit.tooling.world.Particle;
import com.volmit.tooling.world.ToolItem;
import com.volmit.tooling.world.ToolMenu;
import com.volmit.tooling.world.ToolPlayer;
import com.volmit.tooling.world.ToolText;
import com.volmit.tooling.world.ToolWorld;
import net.minecraft.core.Holder;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.network.protocol.game.ClientboundSoundPacket;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.level.GameType;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;

import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/** One online player as a tool sees them. It finds the player again by id each time, so it outlives a respawn. */
final class ModdedToolPlayer implements ToolPlayer {
    private static final String DEFAULT_SIDEBAR_TITLE = "Iris";

    private final UUID id;
    private final String name;
    private final ToolPlayers players;
    private final ToolMenus menus;

    ModdedToolPlayer(UUID id, String name, ToolPlayers players, ToolMenus menus) {
        this.id = id;
        this.name = name;
        this.players = players;
        this.menus = menus;
    }

    private static MinecraftServer server() {
        MinecraftServer server = ModdedEngineBootstrap.currentServer();
        if (server == null) {
            throw new IllegalStateException("No server is running");
        }
        return server;
    }

    ServerPlayer player() {
        ServerPlayer online = server().getPlayerList().getPlayer(id);
        if (online == null) {
            throw new IllegalStateException("Player " + name + " is not online");
        }
        return online;
    }

    private Optional<ServerPlayer> onlinePlayer() {
        return Optional.ofNullable(server().getPlayerList().getPlayer(id));
    }

    @Override
    public UUID id() {
        return id;
    }

    @Override
    public String name() {
        return name;
    }

    @Override
    public Sender sender() {
        return new ModdedToolingSender(player().createCommandSourceStack());
    }

    @Override
    public Optional<ToolWorld> world() {
        return onlinePlayer()
                .filter(online -> online.level() instanceof ServerLevel)
                .flatMap(online -> ToolWorldRegistry.INSTANCE.forLevel((ServerLevel) online.level()))
                .map(world -> world);
    }

    @Override
    public BlockPosition position() {
        return ToolBlocks.positionOf(player().blockPosition());
    }

    @Override
    public float yaw() {
        return player().getYRot();
    }

    @Override
    public float pitch() {
        return player().getXRot();
    }

    @Override
    public boolean sneaking() {
        return player().isShiftKeyDown();
    }

    @Override
    public Optional<BlockPosition> targetBlock(int maxDistance) {
        HitResult hit = player().pick(maxDistance, 1.0F, false);
        if (hit.getType() != HitResult.Type.BLOCK) {
            return Optional.empty();
        }
        return Optional.of(ToolBlocks.positionOf(((BlockHitResult) hit).getBlockPos()));
    }

    @Override
    public void run(Runnable task) {
        server().execute(task);
    }

    @Override
    public CompletableFuture<Boolean> teleport(ToolWorld world, double x, double y, double z, float yaw, float pitch) {
        if (!(world instanceof ModdedToolWorld target)) {
            return CompletableFuture.failedFuture(new IllegalArgumentException("Not a tool world of this platform: " + world));
        }
        CompletableFuture<Boolean> arrived = new CompletableFuture<>();
        server().execute(() -> {
            Optional<ServerPlayer> online = onlinePlayer();
            if (online.isEmpty()) {
                arrived.complete(false);
                return;
            }
            ModdedDimensionManager.teleportAsync(online.get(), server(), target.level(), x, y, z)
                    .whenComplete((success, failure) -> server().execute(() -> faceAfterArrival(arrived, success, failure, target, yaw, pitch)));
        });
        return arrived;
    }

    private void faceAfterArrival(CompletableFuture<Boolean> arrived, Boolean success, Throwable failure,
                                  ModdedToolWorld target, float yaw, float pitch) {
        if (failure != null) {
            arrived.completeExceptionally(failure);
            return;
        }
        Optional<ServerPlayer> online = onlinePlayer();
        if (Boolean.TRUE.equals(success) && online.isPresent()) {
            ServerPlayer arrivedPlayer = online.get();
            arrivedPlayer.teleportTo(target.level(), arrivedPlayer.getX(), arrivedPlayer.getY(), arrivedPlayer.getZ(),
                    Set.of(), yaw, pitch, false);
        }
        arrived.complete(Boolean.TRUE.equals(success));
    }

    @Override
    public void creative(boolean on) {
        server().execute(() -> onlinePlayer().ifPresent(online -> {
            if (on) {
                players.rememberModeBeforeCreative(online);
                online.setGameMode(GameType.CREATIVE);
                return;
            }
            players.restoreGameMode(online);
        }));
    }

    @Override
    public void message(ToolText text) {
        server().execute(() -> onlinePlayer().ifPresent(online -> online.sendSystemMessage(ToolTexts.component(text))));
    }

    @Override
    public void actionBar(ToolText text) {
        server().execute(() -> onlinePlayer().ifPresent(online -> online.sendSystemMessage(ToolTexts.component(text), true)));
    }

    @Override
    public void sound(ResourceId sound, float volume, float pitch) {
        server().execute(() -> onlinePlayer().ifPresent(online -> {
            Holder<SoundEvent> event = Holder.direct(SoundEvent.createVariableRangeEvent(Identifier.parse(sound.toString())));
            online.connection.send(new ClientboundSoundPacket(event, SoundSource.MASTER, online.getX(), online.getY(), online.getZ(),
                    volume, pitch, online.level().getRandom().nextLong()));
        }));
    }

    @Override
    public void sidebar(ToolText title, List<ToolText> lines) {
        ToolText shown = title.isEmpty() ? ToolText.plain(DEFAULT_SIDEBAR_TITLE) : title;
        server().execute(() -> onlinePlayer().ifPresent(online -> players.sidebars().show(online, shown, lines)));
    }

    @Override
    public void hideSidebar() {
        server().execute(() -> onlinePlayer().ifPresent(online -> players.sidebars().hide(online)));
    }

    @Override
    public void particles(List<Particle> dust) {
        List<Particle> specks = List.copyOf(dust);
        server().execute(() -> onlinePlayer().ifPresent(online -> specks.forEach(speck -> showDust(online, speck))));
    }

    private static void showDust(ServerPlayer online, Particle speck) {
        if (online.level() instanceof ServerLevel level) {
            level.sendParticles(online, new DustParticleOptions(speck.rgb(), speck.size()), true, true,
                    speck.x(), speck.y(), speck.z(), 1, 0.0D, 0.0D, 0.0D, 0.0D);
        }
    }

    @Override
    public boolean give(ToolItem item) {
        var stack = ToolItems.create(item);
        if (server().isSameThread()) {
            return player().getInventory().add(stack);
        }
        server().execute(() -> onlinePlayer().ifPresent(online -> online.getInventory().add(stack)));
        return true;
    }

    @Override
    public Optional<ToolItem> held() {
        return ToolItems.read(player().getInventory().getSelectedItem());
    }

    @Override
    public void openMenu(ToolMenu menu) {
        server().execute(() -> onlinePlayer().ifPresent(online -> menus.open(online, this, menu)));
    }

    @Override
    public void closeMenu() {
        server().execute(() -> onlinePlayer().ifPresent(menus::close));
    }

    /**
     * Sends the player to where they were before they entered a tool world, or to the spawn of the main world when that is
     * not known any more, and gives back the game mode they had. Completes with whether they arrived.
     */
    CompletableFuture<Boolean> returnToOrigin() {
        CompletableFuture<Boolean> returned = new CompletableFuture<>();
        server().execute(() -> {
            Optional<ServerPlayer> online = onlinePlayer();
            if (online.isEmpty()) {
                returned.complete(false);
                return;
            }
            ServerPlayer leaving = online.get();
            menus.close(leaving);
            players.sidebars().hide(leaving);
            players.restoreGameMode(leaving);
            players.destinationAfterToolWorld(leaving).whenComplete((success, failure) -> {
                if (failure != null) {
                    returned.completeExceptionally(failure);
                    return;
                }
                returned.complete(Boolean.TRUE.equals(success));
            });
        });
        return returned;
    }

    @Override
    public String toString() {
        return "tool player " + name;
    }
}

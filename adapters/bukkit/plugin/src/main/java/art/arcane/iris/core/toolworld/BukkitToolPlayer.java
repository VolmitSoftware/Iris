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

package art.arcane.iris.core.toolworld;

import art.arcane.iris.core.service.BoardSVC;
import art.arcane.iris.core.service.BukkitToolingSender;
import art.arcane.iris.platform.bukkit.BukkitPlatform;
import art.arcane.iris.spi.IrisServices;
import art.arcane.iris.util.common.scheduling.J;
import art.arcane.volmlib.util.plugin.ComponentMessenger;
import art.arcane.volmlib.util.plugin.ComponentText;
import com.volmit.geometry.BlockPosition;
import com.volmit.resource.ResourceId;
import com.volmit.tooling.Sender;
import com.volmit.tooling.world.Particle;
import com.volmit.tooling.world.ToolItem;
import com.volmit.tooling.world.ToolMenu;
import com.volmit.tooling.world.ToolPlayer;
import com.volmit.tooling.world.ToolText;
import com.volmit.tooling.world.ToolWorld;
import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/** A Bukkit player as a tool sees them. Readers must run on the player's thread; changes hop there by themselves. */
final class BukkitToolPlayer implements ToolPlayer {
    private final Player player;
    private final OpenToolWorlds worlds;
    private final ToolPlayers players;
    private final BukkitToolMenus menus;

    BukkitToolPlayer(Player player, OpenToolWorlds worlds, ToolPlayers players, BukkitToolMenus menus) {
        this.player = player;
        this.worlds = worlds;
        this.players = players;
        this.menus = menus;
    }

    Player bukkit() {
        return player;
    }

    @Override
    public UUID id() {
        return player.getUniqueId();
    }

    @Override
    public String name() {
        return player.getName();
    }

    @Override
    public Sender sender() {
        return new BukkitToolingSender(player);
    }

    @Override
    public Optional<ToolWorld> world() {
        return worlds.forWorld(player.getWorld()).map(world -> world);
    }

    @Override
    public BlockPosition position() {
        return BukkitToolBlocks.positionOf(player.getLocation());
    }

    @Override
    public float yaw() {
        return player.getLocation().getYaw();
    }

    @Override
    public float pitch() {
        return player.getLocation().getPitch();
    }

    @Override
    public boolean sneaking() {
        return player.isSneaking();
    }

    @Override
    public void run(Runnable task) {
        J.runEntity(player, task);
    }

    @Override
    public CompletableFuture<Boolean> teleport(ToolWorld world, double x, double y, double z, float yaw, float pitch) {
        if (!(world instanceof BukkitToolWorld target)) {
            return CompletableFuture.failedFuture(new IllegalArgumentException("Not a tool world of this platform: " + world));
        }
        CompletableFuture<Boolean> arrived = new CompletableFuture<>();
        boolean scheduled = J.runEntity(player, () -> BukkitPlatform
                .teleportAsync(player, new Location(target.bukkit(), x, y, z, yaw, pitch))
                .whenComplete((success, failure) -> {
                    if (failure != null) {
                        arrived.completeExceptionally(failure);
                    } else {
                        arrived.complete(Boolean.TRUE.equals(success));
                    }
                }));
        if (!scheduled) {
            arrived.complete(false);
        }
        return arrived;
    }

    @Override
    public void creative(boolean on) {
        J.runEntity(player, () -> {
            if (on) {
                players.rememberModeBeforeCreative(player);
                player.setGameMode(GameMode.CREATIVE);
                return;
            }
            players.restoreGameMode(player);
        });
    }

    @Override
    public void message(ToolText text) {
        J.runEntity(player, () -> ComponentMessenger.send(player, ComponentText.component(BukkitToolText.component(text))));
    }

    @Override
    public void actionBar(ToolText text) {
        J.runEntity(player, () -> player.sendActionBar(BukkitToolText.component(text)));
    }

    @Override
    public void sound(ResourceId sound, float volume, float pitch) {
        J.runEntity(player, () -> player.playSound(player.getLocation(), sound.toString(), volume, pitch));
    }

    @Override
    public void sidebar(ToolText title, List<ToolText> lines) {
        BoardSVC board = IrisServices.getOrNull(BoardSVC.class);
        if (board == null) {
            return;
        }
        J.runEntity(player, () -> board.applyToolSidebar(player,
                new BoardSVC.ToolSidebar(player.getWorld().getUID(), title, lines)));
    }

    @Override
    public void hideSidebar() {
        BoardSVC board = IrisServices.getOrNull(BoardSVC.class);
        if (board != null) {
            board.clearToolSidebar(player);
        }
    }

    @Override
    public void particles(List<Particle> dust) {
        List<Particle> specks = List.copyOf(dust);
        J.runEntity(player, () -> specks.forEach(this::showDust));
    }

    @Override
    public boolean give(ToolItem item) {
        ItemStack stack = BukkitToolItems.create(item);
        if (J.isOwnedByCurrentRegion(player)) {
            return player.getInventory().addItem(stack).isEmpty();
        }
        J.runEntity(player, () -> player.getInventory().addItem(stack));
        return true;
    }

    @Override
    public Optional<ToolItem> held() {
        return BukkitToolItems.read(player.getInventory().getItemInMainHand());
    }

    @Override
    public void openMenu(ToolMenu menu) {
        J.runEntity(player, () -> menus.open(this, menu));
    }

    @Override
    public void closeMenu() {
        J.runEntity(player, () -> menus.close(player));
    }

    /**
     * Sends the player to where they were before they entered a tool world, or to the spawn of the main world when that
     * is not known any more, and gives back the game mode they had. Completes with whether they arrived.
     */
    CompletableFuture<Boolean> returnToOrigin() {
        CompletableFuture<Boolean> returned = new CompletableFuture<>();
        boolean scheduled = J.runEntity(player, () -> {
            menus.close(player);
            players.restoreGameMode(player);
            Location destination = players.takeOrigin(player)
                    .filter(this::isOutsideToolWorlds)
                    .orElseGet(() -> Bukkit.getWorlds().getFirst().getSpawnLocation());
            BukkitPlatform.teleportAsync(player, destination).whenComplete((success, failure) -> {
                if (failure != null) {
                    returned.completeExceptionally(failure);
                } else {
                    returned.complete(Boolean.TRUE.equals(success));
                }
            });
        });
        if (!scheduled) {
            returned.complete(false);
        }
        return returned;
    }

    private boolean isOutsideToolWorlds(Location location) {
        return location.getWorld() != null && worlds.forWorld(location.getWorld()).isEmpty();
    }

    private void showDust(Particle speck) {
        org.bukkit.Particle.DustOptions options = new org.bukkit.Particle.DustOptions(Color.fromRGB(speck.rgb()), speck.size());
        player.spawnParticle(org.bukkit.Particle.DUST, speck.x(), speck.y(), speck.z(), 1, 0.0D, 0.0D, 0.0D, 0.0D, options);
    }

    @Override
    public String toString() {
        return "tool player " + player.getName();
    }
}

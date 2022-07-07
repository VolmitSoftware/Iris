package com.volmit.iris.platform.bukkit.wrapper;

import art.arcane.amulet.geometry.Vec;
import com.volmit.iris.platform.PlatformPlayer;
import com.volmit.iris.platform.PlatformWorld;
import org.bukkit.entity.Player;

import java.util.UUID;

public class BukkitPlayer implements PlatformPlayer {
    private final Player delegate;

    private BukkitPlayer(Player delegate) {
        this.delegate = delegate;
    }

    @Override
    public UUID getUUID() {
        return delegate.getUniqueId();
    }

    @Override
    public String getName() {
        return delegate.getName();
    }

    @Override
    public Vec getLocation() {
        return delegate.getLocation().vec();
    }

    @Override
    public PlatformWorld getWorld() {
        return BukkitWorld.of(delegate.getWorld());
    }

    @Override
    public boolean canUseIris() {
        return delegate.isOp();
    }

    @Override
    public void sendMessage(String message) {
        delegate.sendMessage(message);
    }

    @Override
    public void sendActionBar(String message) {
        throw new UnsupportedOperationException();
    }

    @Override
    public void sendTitleMessage(String title, String subtitle, int in, int stay, int out) {
        throw new UnsupportedOperationException();
    }

    public static BukkitPlayer of(Player player) {
        return new BukkitPlayer(player);
    }
}

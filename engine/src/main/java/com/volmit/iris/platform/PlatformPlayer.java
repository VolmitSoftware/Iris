package com.volmit.iris.platform;

import art.arcane.amulet.geometry.Vec;

import java.util.UUID;

public interface PlatformPlayer {
    UUID getUUID();

    String getName();

    Vec getLocation();

    PlatformWorld getWorld();

    boolean canUseIris();

    void sendMessage(String message);

    void sendActionBar(String message);

    void sendTitleMessage(String title, String subtitle, int in, int stay, int out);
}

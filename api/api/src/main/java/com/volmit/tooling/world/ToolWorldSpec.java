package com.volmit.tooling.world;

import java.util.Objects;
import java.util.regex.Pattern;

/**
 * What a tool asks {@link ToolWorlds#open} for: a world of the tool's pack whose chunks come only from {@code painter},
 * and who hears about it.
 *
 * <p>The host opens it as a studio world of the tool's pack with its generation swapped for the painter, so its height
 * range is the pack dimension's; {@link ToolWorld#minY()} and {@link ToolWorld#maxY()} report it.
 *
 * @param name     the tool's name for the world, unique among the open tool worlds of the pack: lower case letters,
 *                 digits, {@code _} and {@code -}, starting with a letter or a digit. It is not the platform's world name.
 * @param painter  the only source of the world's chunks
 * @param listener told about the players, blocks and commands of the world
 */
public record ToolWorldSpec(String name, ChunkPainter painter, ToolWorldListener listener) {
    private static final Pattern NAME = Pattern.compile("[a-z0-9][a-z0-9_-]*");

    public ToolWorldSpec {
        Objects.requireNonNull(name, "name");
        Objects.requireNonNull(painter, "painter");
        Objects.requireNonNull(listener, "listener");
        if (!NAME.matcher(name).matches()) {
            throw new IllegalArgumentException("A tool world name is lower case letters, digits, '_' and '-': " + name);
        }
    }
}

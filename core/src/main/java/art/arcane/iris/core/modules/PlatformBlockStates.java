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

package art.arcane.iris.core.modules;

import art.arcane.iris.spi.PlatformBlockState;
import com.volmit.block.BlockState;

import java.util.HashMap;
import java.util.Map;
import java.util.function.Function;

/**
 * Converts between IrisApi block states and the platform's through their keys, remembering each
 * conversion. Meant for one invocation, which repeats few states many times; not thread-safe.
 */
public final class PlatformBlockStates {
    public static final BlockState AIR = BlockState.parse("minecraft:air");
    private static final String MINECRAFT_NAMESPACE = "minecraft";
    private static final char NAMESPACE_SEPARATOR = ':';
    private static final char PROPERTIES_START = '[';

    private final Function<String, PlatformBlockState> platformStates;
    private final Map<PlatformBlockState, BlockState> read = new HashMap<>();
    private final Map<BlockState, PlatformBlockState> written = new HashMap<>();

    /** {@code platformStates} turns a block state key into the platform's state, such as {@code B::getState}. */
    public PlatformBlockStates(Function<String, PlatformBlockState> platformStates) {
        this.platformStates = platformStates;
    }

    /**
     * Iris leaves unset blocks null, which IrisApi forbids, so they read as air. A key without a namespace
     * is Minecraft's, as Minecraft reads it.
     */
    public BlockState toApi(PlatformBlockState state) {
        if (state == null) {
            return AIR;
        }
        return read.computeIfAbsent(state, platform -> BlockState.parse(namespaced(platform.key())));
    }

    public PlatformBlockState toPlatform(BlockState state) {
        return written.computeIfAbsent(state, api -> platformStates.apply(api.key()));
    }

    /** {@code key} with Minecraft's namespace when it names none, as Minecraft reads such keys. */
    public static String namespaced(String key) {
        int namespaceEnd = key.indexOf(NAMESPACE_SEPARATOR);
        int propertiesStart = key.indexOf(PROPERTIES_START);
        boolean hasNamespace = namespaceEnd >= 0 && (propertiesStart < 0 || namespaceEnd < propertiesStart);
        return hasNamespace ? key : MINECRAFT_NAMESPACE + NAMESPACE_SEPARATOR + key;
    }
}

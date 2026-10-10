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
import java.util.IdentityHashMap;
import java.util.Map;
import java.util.function.Function;

/**
 * Converts between IrisApi block states and the platform's through their keys, remembering each
 * conversion. Meant for one invocation, which repeats few states many times; not thread-safe.
 * <p>
 * A state read from the platform and written back as the same instance keeps its platform identity. IrisApi's
 * {@link BlockState} is only a key, and some platform states share a key with another, such as a content plugin's
 * custom block and the base block it is drawn as; a stage that copies such a block (planned mantle blocks into the
 * chunk) must write the custom block back, not its base. Every distinct platform state therefore reads as its own
 * instance, and writing that instance gives the platform state back; any other instance converts by its key.
 */
public final class PlatformBlockStates {
    public static final BlockState AIR = BlockState.parse("minecraft:air");
    private static final String MINECRAFT_NAMESPACE = "minecraft";
    private static final char NAMESPACE_SEPARATOR = ':';
    private static final char PROPERTIES_START = '[';

    private final Function<String, PlatformBlockState> platformStates;
    private final Map<PlatformBlockState, BlockState> read = new HashMap<>();
    private final Map<BlockState, PlatformBlockState> written = new HashMap<>();
    private final Map<BlockState, PlatformBlockState> origins = new IdentityHashMap<>();
    /** The state read last: scans over a chunk read long runs of one state, such as stone or air, so most reads hit it. */
    private PlatformBlockState lastRead;
    private BlockState lastReadAsApi;

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
        if (state != lastRead) {
            lastReadAsApi = convertRead(state);
            lastRead = state;
        }
        return lastReadAsApi;
    }

    private BlockState convertRead(PlatformBlockState state) {
        BlockState known = read.get(state);
        if (known != null) {
            return known;
        }
        BlockState api = BlockState.parse(namespaced(state.key()));
        read.put(state, api);
        origins.put(api, state);
        return api;
    }

    public PlatformBlockState toPlatform(BlockState state) {
        PlatformBlockState origin = origins.get(state);
        if (origin != null) {
            return origin;
        }
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

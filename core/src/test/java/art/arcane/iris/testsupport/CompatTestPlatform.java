/*
 * Iris is a World Generator for Minecraft Bukkit Servers
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

package art.arcane.iris.testsupport;

import art.arcane.iris.generation.runtime.MeteredCache;
import art.arcane.iris.generation.runtime.PreservationRegistry;
import art.arcane.iris.spi.IrisPlatform;
import art.arcane.iris.spi.IrisPlatforms;
import art.arcane.iris.spi.IrisServices;
import art.arcane.iris.spi.PlatformBiome;
import art.arcane.iris.spi.PlatformBlockProperty;
import art.arcane.iris.spi.PlatformBlockState;
import art.arcane.iris.spi.PlatformEntityType;
import art.arcane.iris.spi.PlatformItem;
import art.arcane.iris.spi.PlatformRegistries;
import org.mockito.Answers;

import java.io.DataOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ExecutorService;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Binds a platform whose block registry holds exactly the keys a compat test declares, so the gate can be exercised
 * without a server. Also writes the .iob and JSON fixtures those tests need.
 */
public final class CompatTestPlatform {
    private final IrisPlatform previous;

    private CompatTestPlatform(IrisPlatform previous) {
        this.previous = previous;
    }

    public static CompatTestPlatform bind(File dataFolder, List<String> blockKeys) {
        IrisPlatform previous = IrisPlatforms.isBound() ? IrisPlatforms.get() : null;
        IrisPlatforms.unbind();
        IrisPlatform platform = mock(IrisPlatform.class, Answers.CALLS_REAL_METHODS);
        when(platform.dataFolder()).thenReturn(dataFolder);
        when(platform.registries()).thenReturn(new FixedRegistries(blockKeys));
        IrisPlatforms.bind(platform);
        IrisServices.register(PreservationRegistry.class, new NoOpPreservationRegistry());
        return new CompatTestPlatform(previous);
    }

    public void unbind() {
        IrisServices.remove(PreservationRegistry.class);
        IrisPlatforms.unbind();
        if (previous != null) {
            IrisPlatforms.bind(previous);
        }
    }

    /** A minimal V2 .iob under {@code <pack>/objects/<key>.iob} carrying only the given palette. */
    public static File writeObject(File pack, String key, String... palette) throws IOException {
        File file = new File(pack, "objects/" + key + ".iob");
        file.getParentFile().mkdirs();
        try (DataOutputStream out = new DataOutputStream(new FileOutputStream(file))) {
            out.writeInt(1);
            out.writeInt(1);
            out.writeInt(1);
            out.writeUTF("Iris V2 IOB;");
            out.writeShort(palette.length);
            for (String entry : palette) {
                out.writeUTF(entry);
            }
            out.writeInt(0);
            out.writeInt(0);
        }
        return file;
    }

    /**
     * A V2 .iob whose palette is {@code palette} and which carries one block per palette entry, entry {@code i} at
     * {@code (i, 0, 0)} relative to the object origin, so a placer actually has something to stamp.
     */
    public static File writeObjectWithBlocks(File pack, String key, String... palette) throws IOException {
        File file = new File(pack, "objects/" + key + ".iob");
        file.getParentFile().mkdirs();
        try (DataOutputStream out = new DataOutputStream(new FileOutputStream(file))) {
            out.writeInt(palette.length);
            out.writeInt(1);
            out.writeInt(1);
            out.writeUTF("Iris V2 IOB;");
            out.writeShort(palette.length);
            for (String entry : palette) {
                out.writeUTF(entry);
            }
            out.writeInt(palette.length);
            for (int index = 0; index < palette.length; index++) {
                out.writeShort(index);
                out.writeShort(0);
                out.writeShort(0);
                out.writeShort(index);
            }
            out.writeInt(0);
        }
        return file;
    }

    public static File write(File pack, String path, String content) throws IOException {
        File file = new File(pack, path);
        file.getParentFile().mkdirs();
        Files.write(file.toPath(), content.getBytes(StandardCharsets.UTF_8));
        return file;
    }

    private record FixedRegistries(List<String> blocks) implements PlatformRegistries {
        private static String base(String key) {
            if (key == null) {
                return null;
            }
            String trimmed = key.trim().toLowerCase(Locale.ROOT);
            int props = trimmed.indexOf('[');
            String head = props < 0 ? trimmed : trimmed.substring(0, props);
            return head.indexOf(':') < 0 ? "minecraft:" + head : head;
        }

        private boolean has(String key) {
            String normalized = base(key);
            if (normalized == null) {
                return false;
            }
            for (String block : blocks) {
                if (base(block).equals(normalized)) {
                    return true;
                }
            }
            return false;
        }

        @Override
        public PlatformBlockState block(String key) {
            PlatformBlockState state = blockOrNull(key, false);
            return state == null ? air() : state;
        }

        @Override
        public PlatformBlockState blockOrNull(String key) {
            return blockOrNull(key, false);
        }

        @Override
        public PlatformBlockState blockOrNull(String key, boolean warn) {
            return has(key) ? StateCache.of(base(key)) : null;
        }

        @Override
        public PlatformBlockState air() {
            return StateCache.of("minecraft:air");
        }

        @Override
        public PlatformBlockState deepSlateOre(PlatformBlockState block, PlatformBlockState ore) {
            return ore;
        }

        @Override
        public PlatformBiome biome(String key) {
            return null;
        }

        @Override
        public PlatformItem item(String key) {
            return null;
        }

        @Override
        public PlatformEntityType entity(String key) {
            return null;
        }

        @Override
        public List<String> blockKeys() {
            return blocks;
        }

        @Override
        public List<String> biomeKeys() {
            return List.of();
        }

        @Override
        public List<String> structureKeys() {
            return List.of();
        }

        @Override
        public List<String> itemKeys() {
            return List.of();
        }

        @Override
        public List<String> entityKeys() {
            return List.of();
        }

        @Override
        public List<String> blockTypeKeys() {
            return blocks;
        }

        @Override
        public List<String> enchantmentKeys() {
            return List.of();
        }

        @Override
        public List<String> potionEffectKeys() {
            return List.of();
        }

        @Override
        public List<String> lootTableKeys() {
            return List.of();
        }

        @Override
        public Map<String, List<PlatformBlockProperty>> blockStateProperties() {
            return Map.of();
        }
    }

    /**
     * One interned mock per key so {@code matches} and identity behave like a real adapter. The physical predicates
     * answer like the real thing too: {@code B} caches the first registries it sees for the whole JVM, so a state
     * handed out here can outlive the test that bound this platform.
     */
    private static final class StateCache {
        private static final Map<String, PlatformBlockState> STATES = new LinkedHashMap<>();

        private StateCache() {
        }

        static synchronized PlatformBlockState of(String key) {
            return STATES.computeIfAbsent(key, k -> {
                boolean air = k.endsWith("air");
                PlatformBlockState state = mock(PlatformBlockState.class);
                when(state.key()).thenReturn(k);
                when(state.materialKey()).thenReturn(k);
                when(state.isAir()).thenReturn(air);
                when(state.isSolid()).thenReturn(!air && !k.endsWith("water"));
                when(state.isOccluding()).thenReturn(!air && !k.endsWith("water"));
                when(state.isFluid()).thenReturn(k.endsWith("water"));
                when(state.isWater()).thenReturn(k.endsWith("water"));
                when(state.placementBaseState()).thenReturn(state);
                return state;
            });
        }
    }

    private static final class NoOpPreservationRegistry implements PreservationRegistry {
        @Override
        public void register(Thread thread) {
        }

        @Override
        public void register(ExecutorService service) {
        }

        @Override
        public void registerCache(MeteredCache cache) {
        }

        @Override
        public void dereference() {
        }
    }
}

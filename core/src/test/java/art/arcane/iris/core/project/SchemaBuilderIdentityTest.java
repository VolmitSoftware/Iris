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

package art.arcane.iris.core.project;

import art.arcane.iris.core.loader.IrisData;
import art.arcane.iris.core.loader.IrisRegistrant;
import art.arcane.iris.core.loader.ResourceLoader;
import art.arcane.iris.engine.object.IrisBiome;
import art.arcane.iris.engine.object.IrisBlockData;
import art.arcane.iris.engine.object.IrisDimension;
import art.arcane.iris.engine.object.IrisEntity;
import art.arcane.iris.engine.object.IrisExpression;
import art.arcane.iris.engine.object.IrisGenerator;
import art.arcane.iris.engine.object.IrisHydrology;
import art.arcane.iris.engine.object.IrisImageMap;
import art.arcane.iris.engine.object.IrisJigsawPiece;
import art.arcane.iris.engine.object.IrisLootTable;
import art.arcane.iris.engine.object.IrisObjectPlacement;
import art.arcane.iris.engine.object.IrisRegion;
import art.arcane.iris.engine.object.IrisStructure;
import art.arcane.iris.engine.object.IrisStructurePlacement;
import art.arcane.iris.engine.object.IrisVanillaStructureAdjustment;
import art.arcane.iris.spi.IrisPlatform;
import art.arcane.iris.spi.IrisPlatforms;
import art.arcane.iris.spi.LogLevel;
import art.arcane.iris.spi.PlatformBiome;
import art.arcane.iris.spi.PlatformBiomeWriter;
import art.arcane.iris.spi.PlatformBlockProperty;
import art.arcane.iris.spi.PlatformBlockState;
import art.arcane.iris.spi.PlatformEntityType;
import art.arcane.iris.spi.PlatformItem;
import art.arcane.iris.spi.PlatformNumericRange;
import art.arcane.iris.spi.PlatformRegistries;
import art.arcane.iris.spi.PlatformScheduler;
import art.arcane.iris.spi.PlatformStructureHooks;
import art.arcane.iris.spi.PlatformWorld;
import art.arcane.iris.testsupport.PlatformLeakGuard;
import art.arcane.volmlib.util.collection.KList;
import art.arcane.volmlib.util.collection.KMap;
import art.arcane.volmlib.util.json.JSONArray;
import art.arcane.volmlib.util.json.JSONObject;
import org.junit.After;
import org.junit.Before;
import org.junit.ClassRule;
import org.junit.Test;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeSet;

import static org.junit.Assert.assertEquals;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class SchemaBuilderIdentityTest {
    @ClassRule
    public static final PlatformLeakGuard PLATFORM_GUARD = PlatformLeakGuard.clean();

    private static final String EXPECTED_SCHEMA_DIGEST = "375be26228878d948d5dd4ccd4593fc3285c9a5a1dd1943a38e289d7c932c711";

    private static final List<Class<?>> SCHEMA_ROOTS = List.of(
            IrisDimension.class,
            IrisRegion.class,
            IrisBiome.class,
            IrisGenerator.class,
            IrisHydrology.class,
            IrisObjectPlacement.class,
            IrisStructure.class,
            IrisStructurePlacement.class,
            IrisJigsawPiece.class,
            IrisVanillaStructureAdjustment.class,
            IrisEntity.class,
            IrisLootTable.class,
            IrisBlockData.class,
            IrisImageMap.class,
            IrisExpression.class);

    private IrisPlatform previous;

    @Before
    public void bindFakePlatform() {
        previous = IrisPlatforms.isBound() ? IrisPlatforms.get() : null;
        if (previous != null) {
            IrisPlatforms.unbind();
        }
        IrisPlatforms.bind(new FakePlatform());
    }

    @After
    public void restorePlatform() {
        IrisPlatforms.unbind();
        if (previous != null) {
            IrisPlatforms.bind(previous);
        }
    }

    @Test
    public void studioSchemaOutputIsByteIdentical() {
        StringBuilder canonical = new StringBuilder();
        for (Class<?> root : SCHEMA_ROOTS) {
            canonical.append(root.getName()).append('\n');
            canonical.append(canonicalize(new SchemaBuilder(root, schemaData()).construct()));
            canonical.append('\n');
        }
        assertEquals(EXPECTED_SCHEMA_DIGEST, digest(canonical.toString()));
    }

    private static String digest(String value) {
        MessageDigest sha256;
        try {
            sha256 = MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
        byte[] hash = sha256.digest(value.getBytes(StandardCharsets.UTF_8));
        StringBuilder hex = new StringBuilder(hash.length * 2);
        for (byte b : hash) {
            hex.append(Character.forDigit((b >> 4) & 0xF, 16));
            hex.append(Character.forDigit(b & 0xF, 16));
        }
        return hex.toString();
    }

    private static String canonicalize(Object value) {
        StringBuilder out = new StringBuilder();
        appendCanonical(out, value);
        return out.toString();
    }

    private static void appendCanonical(StringBuilder out, Object value) {
        if (value instanceof JSONObject object) {
            out.append('{');
            boolean first = true;
            for (String key : new TreeSet<>(object.keySet())) {
                if (!first) {
                    out.append(',');
                }
                first = false;
                out.append('"').append(key).append("\":");
                appendCanonical(out, object.get(key));
            }
            out.append('}');
            return;
        }
        if (value instanceof JSONArray array) {
            out.append('[');
            for (int index = 0; index < array.length(); index++) {
                if (index > 0) {
                    out.append(',');
                }
                appendCanonical(out, array.get(index));
            }
            out.append(']');
            return;
        }
        out.append('<').append(value == null ? "null" : value.getClass().getSimpleName()).append(':');
        out.append(String.valueOf(value)).append('>');
    }

    private static IrisData schemaData() {
        IrisData data = mock(IrisData.class);
        ResourceLoader<IrisStructure> structureLoader = mock(ResourceLoader.class);
        ResourceLoader<IrisJigsawPiece> pieceLoader = mock(ResourceLoader.class);
        ResourceLoader<IrisExpression> expressionLoader = mock(ResourceLoader.class);
        ResourceLoader<IrisBlockData> blockLoader = mock(ResourceLoader.class);
        KMap<Class<? extends IrisRegistrant>, ResourceLoader<? extends IrisRegistrant>> loaders = new KMap<>();
        loaders.put(IrisExpression.class, expressionLoader);
        when(data.getStructureLoader()).thenReturn(structureLoader);
        when(data.getJigsawPieceLoader()).thenReturn(pieceLoader);
        when(data.getBlockLoader()).thenReturn(blockLoader);
        when(data.getLoaders()).thenReturn(loaders);
        when(data.getPossibleSnippets(anyString())).thenReturn(new KList<String>().qadd("alpha").qadd("beta"));
        when(structureLoader.getPossibleKeys()).thenReturn(new String[]{"pack/tower", "pack/keep"});
        when(pieceLoader.getPossibleKeys()).thenReturn(new String[]{"pack/tower-base"});
        when(blockLoader.getPossibleKeys()).thenReturn(new String[]{"pack/mossy_stone"});
        when(expressionLoader.getPossibleKeys()).thenReturn(new String[]{"pack/height"});
        when(expressionLoader.getFolderName()).thenReturn("expressions");
        when(expressionLoader.getResourceTypeName()).thenReturn("Expression");
        return data;
    }

    private static final class FakeRegistries implements PlatformRegistries {
        private static final List<PlatformBlockProperty> STONE_PROPERTIES = List.of(
                new PlatformBlockProperty("waterlogged", "boolean", Boolean.FALSE, List.of(Boolean.TRUE, Boolean.FALSE), null),
                new PlatformBlockProperty("level", "integer", 0, List.of(0, 1, 2),
                        new PlatformNumericRange(0D, 2D, false, false)));
        private static final List<PlatformBlockProperty> LOG_PROPERTIES = List.of(
                new PlatformBlockProperty("axis", "string", "y", List.of("x", "y", "z"), null));

        @Override
        public PlatformBlockState block(String key) {
            return null;
        }

        @Override
        public PlatformBlockState blockOrNull(String key) {
            return null;
        }

        @Override
        public PlatformBlockState blockOrNull(String key, boolean warn) {
            return null;
        }

        @Override
        public PlatformBlockState air() {
            return null;
        }

        @Override
        public PlatformBlockState deepSlateOre(PlatformBlockState block, PlatformBlockState ore) {
            return null;
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
            return List.of("minecraft:stone", "minecraft:oak_log");
        }

        @Override
        public List<String> biomeKeys() {
            return List.of("minecraft:plains", "minecraft:desert", "cool_mod:sky_meadow");
        }

        @Override
        public List<String> specialEntityKeys() {
            return List.of("mythicmobs:skeleton_king");
        }

        @Override
        public List<String> structureKeys() {
            return List.of("minecraft:monument", "minecraft:stronghold", "cool_mod:sky_temple");
        }

        @Override
        public List<String> itemKeys() {
            return List.of("minecraft:stone", "minecraft:diamond_sword", "cool_mod:ruby");
        }

        @Override
        public List<String> entityKeys() {
            return List.of("minecraft:zombie", "cool_mod:grizzly_bear");
        }

        @Override
        public List<String> blockTypeKeys() {
            return List.of("minecraft:stone", "minecraft:oak_log");
        }

        @Override
        public List<String> enchantmentKeys() {
            return List.of("minecraft:sharpness", "cool_mod:vorpal");
        }

        @Override
        public List<String> potionEffectKeys() {
            return List.of("minecraft:speed", "minecraft:slow_falling", "sniffer_mod:mega_boost");
        }

        @Override
        public List<String> lootTableKeys() {
            return List.of("minecraft:chests/simple_dungeon", "cool_mod:chests/sky_temple");
        }

        @Override
        public Map<String, List<PlatformBlockProperty>> blockStateProperties() {
            Map<String, List<PlatformBlockProperty>> properties = new LinkedHashMap<>();
            properties.put("minecraft:stone", STONE_PROPERTIES);
            properties.put("minecraft:andesite", STONE_PROPERTIES);
            properties.put("minecraft:oak_log", LOG_PROPERTIES);
            return properties;
        }
    }

    private static final class FakePlatform implements IrisPlatform {
        private final PlatformRegistries registries = new FakeRegistries();

        @Override
        public String platformName() {
            return "fake";
        }

        @Override
        public String minecraftVersion() {
            return "0.0.0";
        }

        @Override
        public PlatformRegistries registries() {
            return registries;
        }

        @Override
        public PlatformScheduler scheduler() {
            return null;
        }

        @Override
        public PlatformStructureHooks structureHooks() {
            return null;
        }

        @Override
        public PlatformBiomeWriter biomeWriter() {
            return null;
        }

        @Override
        public File dataFolder() {
            return new File(".");
        }

        @Override
        public File dataFile(String... path) {
            return new File(".");
        }

        @Override
        public File pluginJar() {
            return new File(".");
        }

        @Override
        public int irisVersionNumber() {
            return 0;
        }

        @Override
        public int minecraftVersionNumber() {
            return 0;
        }

        @Override
        public void callEvent(Object event) {
        }

        @Override
        public void dispatchConsoleCommand(String command) {
        }

        @Override
        public boolean spawnEntity(PlatformWorld world, String entityKey, double x, double y, double z) {
            return false;
        }

        @Override
        public void log(LogLevel level, String message) {
        }

        @Override
        public void msg(String message) {
        }

        @Override
        public void reportError(Throwable error) {
        }
    }
}

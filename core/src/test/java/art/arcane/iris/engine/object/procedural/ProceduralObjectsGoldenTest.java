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

package art.arcane.iris.engine.object.procedural;

import art.arcane.iris.builtin.objects.ProceduralObjectSettings;
import art.arcane.iris.modules.objects.PlacementChanges;
import art.arcane.iris.modules.objects.ProceduralPlacement;
import art.arcane.iris.core.loader.IrisData;
import art.arcane.iris.core.loader.ResourceLoader;
import art.arcane.iris.core.modules.IrisPackResources;
import art.arcane.iris.core.modules.PlatformBlockStates;
import art.arcane.iris.modules.objects.EffectiveSettings;
import art.arcane.iris.modules.objects.ObjectBuilder;
import com.volmit.block.BlockTurn;
import com.volmit.object.PlaceableObject;
import com.volmit.resource.PackResources;
import art.arcane.iris.engine.UsableBlockPlatform;
import art.arcane.iris.engine.object.prefab.IrisObject;
import art.arcane.iris.util.common.data.B;

import com.volmit.geometry.BlockFace;
import com.volmit.block.BlockRegistry;
import com.volmit.block.BlockSpec;
import com.volmit.block.BlockState;
import com.volmit.block.BlockTrait;
import com.volmit.block.PaletteSampler;
import com.volmit.block.PaletteSpec;
import com.volmit.object.BlockEntityData;
import com.volmit.resource.ResourceId;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.After;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.IOException;
import java.io.InputStream;
import java.net.URISyntaxException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;
import java.util.stream.Stream;

import static org.junit.Assert.assertEquals;
import static org.mockito.Mockito.CALLS_REAL_METHODS;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.withSettings;
import static org.mockito.Mockito.when;

/**
 * Every procedural object of the overworld pack (its biomes' and regions' {@code proceduralObjects}, with the pack's
 * snippets) bakes the same variants it did before the generators moved onto IrisApi block states: same load keys,
 * sizes, blocks in the same order, and the same placement options.
 */
public class ProceduralObjectsGoldenTest {
    private static final String FIXTURE = "/procedural-golden";
    private static final String PALETTE_TREE = "/procedural-palette-tree.json";
    /** Digests on a platform whose blocks have no properties and which knows no block by {@code blockOrNull}. */
    private static final Map<String, String> BLOCK_ONLY = Map.ofEntries(
            Map.entry("biomes-carving-frost-shards", "33bda9f1dd351fc4"),
            Map.entry("biomes-estranged-baobab-grove", "155d29fcea336a86"),
            Map.entry("biomes-estranged-birchglass-heath", "d21a050d546ba00e"),
            Map.entry("biomes-estranged-bloomspire-orchard", "0d05b575880fc6a1"),
            Map.entry("biomes-estranged-bonsai", "9245745f24146cdf"),
            Map.entry("biomes-estranged-candlewood-grove", "3783b4cf5287abf6"),
            Map.entry("biomes-estranged-emberbark-woods", "60f6b58da334757c"),
            Map.entry("biomes-estranged-mistveil-swamp", "16022337e82c65c4"),
            Map.entry("biomes-estranged-pallid-necropolis", "093577a814639497"),
            Map.entry("biomes-estranged-skyreach-pines", "3764a9ccd2431952"),
            Map.entry("biomes-estranged-slinky-spires", "ee3b63138b2c592e"),
            Map.entry("biomes-estranged-snarlwood-thicket", "5afed9c5cc066fc7"),
            Map.entry("biomes-estranged-spindlewood", "721119eefe160749"),
            Map.entry("biomes-estranged-stiltroot-shallows", "dccb4f3201ae2188"),
            Map.entry("biomes-estranged-taxodium", "b2a9eae3326eeb5a"),
            Map.entry("biomes-estranged-weeping-mire", "02585c9591104a05"),
            Map.entry("biomes-frozen-fields-hilly-plains", "5b5b44388604bc41"),
            Map.entry("biomes-frozen-ice-spikes", "7a8fb734e5f4af18"),
            Map.entry("biomes-frozen-sea-frozen-parent-river", "d1b78bab26fc94bd"),
            Map.entry("biomes-magnetics-glass", "f777c14cb83db061"),
            Map.entry("biomes-magnetics-glass-shard", "2e0a6ccb1bdc848b"),
            Map.entry("biomes-ocean-warm", "caabea76170e4041"),
            Map.entry("biomes-temperate-sea-ocean", "c822f4f8d991763b"),
            Map.entry("regions-estranged", "ba1d3ef0705b6364"),
            Map.entry("regions-mushroom", "a594950e02f8211f"));
    /** Digests on {@link GameLikeBlocks}. */
    private static final Map<String, String> GAME_LIKE = Map.ofEntries(
            Map.entry("biomes-carving-frost-shards", "fcfec28e5ef4de43"),
            Map.entry("biomes-estranged-baobab-grove", "b42945a646ba8ee1"),
            Map.entry("biomes-estranged-birchglass-heath", "c3b414d6ed63320f"),
            Map.entry("biomes-estranged-bloomspire-orchard", "0c90f1c035e42219"),
            Map.entry("biomes-estranged-bonsai", "30ada2214f2ef430"),
            Map.entry("biomes-estranged-candlewood-grove", "8485117062466b15"),
            Map.entry("biomes-estranged-emberbark-woods", "bd3b5b08965b6689"),
            Map.entry("biomes-estranged-mistveil-swamp", "12280976ccb0f671"),
            Map.entry("biomes-estranged-pallid-necropolis", "51b3e57c1788c8dc"),
            Map.entry("biomes-estranged-skyreach-pines", "fea026b987372ea9"),
            Map.entry("biomes-estranged-slinky-spires", "a64191700ef3d1de"),
            Map.entry("biomes-estranged-snarlwood-thicket", "6a9a1cf1c2968708"),
            Map.entry("biomes-estranged-spindlewood", "0e4633d9e3e9748f"),
            Map.entry("biomes-estranged-stiltroot-shallows", "2d1ffba121e6e1ef"),
            Map.entry("biomes-estranged-taxodium", "234615968e3201bb"),
            Map.entry("biomes-estranged-weeping-mire", "b50340bcf83f74e7"),
            Map.entry("biomes-frozen-fields-hilly-plains", "1cbca204dfe7406c"),
            Map.entry("biomes-frozen-ice-spikes", "8fe197db8844b5d9"),
            Map.entry("biomes-frozen-sea-frozen-parent-river", "575ec6e46eb52840"),
            Map.entry("biomes-magnetics-glass", "2b49f0d91feffa42"),
            Map.entry("biomes-magnetics-glass-shard", "48e61318dd2e3569"),
            Map.entry("biomes-ocean-warm", "5792d4354ee2b17b"),
            Map.entry("biomes-temperate-sea-ocean", "b2cde747ce90efea"),
            Map.entry("regions-estranged", "a887509dbcc03041"),
            Map.entry("regions-mushroom", "9fe1cb03e1f387a4"));

    @Rule
    public TemporaryFolder folder = new TemporaryFolder();

    @After
    public void unbindPlatform() {
        UsableBlockPlatform.unbind();
    }

    @Test
    public void onABlockOnlyPlatformEveryProceduralObjectOfTheOverworldPackBakesAsBefore() throws Exception {
        UsableBlockPlatform.bind();
        assertEquals(new TreeMap<>(BLOCK_ONLY), bakeAll());
    }

    @Test
    public void onAGameLikePlatformEveryProceduralObjectOfTheOverworldPackBakesAsBefore() throws Exception {
        UsableBlockPlatform.bind(new GameLikeBlocks().registries());
        assertEquals(new TreeMap<>(GAME_LIKE), bakeAll());
    }

    /**
     * A tree palette picks through Iris's own palette noise, seeded and positioned as before the port: the digest was
     * taken from this build after a probe of the same tree gave one signature on it and on the build before the port.
     */
    @Test
    public void treePalettesPickWithIrisPaletteNoiseAsBefore() throws Exception {
        UsableBlockPlatform.bind();
        IrisData data = mock(IrisData.class, withSettings().stubOnly().defaultAnswer(CALLS_REAL_METHODS));
        doReturn(folder.getRoot()).when(data).getDataFolder();
        doReturn(mock(ResourceLoader.class)).when(data).getBlockLoader();
        doReturn(null).when(data).getEngine();
        IrisPackResources resources = new IrisPackResources(data);
        doReturn(resources).when(data).getPackResources();
        String json = Files.readString(Path.of(ProceduralObjectsGoldenTest.class.getResource(PALETTE_TREE).toURI()));
        ProceduralObjectSettings settings = new GsonBuilder().registerTypeAdapterFactory(data).create()
                .fromJson(json, ProceduralObjectSettings.class);

        assertEquals("6936acdff5cdd277", bake(settings, data));
    }

    private Map<String, String> bakeAll() throws Exception {
        copyFixtureInto(folder.getRoot().toPath());
        IrisData data = mock(IrisData.class, withSettings().stubOnly().defaultAnswer(CALLS_REAL_METHODS));
        IrisPackResources resources = mock(IrisPackResources.class, withSettings().stubOnly());
        doReturn(folder.getRoot()).when(data).getDataFolder();
        doReturn(resources).when(data).getPackResources();
        doReturn(mock(ResourceLoader.class)).when(data).getBlockLoader();
        doReturn(null).when(data).getEngine();
        IrisPackResources host = new IrisPackResources(data);
        when(resources.blocks()).thenReturn(new HashedPalettes(host.blocks()));
        when(resources.objects()).thenReturn(host.objects());
        Gson gson = new GsonBuilder().registerTypeAdapterFactory(data).create();
        JsonObject sets = JsonParser.parseString(Files.readString(folder.getRoot().toPath().resolve("sets.json"))).getAsJsonObject();

        Map<String, String> actual = new TreeMap<>();
        for (String name : sets.keySet()) {
            ProceduralObjectSettings settings = gson.fromJson(sets.get(name), ProceduralObjectSettings.class);
            actual.put(name, bake(settings, data));
        }
        return actual;
    }

    private static String bake(ProceduralObjectSettings settings, IrisData data) throws NoSuchAlgorithmException {
        Gson plain = new Gson();
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        PackResources resources = data.getPackResources();
        ObjectBuilder builder = new ObjectBuilder(resources);
        EffectiveSettings options = new EffectiveSettings();
        for (ProceduralPlacement placement : ResolvedProceduralObjects.resolve(settings, resources)) {
            update(digest, placement.name() + '|' + placement.chance() + '|' + placement.density() + '|'
                    + placement.plausible() + '|' + plain.toJson(options.of(placement, PlacementChanges.NONE)) + '\n');
            for (PlaceableObject variant : ((ResolvedProceduralPlacement) placement).bake(builder)) {
                IrisObject object = (IrisObject) variant;
                update(digest, object.getLoadKey() + ' ' + object.getW() + 'x' + object.getH() + 'x' + object.getD() + ':');
                object.getBlocks().forEach((position, state) -> update(digest, position.getBlockX() + "," + position.getBlockY()
                        + "," + position.getBlockZ() + "=" + state.key() + ";"));
                update(digest, "\n");
            }
        }
        return HexFormat.of().formatHex(digest.digest(), 0, 8);
    }

    private static void update(MessageDigest digest, String text) {
        digest.update(text.getBytes(StandardCharsets.UTF_8));
    }

    /**
     * Blocks as the host resolves them, and palettes that pick a block from a hash of their seed and the position, so a
     * generator asking with another seed or position gives another digest. Iris's real palette noise needs a platform the
     * test does not have; it is the host's, unchanged by the port.
     */
    private static final class HashedPalettes implements BlockRegistry {
        private final BlockRegistry host;

        private HashedPalettes(BlockRegistry host) {
            this.host = host;
        }

        @Override
        public Optional<BlockState> find(String key) {
            return host.find(key);
        }

        @Override
        public BlockState withProperty(BlockState state, String name, String value) {
            return host.withProperty(state, name, value);
        }

        @Override
        public BlockState rotate(BlockState state, BlockTurn turn) {
            throw new UnsupportedOperationException();
        }

        @Override
        public BlockState merge(BlockState state, BlockState onto) {
            throw new UnsupportedOperationException();
        }

        @Override
        public boolean matches(BlockState state, BlockState pattern) {
            throw new UnsupportedOperationException();
        }

        @Override
        public BlockState resolve(BlockSpec spec) {
            return stateOf(spec.block());
        }

        @Override
        public BlockState oreFor(BlockState rock, BlockState ore) {
            return ore;
        }

        private static BlockState stateOf(String key) {
            return BlockState.parse(PlatformBlockStates.namespaced(B.getState(key).key()));
        }

        @Override
        public PaletteSampler palette(PaletteSpec spec, long seed) {
            List<BlockSpec> blocks = spec.palette();
            return (x, y, z) -> {
                long hash = seed * 31 + Double.hashCode(x) * 961L + Double.hashCode(y) * 29791L + Double.hashCode(z);
                return resolve(blocks.get(Math.floorMod(Long.hashCode(hash), blocks.size())));
            };
        }

        @Override
        public Optional<BlockEntityData> blockEntity(BlockSpec spec) {
            throw new UnsupportedOperationException();
        }

        @Override
        public boolean hasTrait(BlockState state, BlockTrait trait) {
            throw new UnsupportedOperationException();
        }

        @Override
        public boolean inTag(BlockState state, ResourceId tag) {
            throw new UnsupportedOperationException();
        }

        @Override
        public boolean canAttach(BlockState state, BlockState support, BlockFace face) {
            throw new UnsupportedOperationException();
        }

        @Override
        public boolean canPlaceOn(BlockState state, BlockState below) {
            throw new UnsupportedOperationException();
        }
    }

    private static void copyFixtureInto(Path target) throws IOException, URISyntaxException {
        Path source = Path.of(ProceduralObjectsGoldenTest.class.getResource(FIXTURE).toURI());
        try (Stream<Path> files = Files.walk(source)) {
            for (Path file : files.filter(Files::isRegularFile).toList()) {
                Path copy = target.resolve(source.relativize(file).toString());
                Files.createDirectories(copy.getParent());
                try (InputStream in = Files.newInputStream(file)) {
                    Files.copy(in, copy, StandardCopyOption.REPLACE_EXISTING);
                }
            }
        }
    }
}

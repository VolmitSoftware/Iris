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

import art.arcane.iris.modules.objects.ProceduralObjectSettings;
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
            Map.entry("biomes-carving-frost-shards", "e07f555cae739a71"),
            Map.entry("biomes-estranged-baobab-grove", "4f9da6a2c02ed6e9"),
            Map.entry("biomes-estranged-birchglass-heath", "3d687b1508b55c9e"),
            Map.entry("biomes-estranged-bloomspire-orchard", "c02124a9e0e110d1"),
            Map.entry("biomes-estranged-bonsai", "2bbe95ef2341e459"),
            Map.entry("biomes-estranged-candlewood-grove", "b1b6e514f45e6e3d"),
            Map.entry("biomes-estranged-emberbark-woods", "058932e9d4d5b0d3"),
            Map.entry("biomes-estranged-mistveil-swamp", "52e03623ed97b52c"),
            Map.entry("biomes-estranged-pallid-necropolis", "5b4dac167760b356"),
            Map.entry("biomes-estranged-skyreach-pines", "775a653c26c3f1ce"),
            Map.entry("biomes-estranged-slinky-spires", "1601adf817f0c9f8"),
            Map.entry("biomes-estranged-snarlwood-thicket", "d85da8751c1dc69a"),
            Map.entry("biomes-estranged-spindlewood", "378af33a84e18495"),
            Map.entry("biomes-estranged-stiltroot-shallows", "8853f17d5363cac3"),
            Map.entry("biomes-estranged-taxodium", "760ac58118bf3980"),
            Map.entry("biomes-estranged-weeping-mire", "5bc2338e974ef85e"),
            Map.entry("biomes-frozen-fields-hilly-plains", "1807f6e5c34454af"),
            Map.entry("biomes-frozen-ice-spikes", "67141d051a475b74"),
            Map.entry("biomes-frozen-sea-frozen-parent-river", "2b2dbcf872814df7"),
            Map.entry("biomes-magnetics-glass", "0e665e690219026f"),
            Map.entry("biomes-magnetics-glass-shard", "c19c04ce047c3b6b"),
            Map.entry("biomes-ocean-warm", "d01adb88188d5d6a"),
            Map.entry("biomes-temperate-sea-ocean", "f07bf98a3e6f1f62"),
            Map.entry("regions-estranged", "54a7a643793f9a2a"),
            Map.entry("regions-mushroom", "8798b6e3efde6124"));
    /** Digests on {@link GameLikeBlocks}. */
    private static final Map<String, String> GAME_LIKE = Map.ofEntries(
            Map.entry("biomes-carving-frost-shards", "58913e09aad05c20"),
            Map.entry("biomes-estranged-baobab-grove", "e6f41310af0acf33"),
            Map.entry("biomes-estranged-birchglass-heath", "14d377127568b966"),
            Map.entry("biomes-estranged-bloomspire-orchard", "53206cf7604536ea"),
            Map.entry("biomes-estranged-bonsai", "e9cdda72bb5677e8"),
            Map.entry("biomes-estranged-candlewood-grove", "0b981b474a763176"),
            Map.entry("biomes-estranged-emberbark-woods", "c6a910701a769bc2"),
            Map.entry("biomes-estranged-mistveil-swamp", "93de774da3bd3b69"),
            Map.entry("biomes-estranged-pallid-necropolis", "09b3f2e782bd0cbe"),
            Map.entry("biomes-estranged-skyreach-pines", "d10bf77da543ea50"),
            Map.entry("biomes-estranged-slinky-spires", "e82890512992f1f4"),
            Map.entry("biomes-estranged-snarlwood-thicket", "1dbdd16f1b1d5c69"),
            Map.entry("biomes-estranged-spindlewood", "705d29f85b3f6a1c"),
            Map.entry("biomes-estranged-stiltroot-shallows", "7b21912e1aeceb7c"),
            Map.entry("biomes-estranged-taxodium", "58628d26bc7aa937"),
            Map.entry("biomes-estranged-weeping-mire", "33a5865800e722ce"),
            Map.entry("biomes-frozen-fields-hilly-plains", "3ac105850fed5c6a"),
            Map.entry("biomes-frozen-ice-spikes", "7bd31e676851af69"),
            Map.entry("biomes-frozen-sea-frozen-parent-river", "74bbe0fc6a62f1f0"),
            Map.entry("biomes-magnetics-glass", "6f865dbdf86c2231"),
            Map.entry("biomes-magnetics-glass-shard", "9043905e77b0e2d4"),
            Map.entry("biomes-ocean-warm", "d02b2b5c0ea91a8d"),
            Map.entry("biomes-temperate-sea-ocean", "a60f0e54f345ae50"),
            Map.entry("regions-estranged", "5719b6d81d0b1946"),
            Map.entry("regions-mushroom", "03952e52695a0652"));

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

        assertEquals("b935b5c5c85d5162", bake(settings, data));
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

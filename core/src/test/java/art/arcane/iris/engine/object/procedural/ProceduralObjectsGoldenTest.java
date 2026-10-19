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

import art.arcane.iris.builtin.objects.ObjectHost;
import art.arcane.iris.builtin.objects.PlaceableObject;
import art.arcane.iris.builtin.objects.ProceduralObjectSettings;
import art.arcane.iris.builtin.objects.ProceduralPlacement;
import art.arcane.iris.core.loader.IrisData;
import art.arcane.iris.core.loader.ResourceLoader;
import art.arcane.iris.core.modules.IrisObjectBuilder;
import art.arcane.iris.core.modules.IrisPackResources;
import art.arcane.iris.core.modules.PlatformBlockStates;
import art.arcane.iris.core.modules.ProceduralOptions;
import art.arcane.iris.engine.UsableBlockPlatform;
import art.arcane.iris.engine.object.prefab.IrisObject;
import art.arcane.iris.util.common.data.B;

import com.volmit.block.BlockFace;
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
            Map.entry("biomes-carving-frost-shards", "954613b558bd0e8b"),
            Map.entry("biomes-estranged-baobab-grove", "3232037f828a9107"),
            Map.entry("biomes-estranged-birchglass-heath", "a55859aa6a343b2a"),
            Map.entry("biomes-estranged-bloomspire-orchard", "4f293781cb8a3096"),
            Map.entry("biomes-estranged-bonsai", "a5f3bfca48d9765e"),
            Map.entry("biomes-estranged-candlewood-grove", "54edc65fd907b0f0"),
            Map.entry("biomes-estranged-emberbark-woods", "aef11a05b3736872"),
            Map.entry("biomes-estranged-mistveil-swamp", "13465ade8e5ffb63"),
            Map.entry("biomes-estranged-pallid-necropolis", "d084420689990166"),
            Map.entry("biomes-estranged-skyreach-pines", "0ae4e50672f7b69c"),
            Map.entry("biomes-estranged-slinky-spires", "5e206ec07f334985"),
            Map.entry("biomes-estranged-snarlwood-thicket", "08121289e05d4828"),
            Map.entry("biomes-estranged-spindlewood", "e5e8b64cfb12fd28"),
            Map.entry("biomes-estranged-stiltroot-shallows", "eb83972fb116f254"),
            Map.entry("biomes-estranged-taxodium", "48a648d2f4f2715e"),
            Map.entry("biomes-estranged-weeping-mire", "d6ac39f8c2ffde66"),
            Map.entry("biomes-frozen-fields-hilly-plains", "170d6ed6174f8e40"),
            Map.entry("biomes-frozen-ice-spikes", "7ac324754cb82ff1"),
            Map.entry("biomes-frozen-sea-frozen-parent-river", "1d9f74d9713e1a4d"),
            Map.entry("biomes-magnetics-glass", "3f30c7fa5aa07410"),
            Map.entry("biomes-magnetics-glass-shard", "5e9834cd11338a4a"),
            Map.entry("biomes-ocean-warm", "606e256ae6242124"),
            Map.entry("biomes-temperate-sea-ocean", "ebd0960ae22f4fc6"),
            Map.entry("regions-estranged", "a806532a68e8c232"),
            Map.entry("regions-mushroom", "f08c6663543ac201"));
    /** Digests on {@link GameLikeBlocks}. */
    private static final Map<String, String> GAME_LIKE = Map.ofEntries(
            Map.entry("biomes-carving-frost-shards", "b425550ade26b810"),
            Map.entry("biomes-estranged-baobab-grove", "ba75886b7bc06be0"),
            Map.entry("biomes-estranged-birchglass-heath", "cb04761bb5074cf2"),
            Map.entry("biomes-estranged-bloomspire-orchard", "27dca6f700d2c683"),
            Map.entry("biomes-estranged-bonsai", "9d9a3eb1125a25fa"),
            Map.entry("biomes-estranged-candlewood-grove", "6c085c37f8993922"),
            Map.entry("biomes-estranged-emberbark-woods", "a8636178fffd89f2"),
            Map.entry("biomes-estranged-mistveil-swamp", "03fc65a53bf531d9"),
            Map.entry("biomes-estranged-pallid-necropolis", "0aeafb5af5224fd8"),
            Map.entry("biomes-estranged-skyreach-pines", "facf4e1de1615c66"),
            Map.entry("biomes-estranged-slinky-spires", "a8d019e9c4da8b14"),
            Map.entry("biomes-estranged-snarlwood-thicket", "e5ce78962c3853d0"),
            Map.entry("biomes-estranged-spindlewood", "71cca12bfe23e18e"),
            Map.entry("biomes-estranged-stiltroot-shallows", "21d67797a8e38e9d"),
            Map.entry("biomes-estranged-taxodium", "2f8f7b47d78102c9"),
            Map.entry("biomes-estranged-weeping-mire", "f871b1643435a2f7"),
            Map.entry("biomes-frozen-fields-hilly-plains", "6ab2b00dd640de13"),
            Map.entry("biomes-frozen-ice-spikes", "732b09dfd0799a23"),
            Map.entry("biomes-frozen-sea-frozen-parent-river", "a2fbf4ff15ad75aa"),
            Map.entry("biomes-magnetics-glass", "6dfa6bacedf24a5f"),
            Map.entry("biomes-magnetics-glass-shard", "21f08d7a93fd78c2"),
            Map.entry("biomes-ocean-warm", "3df57be11fa3d53c"),
            Map.entry("biomes-temperate-sea-ocean", "4e2269f4f39cff87"),
            Map.entry("regions-estranged", "61d9ed40fed38854"),
            Map.entry("regions-mushroom", "ccf240de087fac38"));

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

        assertEquals("393616b22e2dd257", bake(settings, data));
    }

    private Map<String, String> bakeAll() throws Exception {
        copyFixtureInto(folder.getRoot().toPath());
        IrisData data = mock(IrisData.class, withSettings().stubOnly().defaultAnswer(CALLS_REAL_METHODS));
        IrisPackResources resources = mock(IrisPackResources.class, withSettings().stubOnly());
        doReturn(folder.getRoot()).when(data).getDataFolder();
        doReturn(resources).when(data).getPackResources();
        when(resources.blocks()).thenReturn(new HashedPalettes());
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
        IrisObjectBuilder builder = new IrisObjectBuilder(data);
        for (ProceduralPlacement placement : ResolvedProceduralObjects.resolve(settings, mock(ObjectHost.class))) {
            update(digest, placement.name() + '|' + placement.chance() + '|' + placement.density() + '|'
                    + placement.plausible() + '|' + plain.toJson(ProceduralOptions.of(placement)) + '\n');
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
        @Override
        public BlockState resolve(String key) {
            return BlockState.parse(PlatformBlockStates.namespaced(B.getState(key).key()));
        }

        @Override
        public BlockState resolve(BlockSpec spec) {
            return resolve(spec.block());
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

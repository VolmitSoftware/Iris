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

package art.arcane.iris.generation.procedural;

import art.arcane.iris.generation.geometry.IrisBlockVector;
import art.arcane.iris.structure.object.IrisObject;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.io.IOException;
import java.net.URISyntaxException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

/**
 * Every procedural object of the overworld pack (its {@code procedural/} folder, with the palette snippets it names)
 * bakes, and bakes the same variants as when these digests were taken: same names, sizes and blocks, in the order
 * they were made. A digest that changes means world generation places different objects.
 * <p>
 * Shapes match Iris v5 block for block. Palette picks follow v4's noise, which scales zoom over fracture and cellular
 * and keeps the style's own power, so objects with a multi-block palette differ from v5's digests by design.
 */
public class ProceduralObjectsGoldenTest {
    private static final Map<String, String> GAME_LIKE = Map.ofEntries(
            Map.entry("coral/ocean/reef-brain", "b780ee9793980556"),
            Map.entry("coral/ocean/reef-branch", "5d9d2881b522ca52"),
            Map.entry("coral/ocean/reef-fan", "7aa06119d4a119c2"),
            Map.entry("coral/temperate/brain-coral", "7f61fb3597b4f07a"),
            Map.entry("coral/temperate/branch-coral", "85866503fe458e27"),
            Map.entry("crystals/estranged/amethyst-cluster", "8a1cf4b29c5e2643"),
            Map.entry("crystals/estranged/frostspar", "94adf86f507c9775"),
            Map.entry("crystals/frozen/glacial-frost-bloom", "269ba23560175716"),
            Map.entry("crystals/frozen/glacial-frost-sprigs", "2966bfc03d3ecc49"),
            Map.entry("formations/estranged/boulder", "8d8601e794ade5a3"),
            Map.entry("formations/estranged/obelisk-spire", "35c0817e7aef80e3"),
            Map.entry("formations/estranged/ossuary-spire", "b070c7cdcdcb1893"),
            Map.entry("formations/frozen/glacial-arch", "88b208875ba9137c"),
            Map.entry("formations/frozen/glacial-arch-2", "eda15bb101c732b2"),
            Map.entry("formations/frozen/glacial-cave-arch", "4106698fa11c4fd9"),
            Map.entry("formations/frozen/glacial-cave-fissure", "4b66f037cb8dfafc"),
            Map.entry("formations/frozen/glacial-cave-hanging-needle", "309896032f06a611"),
            Map.entry("formations/frozen/glacial-cave-iceberg", "85c94b00f016e48c"),
            Map.entry("formations/frozen/glacial-cave-needle", "958c6a5be931e30d"),
            Map.entry("formations/frozen/glacial-cave-overhang", "084251a8b3b73489"),
            Map.entry("formations/frozen/glacial-cave-shard-fan", "bdf6728f843298a5"),
            Map.entry("formations/frozen/glacial-cave-spiral", "95dec28606d59bd1"),
            Map.entry("formations/frozen/glacial-crooked-spire", "2a65f5c2c165519e"),
            Map.entry("formations/frozen/glacial-crooked-spire-2", "29db29d789a47ad8"),
            Map.entry("formations/frozen/glacial-drift", "d6bf138b5fedf4ec"),
            Map.entry("formations/frozen/glacial-fissure", "e6f03adedc95d4b5"),
            Map.entry("formations/frozen/glacial-fissure-2", "414b7ca59be86d70"),
            Map.entry("formations/frozen/glacial-iceberg", "68204af6fd4e82c9"),
            Map.entry("formations/frozen/glacial-iceberg-2", "c94e60ef36416a21"),
            Map.entry("formations/frozen/glacial-overhang", "8c833445d4bdee4c"),
            Map.entry("formations/frozen/glacial-overhang-2", "33d4344f779ecc12"),
            Map.entry("formations/frozen/glacial-overhang-3", "fa68510db7ab437e"),
            Map.entry("formations/frozen/glacial-shard-fan", "e01f0e007cb2cdbd"),
            Map.entry("formations/frozen/glacial-shard-fan-2", "71559cf0f7aa8192"),
            Map.entry("formations/frozen/glacial-spiral", "ee0eba9400f7531b"),
            Map.entry("formations/frozen/glacial-spiral-2", "042681969a2f42b8"),
            Map.entry("formations/frozen/glacial-spiral-3", "afadec6271ac652b"),
            Map.entry("formations/magnetics/glacial-leaning-lance", "bb21fbb337bc4c3c"),
            Map.entry("formations/magnetics/glacial-leaning-lance-2", "6050700a6e2cf702"),
            Map.entry("formations/magnetics/glacial-needle", "6f96f650f3287013"),
            Map.entry("formations/magnetics/glacial-needle-2", "74cf79674ecd59b2"),
            Map.entry("formations/magnetics/glacial-shard-fan", "ce0699c25f897800"),
            Map.entry("formations/magnetics/glacial-shard-fan-2", "7a92fcd891b58b67"),
            Map.entry("fungi/mushroom/brown-table", "43559217abe6f50e"),
            Map.entry("fungi/mushroom/red-cap", "d530e0ee8bf2c45a"),
            Map.entry("fungi/mushroom/spindle-toadstool", "f9325da029df1a25"),
            Map.entry("ruins/estranged/pallid-marker", "ecc17127ff23b82d"),
            Map.entry("ruins/estranged/ruined-pillar", "f4ad226fb50365d5"),
            Map.entry("ruins/estranged/ruined-wall", "e892dc089247bab2"),
            Map.entry("trees/estranged/baobab-giant", "2d2f6ac1d0f3a12e"),
            Map.entry("trees/estranged/baobab-young", "040e6f0edbe0454c"),
            Map.entry("trees/estranged/birchglass", "18ddbb47c30ecf39"),
            Map.entry("trees/estranged/birchglass-wide", "bc76c876729c039b"),
            Map.entry("trees/estranged/bloomspire", "c7abe73e9aacc9c7"),
            Map.entry("trees/estranged/bonsai-blossom", "b1086398efa3d47d"),
            Map.entry("trees/estranged/bonsai-green", "d65fe4f34e9db801"),
            Map.entry("trees/estranged/candlewood", "747ac67821c9b669"),
            Map.entry("trees/estranged/emberbark", "fcf9b3f2ddafd4e2"),
            Map.entry("trees/estranged/emberbark-tall", "d2d9c1b1e5745fb7"),
            Map.entry("trees/estranged/lollipop-pink", "a3f8915b37fa0cea"),
            Map.entry("trees/estranged/lollipop-teal", "9ccc63d709c941eb"),
            Map.entry("trees/estranged/mistveil-spire", "54a98ed949007920"),
            Map.entry("trees/estranged/mistveil-young", "5b997ebf801fd7e9"),
            Map.entry("trees/estranged/pallid-spindle", "7e61438ae7d9d0b3"),
            Map.entry("trees/estranged/pallid-willow", "7671510319a28455"),
            Map.entry("trees/estranged/skyreach-pine", "56eeb832a996f9f5"),
            Map.entry("trees/estranged/snarlwood", "4949c8fb9b8b3d1b"),
            Map.entry("trees/estranged/spindle-golden", "8c59979a5ebeb224"),
            Map.entry("trees/estranged/spindle-whorl", "2b91fe5d0c10db6b"),
            Map.entry("trees/estranged/stiltroot", "8986e4cae3578abf"),
            Map.entry("trees/estranged/taxodium", "5f61ee1495fc3ff5"),
            Map.entry("trees/estranged/weeping-willow", "ceda6cba27c98ccf")
    );

    private ProceduralFixture fixture;

    @Before
    public void bindGameLikePlatform() throws URISyntaxException {
        fixture = ProceduralFixture.bind();
    }

    @After
    public void unbindPlatform() {
        ProceduralFixture.unbind();
    }

    @Test
    public void everyProceduralObjectOfTheOverworldPackBakesVariantsWithBlocks() throws IOException {
        List<String> empty = new ArrayList<>();
        for (String path : fixture.definitionPaths()) {
            List<IrisObject> variants = fixture.resolved(path).variants();
            if (variants.isEmpty() || variants.stream().anyMatch(variant -> variant.getBlocks().isEmpty())) {
                empty.add(path);
            }
        }
        assertEquals(List.of(), empty);
    }

    @Test
    public void everyProceduralObjectOfTheOverworldPackBakesAsWhenTheDigestsWereTaken() throws Exception {
        Map<String, String> actual = new TreeMap<>();
        for (String path : fixture.definitionPaths()) {
            actual.put(path, digest(fixture.resolved(path)));
        }
        assertEquals(new TreeMap<>(GAME_LIKE), actual);
    }

    @Test
    public void theBakedObjectsHoldTheShapesTheDigestsWereTakenOf() throws Exception {
        for (String path : fixture.definitionPaths()) {
            ProceduralVariants variants = fixture.resolved(path);
            for (IrisObject baked : variants.variants()) {
                int index = Integer.parseInt(baked.getLoadKey().substring(baked.getLoadKey().indexOf('#') + 1));
                ProceduralShape shape = shapeOf(variants, index);
                assertNotNull(path, shape);
                assertEquals(path, variants.variantKey(index), baked.getLoadKey());
                assertEquals(path, shape.width(), baked.getW());
                assertEquals(path, shape.height(), baked.getH());
                assertEquals(path, shape.depth(), baked.getD());
                assertEquals(path, shape.blocks().size(), baked.getBlocks().size());
                for (ProceduralBlock block : shape.blocks()) {
                    assertEquals(path, block.state(), baked.getBlocks().get(new IrisBlockVector(block.x(), block.y(), block.z())));
                }
            }
        }
    }

    private static ProceduralShape shapeOf(ProceduralVariants variants, int index) {
        return variants.generate(index, variants.variantRng(index), variants.newBuilder(), BakeProfile.NONE);
    }

    private static String digest(ProceduralVariants variants) throws NoSuchAlgorithmException {
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        for (IrisObject variant : variants.variants()) {
            update(digest, variant.getLoadKey() + ' ' + variant.getW() + 'x' + variant.getH() + 'x' + variant.getD() + ':');
            variant.getBlocks().forEach((position, state) ->
                    update(digest, position.getBlockX() + "," + position.getBlockY() + "," + position.getBlockZ() + "=" + state.key() + ";"));
            update(digest, "\n");
        }
        return HexFormat.of().formatHex(digest.digest(), 0, 8);
    }

    private static void update(MessageDigest digest, String text) {
        digest.update(text.getBytes(StandardCharsets.UTF_8));
    }
}

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

package art.arcane.iris.engine.procedural;

import art.arcane.iris.core.loader.IrisData;
import art.arcane.iris.core.loader.ResourceLoader;
import art.arcane.iris.core.modules.IrisPackResources;
import art.arcane.iris.core.modules.JsonFormsCodecs;
import art.arcane.iris.engine.UsableBlockPlatform;
import art.arcane.iris.engine.procedural.part.ProceduralObject;
import art.arcane.iris.engine.procedural.part.ResolvedProceduralObject;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.volmit.object.ObjectBlock;
import com.volmit.object.PlaceableObject;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.io.IOException;
import java.net.URISyntaxException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.stream.Stream;

import static org.junit.Assert.assertEquals;
import static org.mockito.Mockito.CALLS_REAL_METHODS;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.withSettings;

/**
 * Every procedural object of the overworld pack (its {@code procedural/} folder, with the palette snippets it names)
 * bakes, and bakes the same variants as when these digests were taken: same names, sizes and blocks. A digest that
 * changes means world generation places different objects; retake it only for a change meant to do that.
 */
public class ProceduralObjectsGoldenTest {
    private static final String FIXTURE = "/procedural-parts";
    private static final String DEFINITIONS = "procedural";
    private static final Map<String, String> GAME_LIKE = Map.ofEntries(
            Map.entry("coral/ocean/reef-brain", "d761fa1a8dc85b20"),
            Map.entry("coral/ocean/reef-branch", "f03b09fb586b648c"),
            Map.entry("coral/ocean/reef-fan", "76597dc8f6cf96cf"),
            Map.entry("coral/temperate/brain-coral", "7f61fb3597b4f07a"),
            Map.entry("coral/temperate/branch-coral", "3f99222e95ce6964"),
            Map.entry("crystals/estranged/amethyst-cluster", "469e133d1564a125"),
            Map.entry("crystals/estranged/frostspar", "94adf86f507c9775"),
            Map.entry("crystals/frozen/glacial-frost-bloom", "269ba23560175716"),
            Map.entry("crystals/frozen/glacial-frost-sprigs", "2780c39be46ac225"),
            Map.entry("formations/estranged/boulder", "5075e6b19323bdc7"),
            Map.entry("formations/estranged/obelisk-spire", "52973501f324428e"),
            Map.entry("formations/estranged/ossuary-spire", "56cde4be5e71d537"),
            Map.entry("formations/frozen/glacial-arch", "a4eaa2ac66dbe498"),
            Map.entry("formations/frozen/glacial-arch-2", "d90b2acd92233407"),
            Map.entry("formations/frozen/glacial-cave-arch", "5eb7bbf9548c0e6d"),
            Map.entry("formations/frozen/glacial-cave-fissure", "9ff1db3cf9dfe4a0"),
            Map.entry("formations/frozen/glacial-cave-hanging-needle", "309896032f06a611"),
            Map.entry("formations/frozen/glacial-cave-iceberg", "fa98c8227ede70d8"),
            Map.entry("formations/frozen/glacial-cave-needle", "8b54927c81e3035d"),
            Map.entry("formations/frozen/glacial-cave-overhang", "f99c488c478d80b3"),
            Map.entry("formations/frozen/glacial-cave-shard-fan", "da094b44cec354d5"),
            Map.entry("formations/frozen/glacial-cave-spiral", "95dec28606d59bd1"),
            Map.entry("formations/frozen/glacial-crooked-spire", "2a65f5c2c165519e"),
            Map.entry("formations/frozen/glacial-crooked-spire-2", "29db29d789a47ad8"),
            Map.entry("formations/frozen/glacial-drift", "97d5ab9a9cb47a52"),
            Map.entry("formations/frozen/glacial-fissure", "d0c032412c25548f"),
            Map.entry("formations/frozen/glacial-fissure-2", "33d4d237f96312df"),
            Map.entry("formations/frozen/glacial-iceberg", "c9cc7f30aa3cca4b"),
            Map.entry("formations/frozen/glacial-iceberg-2", "a7f581cbf879e6c6"),
            Map.entry("formations/frozen/glacial-overhang", "1e447dd99fb91822"),
            Map.entry("formations/frozen/glacial-overhang-2", "7579d953b706455c"),
            Map.entry("formations/frozen/glacial-overhang-3", "a509da1fc4f2e7d4"),
            Map.entry("formations/frozen/glacial-shard-fan", "52b40ab0d1ec2e80"),
            Map.entry("formations/frozen/glacial-shard-fan-2", "2a12859998fbf6fd"),
            Map.entry("formations/frozen/glacial-spiral", "4736b907b62b7dda"),
            Map.entry("formations/frozen/glacial-spiral-2", "3b1fdb376a524794"),
            Map.entry("formations/frozen/glacial-spiral-3", "6a326b0dcfb0c32f"),
            Map.entry("formations/magnetics/glacial-leaning-lance", "e903d16fd0791b7b"),
            Map.entry("formations/magnetics/glacial-leaning-lance-2", "f95293be79600c5e"),
            Map.entry("formations/magnetics/glacial-needle", "4379c14e96de7c23"),
            Map.entry("formations/magnetics/glacial-needle-2", "15be4608b47322c2"),
            Map.entry("formations/magnetics/glacial-shard-fan", "2cfd006aefe4606b"),
            Map.entry("formations/magnetics/glacial-shard-fan-2", "39b6a3804fa863c2"),
            Map.entry("fungi/mushroom/brown-table", "43559217abe6f50e"),
            Map.entry("fungi/mushroom/red-cap", "d530e0ee8bf2c45a"),
            Map.entry("fungi/mushroom/spindle-toadstool", "f9325da029df1a25"),
            Map.entry("ruins/estranged/pallid-marker", "04b45fca766324f3"),
            Map.entry("ruins/estranged/ruined-pillar", "0fc9d051ff475eeb"),
            Map.entry("ruins/estranged/ruined-wall", "74460a5094fa879c"),
            Map.entry("trees/estranged/baobab-giant", "62aa55828a21737c"),
            Map.entry("trees/estranged/baobab-young", "fa2b9c1f7d2dfd96"),
            Map.entry("trees/estranged/birchglass", "26b6746daa1553e3"),
            Map.entry("trees/estranged/birchglass-wide", "bc76c876729c039b"),
            Map.entry("trees/estranged/bloomspire", "19ef665ce2a46edb"),
            Map.entry("trees/estranged/bonsai-blossom", "77392c7779ec932a"),
            Map.entry("trees/estranged/bonsai-green", "59de9a369d465962"),
            Map.entry("trees/estranged/candlewood", "1f6a191071d0fc61"),
            Map.entry("trees/estranged/emberbark", "6eb2c53921956aaa"),
            Map.entry("trees/estranged/emberbark-tall", "5e53fa03b5d335a1"),
            Map.entry("trees/estranged/lollipop-pink", "62637edb78f21acb"),
            Map.entry("trees/estranged/lollipop-teal", "9231a9a59089ea47"),
            Map.entry("trees/estranged/mistveil-spire", "a7def2e1fd04f973"),
            Map.entry("trees/estranged/mistveil-young", "5b997ebf801fd7e9"),
            Map.entry("trees/estranged/pallid-spindle", "a17288d361507cd9"),
            Map.entry("trees/estranged/pallid-willow", "ee12c867430772ce"),
            Map.entry("trees/estranged/skyreach-pine", "56eeb832a996f9f5"),
            Map.entry("trees/estranged/snarlwood", "82dc7264dc75363d"),
            Map.entry("trees/estranged/spindle-golden", "8c59979a5ebeb224"),
            Map.entry("trees/estranged/spindle-whorl", "2b91fe5d0c10db6b"),
            Map.entry("trees/estranged/stiltroot", "9075cfbd22465aed"),
            Map.entry("trees/estranged/taxodium", "f36caedfa443564c"),
            Map.entry("trees/estranged/weeping-willow", "e1b3f8aa787a7504")
    );

    private Path pack;
    private IrisPackResources resources;
    private Gson gson;

    @Before
    public void bindGameLikePlatform() throws URISyntaxException {
        UsableBlockPlatform.bind(new GameLikeBlocks().registries());
        pack = Path.of(ProceduralObjectsGoldenTest.class.getResource(FIXTURE).toURI());
        IrisData data = mock(IrisData.class, withSettings().stubOnly().defaultAnswer(CALLS_REAL_METHODS));
        doReturn(pack.toFile()).when(data).getDataFolder();
        doReturn(mock(ResourceLoader.class)).when(data).getBlockLoader();
        doReturn(null).when(data).getEngine();
        resources = new IrisPackResources(data);
        doReturn(resources).when(data).getPackResources();
        gson = new GsonBuilder().registerTypeAdapterFactory(data).registerTypeAdapterFactory(new JsonFormsCodecs()).create();
    }

    @After
    public void unbindPlatform() {
        UsableBlockPlatform.unbind();
    }

    @Test
    public void everyProceduralObjectOfTheOverworldPackBakesVariantsWithBlocks() throws IOException {
        List<String> empty = new ArrayList<>();
        for (String path : definitionPaths()) {
            List<PlaceableObject> variants = variantsOf(path).variants();
            if (variants.isEmpty() || variants.stream().anyMatch(variant -> variant.geometry().blocks().isEmpty())) {
                empty.add(path);
            }
        }
        assertEquals(List.of(), empty);
    }

    @Test
    public void everyProceduralObjectOfTheOverworldPackBakesAsWhenTheDigestsWereTaken() throws Exception {
        Map<String, String> actual = new TreeMap<>();
        for (String path : definitionPaths()) {
            actual.put(path, digest(variantsOf(path).variants()));
        }
        assertEquals(new TreeMap<>(GAME_LIKE), actual);
    }

    private ProceduralVariants variantsOf(String path) throws IOException {
        String json = Files.readString(pack.resolve(DEFINITIONS).resolve(path + ".json"));
        return new ResolvedProceduralObject(path, gson.fromJson(json, ProceduralObject.class), resources);
    }

    private List<String> definitionPaths() throws IOException {
        Path folder = pack.resolve(DEFINITIONS);
        try (Stream<Path> files = Files.walk(folder)) {
            return files.filter(file -> file.toString().endsWith(".json"))
                    .map(file -> folder.relativize(file).toString().replace('\\', '/'))
                    .map(file -> file.substring(0, file.length() - ".json".length()))
                    .sorted()
                    .toList();
        }
    }

    private static String digest(List<PlaceableObject> variants) throws NoSuchAlgorithmException {
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        for (PlaceableObject variant : variants) {
            update(digest, variant.name() + ' ' + variant.width() + 'x' + variant.height() + 'x' + variant.depth() + ':');
            for (ObjectBlock block : variant.geometry().blocks()) {
                update(digest, block.position().x() + "," + block.position().y() + "," + block.position().z() + "=" + block.state().key() + ";");
            }
            update(digest, "\n");
        }
        return HexFormat.of().formatHex(digest.digest(), 0, 8);
    }

    private static void update(MessageDigest digest, String text) {
        digest.update(text.getBytes(StandardCharsets.UTF_8));
    }
}

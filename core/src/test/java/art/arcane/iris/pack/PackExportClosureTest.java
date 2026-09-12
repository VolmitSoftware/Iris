package art.arcane.iris.pack;

import art.arcane.iris.pack.loading.IrisData;
import art.arcane.iris.generation.biome.IrisBiome;
import art.arcane.iris.generation.terrain.IrisDimension;
import art.arcane.iris.generation.terrain.IrisDimensionStack;
import art.arcane.iris.structure.object.IrisObjectMarker;
import art.arcane.iris.structure.object.IrisObjectPlacement;
import art.arcane.iris.structure.object.IrisStaticObject;
import art.arcane.volmlib.util.collection.KList;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class PackExportClosureTest {
    @Rule
    public final TemporaryFolder temporary = new TemporaryFolder();

    @Test
    public void exportedSnippetsLoadTerrainAndNestedStylesFromTheTargetPack() throws Exception {
        Path source = temporary.newFolder("source").toPath();
        Path target = temporary.newFolder("target").toPath();
        Path terrain = source.resolve("snippet/terrain-3d/cliffs/main.json");
        Path density = source.resolve("snippet/style/cliffs/density.json");
        Path fracture = source.resolve("snippet/style/cliffs/fracture.json");
        Path decorator = source.resolve("snippet/decorator/ledge.json");
        for (Path file : List.of(terrain, density, fracture, decorator)) {
            Files.createDirectories(file.getParent());
        }
        Files.writeString(terrain, "{\"amplitude\":57,\"densityStyle\":\"snippet/style/cliffs/density\"}");
        Files.writeString(density, "{\"style\":\"SIMPLEX\",\"zoom\":1.5,\"fracture\":\"snippet/style/cliffs/fracture\"}");
        Files.writeString(fracture, "{\"style\":\"PERLIN\",\"zoom\":2}");
        Files.writeString(decorator, "{\"chance\":0.2}");
        Files.writeString(source.resolve("snippet/notes.txt"), "not pack data");

        String hash = PackExportClosure.copySnippets(source.toFile(), target.toFile());

        assertFalse(hash.isEmpty());
        assertEquals(hash, PackExportClosure.copySnippets(source.toFile(), target.toFile()));
        assertTrue(Files.isRegularFile(target.resolve("snippet/decorator/ledge.json")));
        assertFalse(Files.exists(target.resolve("snippet/notes.txt")));
        IrisData data = IrisData.openDatapackCompiler(target.toFile());
        try {
            IrisBiome biome = data.getGson().fromJson(
                    "{\"terrain3D\":\"snippet/terrain-3d/cliffs/main\"}", IrisBiome.class);
            assertEquals(57D, biome.getTerrain3D().getAmplitude(), 0D);
            assertEquals(1.5D, biome.getTerrain3D().getDensityStyle().getZoom(), 0D);
            assertEquals(2D, biome.getTerrain3D().getDensityStyle().getFracture().getZoom(), 0D);
        } finally {
            data.close();
        }
    }

    @Test
    public void packsWithoutSnippetsDoNotCreateAnExportFolder() throws Exception {
        Path source = temporary.newFolder("without-snippets").toPath();
        Path target = temporary.getRoot().toPath().resolve("absent-export");

        assertEquals("", PackExportClosure.copySnippets(source.toFile(), target.toFile()));
        assertFalse(Files.exists(target));
    }

    private static IrisObjectPlacement placement(String objectKey, String... markerKeys) {
        IrisObjectPlacement placement = new IrisObjectPlacement();
        placement.setPlace(new KList<>(objectKey));
        KList<IrisObjectMarker> markers = new KList<>();
        for (String markerKey : markerKeys) {
            IrisObjectMarker marker = new IrisObjectMarker();
            marker.setMarker(markerKey);
            markers.add(marker);
        }
        placement.setMarkers(markers);
        return placement;
    }

    @Test
    public void collectsMarkerAndObjectKeysFromPlacements() {
        List<IrisObjectPlacement> placements = List.of(
                placement("houses/hut", "spawn-point", "loot-marker"),
                placement("trees/oak"),
                placement("houses/hut", "spawn-point"));

        assertEquals(List.of("loot-marker", "spawn-point"),
                PackExportClosure.collectMarkerKeys(placements).stream().sorted().toList());
        assertEquals(List.of("houses/hut", "trees/oak"),
                PackExportClosure.collectObjectKeys(placements).stream().sorted().toList());
    }

    @Test
    public void toleratesNullPlacementsAndBlankKeys() {
        KList<IrisObjectPlacement> placements = new KList<>();
        placements.add(placement("", ""));
        placements.add((IrisObjectPlacement) null);

        assertTrue(PackExportClosure.collectMarkerKeys(placements).isEmpty());
        assertTrue(PackExportClosure.collectObjectKeys(placements).isEmpty());
    }

    @Test
    public void collectsStaticObjectKeysWithoutDuplicates() {
        KList<IrisStaticObject> placements = new KList<>();
        placements.add(new IrisStaticObject().setObject("landmarks/tower"));
        placements.add(new IrisStaticObject().setObject("landmarks/tower"));
        placements.add(new IrisStaticObject().setObject("landmarks/bridge"));
        placements.add(new IrisStaticObject().setObject(""));
        placements.add((IrisStaticObject) null);

        assertEquals(List.of("landmarks/bridge", "landmarks/tower"),
                PackExportClosure.collectStaticObjectKeys(placements).stream().sorted().toList());
    }

    @Test
    public void collectsRootAndStackedDimensionKeys() {
        IrisDimension dimension = new IrisDimension();
        dimension.setLoadKey("main");
        dimension.setDimensionStack(new IrisDimensionStack()
                .setDimensions(new KList<>("sky", "middle", "main", "sky")));

        assertEquals(List.of("main", "middle", "sky"),
                PackExportClosure.collectDimensionKeys(dimension).stream().sorted().toList());
    }

    @Test
    public void collectsTransitiveStackDimensionsWithoutLooping() {
        IrisDimension main = dimension("main", "sky", "main");
        IrisDimension sky = dimension("sky", "cloud", "sky");
        IrisDimension cloud = dimension("cloud", "main", "cloud");
        Map<String, IrisDimension> dimensions = Map.of(
                "main", main,
                "sky", sky,
                "cloud", cloud);

        assertEquals(List.of("cloud", "main", "sky"),
                PackExportClosure.collectDimensionKeys(main, dimensions::get).stream().sorted().toList());
    }

    private static IrisDimension dimension(String key, String... stackKeys) {
        IrisDimension dimension = new IrisDimension();
        dimension.setLoadKey(key);
        dimension.setDimensionStack(new IrisDimensionStack().setDimensions(new KList<>(stackKeys)));
        return dimension;
    }

    /**
     * Source guard: both packagers must export the ambient-spawning graph. Spawner and marker
     * folders were silently omitted from exports, leaving dangling entitySpawners references.
     */
}

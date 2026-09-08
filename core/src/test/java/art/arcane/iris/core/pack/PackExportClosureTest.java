package art.arcane.iris.core.pack;

import art.arcane.iris.core.loader.IrisData;
import art.arcane.iris.engine.object.IrisBiome;
import art.arcane.iris.engine.object.IrisDimension;
import art.arcane.iris.engine.object.IrisDimensionStack;
import art.arcane.iris.engine.object.IrisObjectMarker;
import art.arcane.iris.engine.object.IrisObjectPlacement;
import art.arcane.iris.engine.object.IrisStaticObject;
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
    @Test
    public void bothPackagersExportSpawnersMarkersAndTheFullEntityGraph() throws Exception {
        String bukkit = Files.readString(Path.of(
                "src/main/java/art/arcane/iris/core/project/IrisPackageCompiler.java")).replace("\r\n", "\n");
        assertTrue("Bukkit packager must write spawners/", bukkit.contains("\"spawners/\""));
        assertTrue("Bukkit packager must write markers/", bukkit.contains("\"markers/\""));
        assertTrue("Bukkit packager must export region objects alongside biome objects",
                bukkit.contains("regions.forEach((r) -> allPlacements.addAll(r.getObjects()))"));
        assertTrue("Bukkit packager must export static objects",
                bukkit.contains("exportedDimension.getStaticObjects()"));
        assertTrue("Bukkit obfuscation must rewrite static object references",
                bukkit.contains("placement.setObject(renameObjects.get(placement.getObject()))"));
        assertTrue("Bukkit packager must export entity loot tables", bukkit.contains("getLoot().getTables()"));
        assertTrue("Bukkit packager must include dimension-stack resources",
                bukkit.contains("PackExportClosure.collectDimensionKeys(dimension)"));
        assertTrue("Bukkit packager must export expression-backed stack blend styles",
                bukkit.contains("dm.getExpressionLoader().getPossibleKeys()")
                        && bukkit.contains("\"expressions/\""));
        int bukkitValidation = bukkit.indexOf("PackValidator.validateForPackaging(project.getPath())");
        assertTrue("Bukkit packager must validate before opening or mutating package state",
                bukkitValidation >= 0
                        && bukkitValidation < bukkit.indexOf("IrisData.openRuntime(project.getPath())")
                        && bukkitValidation < bukkit.indexOf("IO.delete(folder)"));

        String modded = Files.readString(Path.of(
                "../adapters/modded-common/src/main/java/art/arcane/iris/modded/command/ModdedStudioCommands.java")).replace("\r\n", "\n");
        assertTrue("modded packager must write spawners/", modded.contains("\"spawners\""));
        assertTrue("modded packager must write markers/", modded.contains("\"markers\""));
        assertTrue("modded packager must include initial spawns", modded.contains("getInitialSpawns"));
        assertTrue("modded packager must export region objects", modded.contains("region.getObjects()"));
        assertTrue("modded packager must export static objects",
                modded.contains("PackExportClosure.collectStaticObjectKeys(exportedDimension.getStaticObjects())"));
        assertTrue("modded packager must include dimension-stack resources",
                modded.contains("PackExportClosure.collectDimensionKeys(dimension)"));
        assertTrue("modded packager must export expression-backed stack blend styles",
                modded.contains("dm.getExpressionLoader().getPossibleKeys()")
                        && modded.contains("\"expressions\""));
        assertTrue("modded packager must retain all snippet types and nested references",
                modded.contains("PackExportClosure.copySnippets(packFolder, folder)"));
        int moddedValidation = modded.indexOf("PackValidator.validateForPackaging(packFolder)");
        assertTrue("modded packager must validate before mutating package state",
                moddedValidation >= 0 && moddedValidation < modded.indexOf("IO.delete(folder)"));
    }
}

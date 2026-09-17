package art.arcane.iris.core.pack;

import art.arcane.iris.engine.object.marker.IrisObjectMarker;
import art.arcane.iris.engine.object.placement.IrisObjectPlacement;
import art.arcane.volmlib.util.collection.KList;
import org.junit.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class PackExportClosureTest {
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
        assertTrue("Bukkit packager must export entity loot tables", bukkit.contains("getLoot().getTables()"));
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
        int moddedValidation = modded.indexOf("PackValidator.validateForPackaging(packFolder)");
        assertTrue("modded packager must validate before mutating package state",
                moddedValidation >= 0 && moddedValidation < modded.indexOf("IO.delete(folder)"));
    }
}

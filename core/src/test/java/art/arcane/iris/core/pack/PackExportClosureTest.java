package art.arcane.iris.core.pack;

import art.arcane.iris.engine.object.placement.IrisObjectPlacement;
import art.arcane.volmlib.util.collection.KList;
import org.junit.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class PackExportClosureTest {
    private static IrisObjectPlacement placement(String objectKey) {
        IrisObjectPlacement placement = new IrisObjectPlacement();
        placement.setPlace(new KList<>(objectKey));
        return placement;
    }

    @Test
    public void collectsObjectKeysFromPlacements() {
        List<IrisObjectPlacement> placements = List.of(
                placement("houses/hut"),
                placement("trees/oak"),
                placement("houses/hut"));

        assertEquals(List.of("houses/hut", "trees/oak"),
                PackExportClosure.collectObjectKeys(placements).stream().sorted().toList());
    }

    @Test
    public void toleratesNullPlacementsAndBlankKeys() {
        KList<IrisObjectPlacement> placements = new KList<>();
        placements.add(placement(""));
        placements.add((IrisObjectPlacement) null);

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
        assertTrue("Bukkit packager must export region objects alongside biome objects",
                bukkit.contains("regions.forEach((r) -> allPlacements.addAll(r.getObjects()))"));
        assertTrue("Bukkit packager must export the spawn and loot resources the settings reference",
                bukkit.contains("moduleResources.writeTo(folder, minify)"));
        assertTrue("Bukkit packager must export the placements' references", bukkit.contains("moduleResources.follow(allPlacements)"));
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

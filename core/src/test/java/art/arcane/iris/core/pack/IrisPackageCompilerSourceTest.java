package art.arcane.iris.core.pack;

import org.junit.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.Assert.assertTrue;

public class IrisPackageCompilerSourceTest {
    /**
     * Source guard: the packager must export the ambient-spawning graph. Spawner and marker
     * folders were silently omitted from exports, leaving dangling entitySpawners references.
     */
    @Test
    public void thePackagerExportsSpawnersMarkersAndTheFullEntityGraph() throws Exception {
        String bukkit = Files.readString(Path.of(
                "src/main/java/art/arcane/iris/core/project/IrisPackageCompiler.java")).replace("\r\n", "\n");
        assertTrue("Bukkit packager must export region objects alongside biome objects",
                bukkit.contains("regions.forEach((r) -> allPlacements.addAll(ObjectPlacements.all(r)))"));
        assertTrue("Bukkit packager must export the spawn and loot resources the settings reference",
                bukkit.contains("moduleResources.writeTo(folder, minify)"));
        assertTrue("Bukkit packager must export the placements' references", bukkit.contains("moduleResources.follow(allPlacements)"));
        int bukkitValidation = bukkit.indexOf("PackValidator.validateForPackaging(project.getPath())");
        assertTrue("Bukkit packager must validate before opening or mutating package state",
                bukkitValidation >= 0
                        && bukkitValidation < bukkit.indexOf("IrisData.openRuntime(project.getPath())")
                        && bukkitValidation < bukkit.indexOf("IO.delete(folder)"));
    }
}

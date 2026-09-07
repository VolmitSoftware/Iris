package art.arcane.iris;

import org.bukkit.plugin.PluginDescriptionFile;
import org.bukkit.plugin.PluginLoadOrder;
import org.bukkit.permissions.Permission;
import org.bukkit.permissions.PermissionDefault;
import org.junit.Test;

import java.io.InputStream;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

public class PaperPluginMetadataTest {
    private static final List<String> OPTIONAL_PLUGIN_IDS = List.of(
            "PlaceholderAPI",
            "CraftEngine",
            "Nexo",
            "Oraxen",
            "ItemsAdder",
            "SCore",
            "ExecutableItems",
            "MythicLib",
            "MMOItems",
            "eco",
            "EcoItems",
            "MythicMobs",
            "MythicCrucible",
            "KGenerators",
            "Multiverse-Core",
            "WorldEdit"
    );
    private static final List<String> JOINED_PLUGIN_IDS = OPTIONAL_PLUGIN_IDS.stream()
            .filter(pluginId -> !"ExecutableItems".equals(pluginId) && !"Multiverse-Core".equals(pluginId))
            .toList();
    private static final List<String> BUKKIT_SOFT_DEPEND_IDS = OPTIONAL_PLUGIN_IDS.stream()
            .filter(pluginId -> !"Multiverse-Core".equals(pluginId))
            .toList();

    @Test
    public void paperMetadataDeclaresBootstrapAndFoliaSupport() throws Exception {
        String metadata;
        try (InputStream stream = PaperPluginMetadataTest.class.getResourceAsStream("/paper-plugin.yml")) {
            assertNotNull(stream);
            metadata = new String(stream.readAllBytes(), StandardCharsets.UTF_8);
        }

        assertTrue(metadata.contains("bootstrapper: " + IrisBootstrap.class.getName()));
        assertTrue(metadata.contains("loader: " + IrisPluginLoader.class.getName()));
        assertTrue(metadata.contains("folia-supported: true"));
        assertTrue(metadata.contains("load: STARTUP"));
        assertFalse(metadata.contains("commands:"));
        assertTrue(metadata.contains("permissions:\n"));
        assertTrue(metadata.contains("  iris.debugdump:\n"
                + "    description: Allows creating and optionally uploading diagnostic reports.\n"
                + "    default: op"));
        assertTrue(metadata.contains("  iris.language.self:\n"
                + "    description: Allows choosing your own language for this plugin.\n"
                + "    default: true"));
        assertTrue(metadata.contains("  iris.all:\n"
                + "    description: Allows use of the full /iris command tree (worlds, studio, pregen, packs, developer tools).\n"
                + "    default: op\n"
                + "  iris.treefeller:\n"
                + "    description: Allows survival players to fell Iris-managed trees with an axe.\n"
                + "    default: op"));
        for (String pluginId : JOINED_PLUGIN_IDS) {
            assertTrue(metadata.contains(optionalDependencyBlock(pluginId, "BEFORE", true)));
        }
        assertTrue(metadata.contains(optionalDependencyBlock("ExecutableItems", "BEFORE", false)));
        assertTrue(metadata.contains(optionalDependencyBlock("Multiverse-Core", "AFTER", true)));
    }

    @Test
    public void bukkitMetadataRetainsStartupCommandAndAliases() throws Exception {
        PluginDescriptionFile metadata;
        try (InputStream stream = PaperPluginMetadataTest.class.getResourceAsStream("/plugin.yml")) {
            assertNotNull(stream);
            metadata = new PluginDescriptionFile(stream);
        }

        assertEquals(Iris.class.getName(), metadata.getMain());
        assertEquals(PluginLoadOrder.STARTUP, metadata.getLoad());
        Map<String, Map<String, Object>> commands = metadata.getCommands();
        assertTrue(commands.containsKey("iris"));
        assertEquals(List.of("ir", "irs"), commands.get("iris").get("aliases"));
        assertEquals(BUKKIT_SOFT_DEPEND_IDS, metadata.getSoftDepend());
        assertEquals(List.of("Multiverse-Core"), metadata.getLoadBeforePlugins());
        Permission all = metadata.getPermissions().stream()
                .filter(permission -> "iris.all".equals(permission.getName()))
                .findFirst()
                .orElse(null);
        assertNotNull(all);
        assertEquals(
                "Allows use of the full /iris command tree (worlds, studio, pregen, packs, developer tools).",
                all.getDescription());
        assertEquals(PermissionDefault.OP, all.getDefault());
        Permission treeFeller = metadata.getPermissions().stream()
                .filter(permission -> "iris.treefeller".equals(permission.getName()))
                .findFirst()
                .orElse(null);
        assertNotNull(treeFeller);
        assertEquals("Allows survival players to fell Iris-managed trees with an axe.", treeFeller.getDescription());
        assertEquals(PermissionDefault.OP, treeFeller.getDefault());
    }

    @Test
    public void processedResourcesContainBothMetadataFormats() throws Exception {
        URL paperMetadata = PaperPluginMetadataTest.class.getResource("/paper-plugin.yml");
        assertNotNull(paperMetadata);
        assertEquals("file", paperMetadata.getProtocol());
        Path bukkitMetadata = Path.of(paperMetadata.toURI()).resolveSibling("plugin.yml");
        assertTrue(Files.isRegularFile(bukkitMetadata));
        assertFalse(Files.readString(bukkitMetadata, StandardCharsets.UTF_8).contains("${"));
    }

    private static String optionalDependencyBlock(String pluginId, String loadOrder, boolean joinClasspath) {
        return "    " + pluginId + ":\n"
                + "      load: " + loadOrder + "\n"
                + "      required: false\n"
                + "      join-classpath: " + joinClasspath + "\n";
    }
}

package art.arcane.iris.pack.loading;

import art.arcane.iris.configuration.IrisSettings;
import art.arcane.iris.generation.runtime.PreservationRegistry;
import art.arcane.iris.generation.noise.IrisGenerator;
import art.arcane.iris.generation.block.B;
import art.arcane.iris.spi.PlatformBlockState;
import art.arcane.iris.pack.validation.ContentGate;
import art.arcane.iris.spi.IrisServices;
import art.arcane.iris.testsupport.PlatformLeakGuard;
import org.junit.After;
import org.junit.Before;
import org.junit.ClassRule;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.mockito.MockedStatic;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

public class IrisDataAuthoringCacheTest {
    @ClassRule
    public static final PlatformLeakGuard PLATFORM_GUARD = PlatformLeakGuard.clean();

    @Rule
    public final TemporaryFolder temporary = new TemporaryFolder();

    private IrisData source;
    private IrisData epoch;
    private IrisSettings previousSettings;

    @Before
    public void registerServices() {
        previousSettings = IrisSettings.settings;
        IrisSettings.settings = new IrisSettings();
        IrisServices.register(PreservationRegistry.class, mock(PreservationRegistry.class));
    }

    @After
    public void closeData() {
        if (source != null) {
            source.close();
        }
        if (epoch != null) {
            epoch.close();
        }
        IrisServices.remove(PreservationRegistry.class);
        IrisSettings.settings = previousSettings;
    }

    @Test
    public void externalEditsRefreshOnlyTheEditableSourceAndKeyLists() throws Exception {
        Path sourceRoot = temporary.newFolder("source").toPath();
        Path epochRoot = temporary.newFolder("epoch").toPath();
        Files.createDirectories(sourceRoot.resolve("generators"));
        Files.createDirectories(epochRoot.resolve("generators"));
        Files.writeString(sourceRoot.resolve("generators/sample.json"), "{\"zoom\":2.0}");
        Files.writeString(epochRoot.resolve("generators/sample.json"), "{\"zoom\":3.0}");
        source = IrisData.get(new File(sourceRoot.toFile(), "."));
        epoch = IrisData.openRuntime(epochRoot.toFile());
        assertEquals(2.0, source.getGeneratorLoader().load("sample").getZoom(), 0.0);
        IrisGenerator retained = epoch.getGeneratorLoader().load("sample");
        assertFalse(Arrays.asList(source.getGeneratorLoader().getPossibleKeys()).contains("added"));
        Files.writeString(sourceRoot.resolve("generators/sample.json"), "{\"zoom\":4.0}");
        Files.writeString(sourceRoot.resolve("generators/added.json"), "{\"zoom\":5.0}");

        IrisData.invalidateLoadedAuthoringResources(sourceRoot.toFile());

        assertEquals(4.0, source.getGeneratorLoader().load("sample").getZoom(), 0.0);
        assertTrue(Arrays.asList(source.getGeneratorLoader().getPossibleKeys()).contains("added"));
        assertSame(retained, epoch.getGeneratorLoader().load("sample"));
        assertEquals(3.0, retained.getZoom(), 0.0);
        assertEquals("{\"zoom\":3.0}", Files.readString(epochRoot.resolve("generators/sample.json")));
    }

    @Test
    public void resourceKeyDiscoveryPreservesNestedNamesAndCachedArrays() throws Exception {
        Path root = temporary.newFolder("resource-keys").toPath();
        for (String name : List.of(
                "generators/plain.json", "generators/nested.json/ridge.json.alt.json", "generators/ignored.JSON",
                "images/plain.png", "images/nested.png/ridge.png.alt.png", "images/ignored.PNG",
                "matter/plain.mat", "matter/nested.mat/ridge.mat.alt.mat", "matter/ignored.MAT")) {
            Path file = root.resolve(name);
            Files.createDirectories(file.getParent());
            Files.write(file, new byte[0]);
        }
        source = IrisData.get(root.toFile());
        Map<ResourceLoader<?>, Set<String>> expected = Map.of(
                source.getGeneratorLoader(), Set.of("plain", "nested/ridge.alt"),
                source.getImageLoader(), Set.of("plain", "nested.png/ridge.alt"),
                source.getMatterLoader(), Set.of("plain", "nested.mat/ridge.alt"));

        for (Map.Entry<ResourceLoader<?>, Set<String>> entry : expected.entrySet()) {
            String[] keys = entry.getKey().getPossibleKeys();
            assertEquals(entry.getValue(), Set.of(keys));
            assertEquals(entry.getValue().size(), keys.length);
            assertSame(keys, entry.getKey().getPossibleKeys());
        }
    }

    @Test
    public void dropRulePresenceIncludesEachScopeAndIsResetByPackReload() throws Exception {
        for (String folder : List.of("dimensions", "regions", "biomes")) {
            Path root = temporary.newFolder(folder + "-drops").toPath();
            Files.createDirectories(root.resolve(folder));
            Path resource = root.resolve(folder + "/sample.json");
            Files.writeString(resource, "{}");
            source = IrisData.openRuntime(root.toFile());
            try (MockedStatic<B> blocks = mockStatic(B.class)) {
                PlatformBlockState state = mock(PlatformBlockState.class);
                when(state.placementBaseState()).thenReturn(state);
                when(state.materialKey()).thenReturn("minecraft:stone");
                blocks.when(() -> B.getStateOrNull("minecraft:stone", false)).thenReturn(state);
                source.setContentGate(mock(ContentGate.class));
                assertTrue(source.hasBlockDropRules("minecraft:stone"));
                source.prepareBlockDropRules();
                assertFalse(source.hasBlockDropRules("minecraft:stone"));
                Files.writeString(resource, "{\"blockDrops\":[{\"blocks\":[{\"block\":\"minecraft:stone\"}]}]}");
                source.hotloaded();
                source.setContentGate(mock(ContentGate.class));
                assertTrue(source.hasBlockDropRules("minecraft:stone"));
                source.prepareBlockDropRules();
                assertTrue(source.hasBlockDropRules("minecraft:stone"));
                assertFalse(source.hasBlockDropRules("minecraft:grass_block"));
            } finally {
                source.close();
                source = null;
            }
        }
    }

    @Test
    public void customDropCandidatesUseTheResolvedCarrierAndKeepExactStateRulesEligible() throws Exception {
        Path root = temporary.newFolder("custom-drops").toPath();
        Files.createDirectories(root.resolve("biomes"));
        Files.writeString(root.resolve("biomes/sample.json"), """
                {"blockDrops":[{"exactBlocks":true,"blocks":[{"block":"rocks:ruby_ore"}]}]}
                """);
        source = IrisData.openRuntime(root.toFile());
        source.setContentGate(mock(ContentGate.class));
        PlatformBlockState custom = mock(PlatformBlockState.class);
        PlatformBlockState carrier = mock(PlatformBlockState.class);
        when(custom.materialKey()).thenReturn("rocks:ruby_ore");
        when(custom.placementBaseState()).thenReturn(carrier);
        when(carrier.materialKey()).thenReturn("minecraft:note_block");

        try (MockedStatic<B> blocks = mockStatic(B.class)) {
            blocks.when(() -> B.getStateOrNull("rocks:ruby_ore", false)).thenReturn(custom);

            source.prepareBlockDropRules();

            assertTrue(source.hasBlockDropRules("minecraft:note_block"));
            assertFalse(source.hasBlockDropRules("minecraft:stone"));
            assertFalse(source.hasBlockDropRules("rocks:ruby_ore"));
        }
    }
}

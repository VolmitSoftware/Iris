package art.arcane.iris.core.structure.export;

import art.arcane.iris.engine.framework.structure.StructureGraphResolver;
import art.arcane.iris.engine.object.IrisStructure;
import org.junit.Test;

import java.nio.file.Path;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.mock;

public class VanillaJigsawExportRequestTest {
    @Test
    public void aStructureSourceKeepsTheSuppliedGraphInsteadOfLoadingOne() {
        IrisStructure structure = mock(IrisStructure.class);
        StructureGraphResolver resolver = mock(StructureGraphResolver.class);

        VanillaJigsawExportSource source = VanillaJigsawExportSource.forStructure("  village  ", structure, resolver);

        assertEquals("village", source.structureKey());
        assertSame(structure, source.loadStructure());
        assertSame(resolver, source.resolver());
    }

    @Test
    public void aBlankStructureKeyIsRejected() {
        IllegalArgumentException failure = assertThrows(IllegalArgumentException.class,
                () -> VanillaJigsawExportSource.forStructure("   ", mock(IrisStructure.class), mock(StructureGraphResolver.class)));

        assertTrue(failure.getMessage(), failure.getMessage().contains("must not be blank"));
        assertThrows(NullPointerException.class,
                () -> VanillaJigsawExportSource.forStructure(null, mock(IrisStructure.class), mock(StructureGraphResolver.class)));
    }

    @Test
    public void theResourcePathDefaultsToTheStructureKey() {
        VanillaJigsawExportRequest request = request().build();

        assertEquals("village", request.resourcePath());
        assertEquals("iris", request.namespace());
        assertEquals(VanillaJigsawExportFormat.DIRECTORY, request.format());
        assertFalse(request.replaceExisting());
    }

    @Test
    public void theOutputPathIsStoredAbsoluteAndNormalized() {
        VanillaJigsawExportRequest request = VanillaJigsawExportRequest
                .builder(source(), Path.of("build", "exports", "..", "datapack"))
                .build();

        assertTrue(request.output().isAbsolute());
        assertEquals("datapack", request.output().getFileName().toString());
        assertFalse(request.output().toString().contains(".."));
    }

    @Test
    public void identityFieldsAreTrimmedButTheDescriptionIsNot() {
        VanillaJigsawExportRequest request = request()
                .namespace("  mypack  ")
                .resourcePath("  village/plains  ")
                .description("  a description  ")
                .format(VanillaJigsawExportFormat.ZIP)
                .replaceExisting(true)
                .build();

        assertEquals("mypack", request.namespace());
        assertEquals("village/plains", request.resourcePath());
        assertEquals("  a description  ", request.description());
        assertEquals(VanillaJigsawExportFormat.ZIP, request.format());
        assertTrue(request.replaceExisting());
    }

    @Test
    public void theDefaultSettingsDescribeAVanillaSurfaceStructure() {
        VanillaJigsawExportSettings settings = VanillaJigsawExportSettings.defaults();

        assertEquals(List.of("minecraft:plains"), settings.biomes());
        assertEquals(0, settings.startHeight());
        assertEquals(4064, settings.maxDistanceVertical());
        assertEquals(32, settings.spacing());
        assertEquals(8, settings.separation());
        assertEquals(0, settings.salt());
        assertEquals(1.0F, settings.frequency(), 0F);
        assertFalse(settings.expansionHack());
        assertEquals(VanillaJigsawExportSettings.ProjectHeightmap.WORLD_SURFACE_WG, settings.projectHeightmap());
        assertEquals(VanillaJigsawExportSettings.GenerationStep.SURFACE_STRUCTURES, settings.generationStep());
        assertEquals(VanillaJigsawExportSettings.TerrainAdaptation.NONE, settings.terrainAdaptation());
        assertEquals(VanillaJigsawExportSettings.SpreadType.LINEAR, settings.spreadType());
        assertNull(VanillaJigsawExportSettings.ProjectHeightmap.NONE.serializedName());
    }

    @Test
    public void theSettingsBuilderReplacesTheBiomeListRatherThanAppendingToIt() {
        VanillaJigsawExportSettings settings = VanillaJigsawExportSettings.builder()
                .biomes(List.of("minecraft:desert", "minecraft:savanna"))
                .biomes(List.of("minecraft:taiga"))
                .startHeight(64)
                .randomSpread(48, 12, 7)
                .frequency(0.5F)
                .build();

        assertEquals(List.of("minecraft:taiga"), settings.biomes());
        assertEquals(64, settings.startHeight());
        assertEquals(48, settings.spacing());
        assertEquals(12, settings.separation());
        assertEquals(7, settings.salt());
        assertEquals(0.5F, settings.frequency(), 0F);
    }

    private static VanillaJigsawExportSource source() {
        return VanillaJigsawExportSource.forStructure(
                "village", mock(IrisStructure.class), mock(StructureGraphResolver.class));
    }

    private static VanillaJigsawExportRequest.Builder request() {
        return VanillaJigsawExportRequest.builder(source(), Path.of("build", "datapack"));
    }
}

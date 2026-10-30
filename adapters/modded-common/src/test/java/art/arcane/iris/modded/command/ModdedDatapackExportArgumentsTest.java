package art.arcane.iris.modded.command;

import art.arcane.iris.pack.datapack.DataVersion;
import art.arcane.iris.pack.datapack.export.WorldgenExportRequest;
import org.junit.Test;

import java.nio.file.Path;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

public class ModdedDatapackExportArgumentsTest {
    @Test
    public void unknownKeyFails() {
        assertEquals("unknown option 'seed'", reason("overworld seed=1"));
        assertEquals("unknown option 'Version'", reason("overworld Version=26.3"));
    }

    @Test
    public void duplicateKeyFails() {
        assertEquals("option 'replace' given twice", reason("overworld replace=true replace=false"));
        assertEquals("option 'version' given twice", reason("overworld version= version=26.3"));
    }

    @Test
    public void firstBareTokenIsDimension() {
        ModdedDatapackExportArguments arguments = ModdedDatapackExportArguments.parse(
                "  version=26.3 overworld   mode=dimension namespace=worlds output=export_one format=directory objects=false replace=true ");

        assertEquals("overworld", arguments.dimension());
        assertEquals("26.3", arguments.version());
        assertEquals("dimension", arguments.mode());
        assertEquals("worlds", arguments.namespace());
        assertEquals("export_one", arguments.output());
        assertEquals("directory", arguments.format());
        assertFalse(arguments.objects());
        assertTrue(arguments.replace());
        assertSame(DataVersion.V26_3, arguments.target());
        assertEquals("unexpected argument 'underworld'", reason("overworld underworld"));
        assertEquals("missing dimension", reason("   "));
        assertEquals("missing dimension", reason("version=26.3"));
    }

    @Test
    public void defaultsMatchBukkit() {
        ModdedDatapackExportArguments arguments = ModdedDatapackExportArguments.parse("overworld");

        assertEquals("", arguments.version());
        assertEquals("overworld", arguments.mode());
        assertEquals("iris_export", arguments.namespace());
        assertEquals("", arguments.output());
        assertEquals("zip", arguments.format());
        assertTrue(arguments.objects());
        assertFalse(arguments.replace());
        assertSame(DataVersion.V26_3, arguments.target());

        Path pack = Path.of("packs", "overworld");
        Path exportRoot = Path.of("exports", "datapacks");
        WorldgenExportRequest expected = WorldgenExportRequest.builder()
                .pack(pack)
                .dimension("overworld")
                .exportRoot(exportRoot)
                .build();
        assertEquals(expected, arguments.toRequest(pack, exportRoot));
    }

    @Test
    public void unsupportedValuesFailWithSharedMessages() {
        assertEquals("Unsupported datapack export version '1.21.1'. Supported: 26.3",
                reason("overworld version=1.21.1"));
        assertEquals("Unsupported datapack export version '26.2'. Supported: 26.3",
                reason("overworld version=26.2"));
        assertEquals("Unknown export mode 'nether'. Expected: preset, dimension, overworld",
                reason("overworld mode=nether"));
        assertEquals("Export format must be directory or zip.", reason("overworld format=rar"));
        assertEquals("objects must be true or false", reason("overworld objects=yes"));
        assertEquals("replace must be true or false", reason("overworld replace=1"));
        assertEquals("invalid dimension key 'a..b'", reason("a..b"));
    }

    private static String reason(String raw) {
        return assertThrows(IllegalArgumentException.class, () -> ModdedDatapackExportArguments.parse(raw)).getMessage();
    }
}

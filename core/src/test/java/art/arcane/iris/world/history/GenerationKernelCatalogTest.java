package art.arcane.iris.world.history;

import org.junit.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThrows;

public class GenerationKernelCatalogTest {
    @Test
    public void parsesMultipleKernelRegistrations() throws Exception {
        GenerationKernelCatalog.Catalog catalog = parse("current\t2\t3\t4\nkernel\t1\nkernel\t2\n");
        assertEquals(new GenerationKernelRegistry.Version(2, 3, 4), catalog.current());
        assertEquals(List.of(1, 2), catalog.abis());
    }

    @Test
    public void rejectsMissingCurrentDuplicateAndExecutableCatalogEntries() {
        assertThrows(IOException.class, () -> parse("current\t2\t1\t1\nkernel\t1\n"));
        assertThrows(IOException.class, () -> parse("current\t1\t1\t1\nkernel\t1\nkernel\t1\n"));
        assertThrows(IOException.class, () -> parse("current\t1\t1\t1\nkernel\t1\tfactory.Override\n"));
        assertThrows(IOException.class, () -> parse("current\t1\t1\t1\nkernel\t01\n"));
    }

    @Test
    public void installedCatalogUsesItsBuildFactoryAndAlgorithms() throws Exception {
        GenerationKernelRegistry registry = GenerationKernelCatalog.load();
        GenerationBuildRevision.Descriptor revision = GenerationBuildRevision.load(1);
        assertEquals("art.arcane.iris.world.history.GenerationKernelV1", revision.factoryClass());
        assertEquals(revision.fingerprint(), registry.select(new GenerationKernelRegistry.Version(1, 1, 1))
                .implementationFingerprint());
        assertThrows(IOException.class, () -> registry.select(new GenerationKernelRegistry.Version(1, 2, 1)));
    }

    private static GenerationKernelCatalog.Catalog parse(String entries) throws IOException {
        return GenerationKernelCatalog.parse(("iris-generation-kernel-catalog-v1\n" + entries)
                .getBytes(StandardCharsets.UTF_8));
    }
}

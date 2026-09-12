package art.arcane.iris.studio.object;

import art.arcane.iris.pack.loading.IrisData;
import art.arcane.iris.pack.loading.ResourceLoader;
import art.arcane.iris.structure.object.IrisObject;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.DataOutputStream;
import java.io.File;
import java.nio.file.Files;
import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class ObjectStudioLayoutTest {
    @Rule
    public final TemporaryFolder temporary = new TemporaryFolder();

    @Test
    public void changedObjectHeaderInvalidatesSavedLayoutWithoutChangingItsKey() throws Exception {
        File objectFile = temporary.newFile("jungleclutt6.iob");
        File layoutFile = temporary.newFile("layout.json");
        IrisData data = source(objectFile);
        Map<String, IrisData> sources = Map.of("overworld", data);
        int[][] dimensions = {{6, 3, 4}, {5, 4, 4}, {5, 3, 5}};
        for (int[] changed : dimensions) {
            header(objectFile, 5, 3, 4);
            ObjectStudioLayout.build(sources, 2).save(layoutFile);
            assertNotNull(ObjectStudioLayout.load(layoutFile, sources, 2));

            header(objectFile, changed[0], changed[1], changed[2]);

            assertNull(ObjectStudioLayout.load(layoutFile, sources, 2));
            ObjectStudioLayout.GridCell rebuilt = ObjectStudioLayout.build(sources, 2).get("jungleclutt6");
            assertEquals(changed[0], rebuilt.w());
            assertEquals(changed[1], rebuilt.h());
            assertEquals(changed[2], rebuilt.d());
        }
    }

    @Test
    public void highFloorMovesCellsWithoutChangingTheirPackingAndPersists() throws Exception {
        File objectFile = temporary.newFile("jungleclutt6.iob");
        File layoutFile = temporary.newFile("layout.json");
        header(objectFile, 5, 3, 4);
        Map<String, IrisData> sources = Map.of("overworld", source(objectFile));
        ObjectStudioLayout original = ObjectStudioLayout.build(sources, 3, 32);
        ObjectStudioLayout.GridCell before = original.get("jungleclutt6");

        ObjectStudioLayout raised = original.atFloor(200);
        ObjectStudioLayout.GridCell after = raised.get("overworld/jungleclutt6");

        assertEquals(201, after.originY());
        assertEquals(65, before.originY());
        assertEquals(before.originX(), after.originX());
        assertEquals(before.originZ(), after.originZ());
        assertEquals(before.w(), after.w());
        assertEquals(before.h(), after.h());
        assertEquals(before.d(), after.d());
        assertSame(raised, raised.atFloor(200));
        raised.save(layoutFile);
        ObjectStudioLayout restored = ObjectStudioLayout.load(layoutFile, sources, 3);
        assertNotNull(restored);
        assertEquals(after, restored.get("jungleclutt6"));
    }

    @Test
    public void removedSourceObjectInvalidatesItsSavedCell() throws Exception {
        File objectFile = temporary.newFile("jungleclutt6.iob");
        File layoutFile = temporary.newFile("layout.json");
        header(objectFile, 5, 3, 4);
        IrisData data = source(objectFile);
        Map<String, IrisData> sources = Map.of("overworld", data);
        ObjectStudioLayout.build(sources, 2).save(layoutFile);
        when(data.getObjectLoader().findFile("jungleclutt6")).thenReturn(null);

        assertNull(ObjectStudioLayout.load(layoutFile, sources, 2));
    }

    @SuppressWarnings("unchecked")
    private static IrisData source(File objectFile) {
        IrisData data = mock(IrisData.class);
        ResourceLoader<IrisObject> loader = mock(ResourceLoader.class);
        when(data.getObjectLoader()).thenReturn(loader);
        when(loader.getPossibleKeys()).thenReturn(new String[]{"jungleclutt6"});
        when(loader.findFile("jungleclutt6")).thenReturn(objectFile);
        return data;
    }

    private static void header(File file, int width, int height, int depth) throws Exception {
        try (DataOutputStream output = new DataOutputStream(Files.newOutputStream(file.toPath()))) {
            output.writeInt(width);
            output.writeInt(height);
            output.writeInt(depth);
        }
    }
}

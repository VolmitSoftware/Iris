package art.arcane.iris.core.modules;

import art.arcane.iris.core.IrisSettings;
import art.arcane.iris.core.loader.IrisData;
import art.arcane.iris.engine.UsableBlockPlatform;
import art.arcane.iris.engine.object.prefab.IrisObject;
import com.volmit.object.PackObjects;
import com.volmit.object.PlaceableObject;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotSame;
import static org.junit.Assert.assertSame;
import static org.mockito.Mockito.mock;

/** Smart boring hands back a bored copy and leaves the pack's own object as it was. */
public class IrisPackObjectsSmartBoredTest {
    private static final int SIZE = 3;
    private static final int SHELL_BLOCKS = SIZE * SIZE * SIZE - 1;
    private static final int MIDDLE = 1;

    private final PackObjects objects = new IrisPackObjects(mock(IrisData.class), mock(IrisPackResources.class));

    private static IrisSettings previousSettings;

    @BeforeClass
    public static void bindPlatform() {
        UsableBlockPlatform.bind();
        previousSettings = IrisSettings.settings;
        IrisSettings.settings = new IrisSettings();
    }

    @AfterClass
    public static void unbindPlatform() {
        IrisSettings.settings = previousSettings;
        UsableBlockPlatform.unbind();
    }

    @Test
    public void theBoredObjectIsACopyAndThePacksObjectKeepsItsBlocks() {
        IrisObject shell = hollowCube();

        PlaceableObject bored = objects.smartBored(shell);

        assertNotSame(shell, bored);
        assertEquals(SHELL_BLOCKS, shell.getBlocks().size());
        assertEquals(SHELL_BLOCKS + 1, ((IrisObject) bored).getBlocks().size());
        assertEquals(IrisObject.States.VAIR, ((IrisObject) bored).getBlocks().get(shell.getSigned(MIDDLE, MIDDLE, MIDDLE)));
    }

    @Test
    public void boringAgainGivesTheSameCopyWithoutBoringAgain() {
        IrisObject shell = hollowCube();

        PlaceableObject bored = objects.smartBored(shell);

        assertSame(bored, objects.smartBored(shell));
        assertSame(bored, objects.smartBored(bored));
    }

    @Test
    public void changingTheObjectMakesTheNextBoredCopyFromItsNewBlocks() {
        IrisObject shell = hollowCube();
        PlaceableObject before = objects.smartBored(shell);

        shell.setUnsigned(0, 0, 0, null);
        PlaceableObject after = objects.smartBored(shell);

        assertNotSame(before, after);
    }

    private static IrisObject hollowCube() {
        IrisObject cube = new IrisObject(SIZE, SIZE, SIZE);
        for (int x = 0; x < SIZE; x++) {
            for (int y = 0; y < SIZE; y++) {
                for (int z = 0; z < SIZE; z++) {
                    if (x != MIDDLE || y != MIDDLE || z != MIDDLE) {
                        cube.setUnsigned(x, y, z, IrisObject.States.STONE);
                    }
                }
            }
        }
        return cube;
    }
}

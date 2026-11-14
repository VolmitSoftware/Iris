package art.arcane.iris.structure.object;

import art.arcane.iris.generation.runtime.PreservationRegistry;
import art.arcane.iris.pack.loading.IrisData;
import art.arcane.iris.spi.IrisServices;
import art.arcane.iris.testsupport.CapturedLog;
import art.arcane.iris.testsupport.PlatformLeakGuard;
import art.arcane.iris.world.history.GenerationRegistryContract;
import art.arcane.volmlib.util.collection.KList;
import org.junit.After;
import org.junit.Before;
import org.junit.ClassRule;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import static org.junit.Assert.assertEquals;
import static org.mockito.Mockito.mock;

public class IrisObjectPlacementOnceWarningTest {
    private static final String MISSING_TABLE = "Couldn't find loot table ";

    @ClassRule
    public static final PlatformLeakGuard PLATFORM_GUARD = PlatformLeakGuard.clean();

    @Rule
    public TemporaryFolder temporaryFolder = new TemporaryFolder();

    private CapturedLog log;
    private IrisData data;

    @Before
    public void openPack() throws Exception {
        log = CapturedLog.bind(temporaryFolder.newFolder("platform-data"));
        IrisServices.register(PreservationRegistry.class, mock(PreservationRegistry.class));
        data = IrisData.openRuntime(temporaryFolder.newFolder("pack"));
        data.bindGenerationRegistryContract(GenerationRegistryContract.empty());
    }

    @After
    public void closePack() {
        data.close();
        IrisServices.remove(PreservationRegistry.class);
        log.close();
    }

    @Test
    public void aMissingLootTableWarnsOnceAcrossPlacements() {
        lookUpTable(placementWithLoot("chests/typo"));
        lookUpTable(placementWithLoot("chests/typo"));

        assertEquals(1, log.warnings(MISSING_TABLE).size());
    }

    @Test
    public void aDifferentMissingLootTableWarnsSeparately() {
        lookUpTable(placementWithLoot("chests/typo"));
        lookUpTable(placementWithLoot("chests/other-typo"));

        assertEquals(2, log.warnings(MISSING_TABLE).size());
    }

    @Test
    public void clearingThePackListsLetsTheSameMissingLootTableWarnAgain() {
        lookUpTable(placementWithLoot("chests/typo"));

        data.clearLists();
        lookUpTable(placementWithLoot("chests/typo"));

        assertEquals(2, log.warnings(MISSING_TABLE).size());
    }

    private static IrisObjectPlacement placementWithLoot(String tableName) {
        IrisObjectLoot loot = new IrisObjectLoot();
        loot.setName(tableName);
        IrisObjectPlacement placement = new IrisObjectPlacement();
        placement.setLoot(new KList<IrisObjectLoot>().qadd(loot));
        return placement;
    }

    private void lookUpTable(IrisObjectPlacement placement) {
        placement.getCache(data);
    }
}

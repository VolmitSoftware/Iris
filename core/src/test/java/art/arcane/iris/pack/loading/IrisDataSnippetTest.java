package art.arcane.iris.pack.loading;

import art.arcane.iris.configuration.IrisSettings;
import art.arcane.iris.generation.runtime.PreservationRegistry;
import art.arcane.iris.pack.value.IrisRate;
import art.arcane.iris.spi.IrisServices;
import art.arcane.iris.testsupport.PlatformLeakGuard;
import art.arcane.volmlib.util.collection.KList;
import org.junit.After;
import org.junit.Before;
import org.junit.ClassRule;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.mockito.Mockito.mock;

public class IrisDataSnippetTest {
    @ClassRule
    public static final PlatformLeakGuard PLATFORM_GUARD = PlatformLeakGuard.clean();

    @Rule
    public final TemporaryFolder temporary = new TemporaryFolder();

    private IrisSettings previousSettings;
    private Path pack;
    private IrisData data;

    public static class RateHolder {
        public IrisRate rate;
    }

    public static class RateListHolder {
        public List<IrisRate> rates;
        public KList<IrisRate> keyedRates;
    }

    @Before
    public void createPack() throws IOException {
        previousSettings = IrisSettings.settings;
        IrisSettings.settings = new IrisSettings();
        IrisServices.register(PreservationRegistry.class, mock(PreservationRegistry.class));
        pack = temporary.newFolder("pack").toPath();
    }

    @After
    public void closeData() {
        if (data != null) {
            data.close();
        }
        IrisServices.remove(PreservationRegistry.class);
        IrisSettings.settings = previousSettings;
    }

    @Test
    public void nestedPathResolves() throws IOException {
        write("snippet/rate/groups/fast.json", "{\"amount\":8}");

        assertEquals(8, rateOf("\"snippet/groups/fast\"").getAmount());
        assertEquals(8, rateOf("\"snippet/rate/groups/fast\"").getAmount());
    }

    @Test
    public void shortAndFullReferencesResolve() throws IOException {
        write("snippet/rate/slow.json", "{\"amount\":3}");

        assertEquals(3, rateOf("\"snippet/slow\"").getAmount());
        assertEquals(3, rateOf("\"snippet/rate/slow\"").getAmount());
    }

    @Test
    public void snippetsOfAnotherTypeAreNotUsed() throws IOException {
        write("snippet/duration/fast.json", "{\"amount\":7}");

        assertNull(rateOf("\"snippet/fast\""));
    }

    @Test
    public void referenceCannotLeaveItsSnippetFolder() throws IOException {
        write("snippet/duration/fast.json", "{\"amount\":7}");
        write("snippet/rate/other.json", "{\"amount\":1}");

        assertNull(rateOf("\"snippet/rate/../duration/fast\""));
    }

    @Test
    public void symlinkLeadingOutOfItsSnippetFolderIsNotFound() throws IOException {
        write("snippet/duration/fast.json", "{\"amount\":7}");
        link("snippet/rate/fast.json", "snippet/duration/fast.json");

        assertNull(rateOf("\"snippet/fast\""));
    }

    @Test
    public void symlinkStayingInsideItsSnippetFolderResolves() throws IOException {
        write("snippet/rate/real.json", "{\"amount\":4}");
        link("snippet/rate/alias.json", "snippet/rate/real.json");

        assertEquals(4, rateOf("\"snippet/alias\"").getAmount());
    }

    @Test
    public void missingSnippetYieldsNull() throws IOException {
        write("snippet/rate/other.json", "{\"amount\":1}");

        assertNull(rateOf("\"snippet/absent\""));
    }

    @Test
    public void arraySnippetInAListStandsForAllItsEntries() throws IOException {
        write("snippet/rate/many.json", "[{\"amount\":1},{\"amount\":2},]");
        write("snippet/rate/one.json", "{\"amount\":5}");

        RateListHolder holder = data().getGson().fromJson(
                "{\"rates\":[\"snippet/many\",\"snippet/one\",{\"amount\":9}],"
                        + "\"keyedRates\":[\"snippet/many\",{\"amount\":9}]}",
                RateListHolder.class);

        assertEquals(List.of(1, 2, 5, 9), amountsOf(holder.rates));
        assertEquals(List.of(1, 2, 9), amountsOf(holder.keyedRates));
    }

    @Test
    public void unreadableSnippetKeepsItsSlotInAList() throws IOException {
        write("snippet/rate/one.json", "{\"amount\":5}");

        RateListHolder holder = data().getGson().fromJson(
                "{\"rates\":[\"snippet/missing\",\"snippet/one\"]}", RateListHolder.class);

        assertEquals(2, holder.rates.size());
        assertNull(holder.rates.get(0));
        assertEquals(5, holder.rates.get(1).getAmount());
    }

    @Test
    public void singleReferenceToAnArraySnippetYieldsNull() throws IOException {
        write("snippet/rate/many.json", "[{\"amount\":1},{\"amount\":2}]");

        assertNull(rateOf("\"snippet/many\""));
    }

    @Test
    public void listsWithoutReferencesStillLoad() {
        RateListHolder holder = data().getGson().fromJson(
                "{\"rates\":[{\"amount\":4},{\"amount\":6}]}", RateListHolder.class);

        assertEquals(List.of(4, 6), amountsOf(holder.rates));
    }

    @Test
    public void possibleSnippetsListTheTypeFolderAsShortReferences() throws IOException {
        write("snippet/rate/a.json", "{}");
        write("snippet/rate/nested/c.json", "{}");
        write("snippet/duration/other.json", "{}");

        assertEquals(List.of("snippet/a", "snippet/nested/c"),
                data().getPossibleSnippets("rate").stream().sorted().toList());
    }

    private IrisRate rateOf(String jsonValue) {
        return data().getGson().fromJson("{\"rate\":" + jsonValue + "}", RateHolder.class).rate;
    }

    private IrisData data() {
        if (data == null) {
            data = IrisData.get(new File(pack.toFile(), "."));
        }
        return data;
    }

    private void write(String relative, String content) throws IOException {
        Path file = pack.resolve(relative);
        Files.createDirectories(file.getParent());
        Files.writeString(file, content);
    }

    private void link(String relative, String target) throws IOException {
        Path link = pack.resolve(relative);
        Files.createDirectories(link.getParent());
        Files.createSymbolicLink(link, pack.resolve(target));
    }

    private static List<Integer> amountsOf(List<IrisRate> rates) {
        assertNotNull(rates);
        List<Integer> amounts = new ArrayList<>();
        for (IrisRate rate : rates) {
            assertNotNull(rate);
            amounts.add(rate.getAmount());
        }
        return amounts;
    }
}

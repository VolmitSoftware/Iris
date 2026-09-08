package art.arcane.iris.core.link;

import art.arcane.iris.api.pregen.IrisPregenPhase;
import art.arcane.iris.api.pregen.IrisPregenProgress;
import art.arcane.iris.api.terrain.IrisTerrainService;
import org.bukkit.OfflinePlayer;
import org.bukkit.World;
import org.junit.Test;

import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.util.logging.Level;
import java.util.logging.Logger;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

public class IrisPapiExpansionTest {
    private static final UUID PLAYER_ID = UUID.fromString("00000000-0000-0000-0000-0000000000b2");
    private static final String DASH = "---";

    private static final List<String> PUBLISHED_KEYS = List.of(
            "available",
            "pregen.available",
            "pregen.chunks",
            "pregen.chunks-per-second",
            "pregen.eta",
            "pregen.eta-text",
            "pregen.paused",
            "pregen.percent",
            "pregen.total",
            "pregen.world",
            "world.available",
            "world.biome",
            "world.biome-key",
            "world.dimension",
            "world.region",
            "world.region-key");

    private static final List<String> RETIRED_KEYS = List.of(
            "biome_name",
            "biome_id",
            "biome_file",
            "region_name",
            "region_id",
            "region_file",
            "terrain_slope",
            "terrain_height",
            "world_mode",
            "world_seed",
            "world_speed");

    private static Logger quietLogger() {
        Logger logger = Logger.getLogger("IrisPapiExpansionTest");
        logger.setUseParentHandlers(false);
        logger.setLevel(Level.OFF);
        return logger;
    }

    private static IrisPapiExpansion expansion(IrisPapiState state) {
        return new IrisPapiExpansion(state, quietLogger());
    }

    private static IrisPapiState populatedState() {
        FakeIrisTerrainService terrain = new FakeIrisTerrainService();
        World world = IrisPapiTestSupport.world("sandbox");
        terrain.addIrisWorld(world);
        IrisPapiState state = new IrisPapiState(() -> terrain, new IrisPapiTestSupport.Clock());
        state.trackPosition(PLAYER_ID, world, 64, 64);
        state.publishPregen(IrisPregenPhase.TICK, new IrisPregenProgress(
                "sandbox", "sandbox:identity", 42.5D, 1234L, 4096L, 2862L, 0L, 12.5D, 125_000L, 60_000L, "async", false));
        return state;
    }

    @Test
    public void theExpansionPublishesExactlyTheDocumentedKeySet() {
        assertEquals(PUBLISHED_KEYS, expansion(populatedState()).getPlaceholders());
    }

    @Test
    public void everyPublishedKeyResolvesToAValue() {
        IrisPapiExpansion expansion = expansion(populatedState());
        OfflinePlayer player = IrisPapiTestSupport.player(PLAYER_ID);

        for (String key : PUBLISHED_KEYS) {
            String value = expansion.onRequest(player, key);
            assertNotNull("published key " + key + " must resolve", value);
            assertFalse("published key " + key + " must not resolve to an empty string", value.isEmpty());
        }
    }

    @Test
    public void aFullyPopulatedBoardRendersRealValues() {
        IrisPapiExpansion expansion = expansion(populatedState());
        OfflinePlayer player = IrisPapiTestSupport.player(PLAYER_ID);

        assertEquals("true", expansion.onRequest(player, "available"));
        assertEquals("true", expansion.onRequest(player, "world.available"));
        assertEquals("Hot Desert Dunes", expansion.onRequest(player, "world.biome"));
        assertEquals("desert/hot-dunes", expansion.onRequest(player, "world.biome-key"));
        assertEquals("Scorched Expanse", expansion.onRequest(player, "world.region"));
        assertEquals("scorched", expansion.onRequest(player, "world.region-key"));
        assertEquals("overworld", expansion.onRequest(player, "world.dimension"));
        assertEquals("true", expansion.onRequest(player, "pregen.available"));
        assertEquals("sandbox", expansion.onRequest(player, "pregen.world"));
        assertEquals("42.50", expansion.onRequest(player, "pregen.percent"));
        assertEquals("125", expansion.onRequest(player, "pregen.eta"));
        assertEquals("2m 5s", expansion.onRequest(player, "pregen.eta-text"));
        assertEquals("1234", expansion.onRequest(player, "pregen.chunks"));
        assertEquals("4096", expansion.onRequest(player, "pregen.total"));
        assertEquals("12.50", expansion.onRequest(player, "pregen.chunks-per-second"));
        assertEquals("false", expansion.onRequest(player, "pregen.paused"));
    }

    @Test
    public void everyPublishedKeyObeysTheSuiteGrammar() {
        for (String key : expansion(populatedState()).getPlaceholders()) {
            assertFalse("a placeholder path may never contain '_': " + key, key.indexOf('_') >= 0);
            assertEquals("a placeholder path is lowercase ascii: " + key, key.toLowerCase(Locale.ROOT), key);

            for (String segment : key.split("\\.", -1)) {
                assertFalse("empty segment in " + key, segment.isEmpty());
                assertTrue("segment must match [a-z0-9-] in " + key, segment.matches("[a-z0-9-]+"));
            }
        }
    }

    @Test
    public void anUnknownPathReturnsNullSoTheTypoStaysVisible() {
        IrisPapiExpansion expansion = expansion(populatedState());
        OfflinePlayer player = IrisPapiTestSupport.player(PLAYER_ID);

        assertNull(expansion.onRequest(player, "world.biom"));
        assertNull(expansion.onRequest(player, "world"));
        assertNull(expansion.onRequest(player, "world."));
        assertNull(expansion.onRequest(player, ".biome"));
        assertNull(expansion.onRequest(player, "definitely-not-a-key"));
    }

    @Test
    public void theRetiredUnderscoreGrammarNoLongerResolves() {
        IrisPapiExpansion expansion = expansion(populatedState());
        OfflinePlayer player = IrisPapiTestSupport.player(PLAYER_ID);

        for (String retired : RETIRED_KEYS) {
            assertNull("the old key " + retired + " must render literally, not silently answer",
                    expansion.onRequest(player, retired));
        }
    }

    @Test
    public void blankParamsReturnNull() {
        IrisPapiExpansion expansion = expansion(populatedState());
        OfflinePlayer player = IrisPapiTestSupport.player(PLAYER_ID);

        assertNull(expansion.onRequest(player, null));
        assertNull(expansion.onRequest(player, ""));
        assertNull(expansion.onRequest(player, "   "));
    }

    @Test
    public void pathsAreLowercasedBeforeDispatch() {
        IrisPapiExpansion expansion = expansion(populatedState());
        OfflinePlayer player = IrisPapiTestSupport.player(PLAYER_ID);

        assertEquals("Hot Desert Dunes", expansion.onRequest(player, "WORLD.BIOME"));
        assertEquals("Hot Desert Dunes", expansion.onRequest(player, "World.Biome"));
    }

    @Test
    public void anUntrackedPlayerGetsTheUnavailableSentinelRatherThanALie() {
        IrisPapiExpansion expansion = expansion(populatedState());
        OfflinePlayer stranger = IrisPapiTestSupport.player(UUID.fromString("00000000-0000-0000-0000-0000000000c3"));

        assertEquals("false", expansion.onRequest(stranger, "world.available"));
        assertEquals(DASH, expansion.onRequest(stranger, "world.biome"));
        assertEquals("true", expansion.onRequest(stranger, "available"));
    }

    @Test
    public void aNullPlayerStillAnswersTheGlobalKeys() {
        IrisPapiExpansion expansion = expansion(populatedState());

        assertEquals("true", expansion.onRequest(null, "available"));
        assertEquals("42.50", expansion.onRequest(null, "pregen.percent"));
        assertEquals(DASH, expansion.onRequest(null, "world.biome"));
    }

    @Test
    public void aResolverThatThrowsIsCaughtAndReportedAsUnavailable() {
        IrisPapiState exploding = new IrisPapiState(() -> {
            throw new IllegalStateException("terrain service exploded");
        }, new IrisPapiTestSupport.Clock());

        assertEquals(DASH, expansion(exploding).onRequest(IrisPapiTestSupport.player(PLAYER_ID), "available"));
    }

    @Test
    public void metadataIsHardcodedAndTheOwningPluginIsDeclared() {
        IrisPapiExpansion expansion = expansion(populatedState());

        assertEquals("iris", expansion.getIdentifier());
        assertEquals("Volmit Software", expansion.getAuthor());
        assertEquals("2.0.0", expansion.getVersion());
        assertEquals("Iris", expansion.getRequiredPlugin());
        assertTrue(expansion.persist());
    }

    @Test
    public void theTerrainServiceIsTheOnlyDoorIntoIris() {
        IrisTerrainService service = new FakeIrisTerrainService();
        IrisPapiState state = new IrisPapiState(() -> service, new IrisPapiTestSupport.Clock());

        assertEquals("true", expansion(state).onRequest(IrisPapiTestSupport.player(PLAYER_ID), "available"));
    }
}

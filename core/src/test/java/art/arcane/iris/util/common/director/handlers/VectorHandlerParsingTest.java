package art.arcane.iris.util.common.director.handlers;

import art.arcane.volmlib.util.director.exceptions.DirectorParsingException;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.util.Vector;
import org.junit.Test;

import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class VectorHandlerParsingTest {
    @Test
    public void threeComponentsMapToTheirAxes() throws DirectorParsingException {
        Vector parsed = console().parse("1, 2 ,3", false);

        assertEquals(1D, parsed.getX(), 0D);
        assertEquals(2D, parsed.getY(), 0D);
        assertEquals(3D, parsed.getZ(), 0D);
    }

    @Test
    public void twoComponentsAreTreatedAsAHorizontalPair() throws DirectorParsingException {
        Vector parsed = console().parse("4,6", false);

        assertEquals(4D, parsed.getX(), 0D);
        assertEquals(0D, parsed.getY(), 0D);
        assertEquals(6D, parsed.getZ(), 0D);
    }

    @Test
    public void anyOtherComponentCountIsRejectedWithItsArity() {
        DirectorParsingException failure =
                assertThrows(DirectorParsingException.class, () -> console().parse("1,2,3,4", false));

        assertTrue(failure.getMessage(), failure.getMessage().contains("4 components"));
        assertTrue(failure.getMessage(), failure.getMessage().contains("Expected 2 or 3"));
    }

    @Test
    public void positionalKeywordsAreRefusedForAConsoleSender() {
        VectorHandler handler = console();

        assertTrue(assertThrows(DirectorParsingException.class, () -> handler.parse("here", false))
                .getMessage().contains("as a console"));
        assertTrue(assertThrows(DirectorParsingException.class, () -> handler.parse("SELF", false))
                .getMessage().contains("as a console"));
        assertTrue(assertThrows(DirectorParsingException.class, () -> handler.parse("look", false))
                .getMessage().contains("as a console"));
        assertTrue(assertThrows(DirectorParsingException.class, () -> handler.parse("Crosshair", false))
                .getMessage().contains("as a console"));
    }

    @Test
    public void positionalKeywordsResolveForAPlayerSender() throws DirectorParsingException {
        VectorHandler handler = new TestVectorHandler(true, new Vector(10, 20, 30), new Vector(-1, -2, -3), List.of());

        assertEquals(new Vector(10, 20, 30), handler.parse("me", false));
        assertEquals(new Vector(-1, -2, -3), handler.parse("cursor", false));
    }

    @Test
    public void aNamedPlayerResolvesToThatPlayersPosition() throws DirectorParsingException {
        Player player = mock(Player.class);
        when(player.getLocation()).thenReturn(new Location(null, 7D, 8D, 9D));
        VectorHandler handler = new TestVectorHandler(false, null, null, List.of(player));

        assertEquals(new Vector(7, 8, 9), handler.parse("player:steve", false));
    }

    @Test
    public void anUnmatchedPlayerQueryNamesTheQuery() {
        VectorHandler handler = new TestVectorHandler(false, null, null, List.of());

        DirectorParsingException failure =
                assertThrows(DirectorParsingException.class, () -> handler.parse("player:steve", false));

        assertTrue(failure.getMessage(), failure.getMessage().contains("steve"));
    }

    @Test
    public void anUnrecognizedTokenResolvesToNothingRatherThanFailing() throws DirectorParsingException {
        assertNull(console().parse("garbage", false));
        assertNull(console().parse("", false));
    }

    @Test
    public void theRenderedFormOmitsAZeroHeight() {
        VectorHandler handler = console();

        assertEquals(2, handler.toString(new Vector(1, 0, 3)).split(",").length);
        assertEquals(3, handler.toString(new Vector(1, 2, 3)).split(",").length);
    }

    @Test
    public void onlyVectorParametersAreClaimed() {
        assertTrue(console().supports(Vector.class));
        assertEquals(false, console().supports(Location.class));
    }

    private static VectorHandler console() {
        return new TestVectorHandler(false, null, null, List.of());
    }

    private static final class TestVectorHandler extends VectorHandler {
        private final boolean senderIsPlayer;
        private final Vector senderVector;
        private final Vector lookVector;
        private final List<?> players;

        private TestVectorHandler(boolean senderIsPlayer, Vector senderVector, Vector lookVector, List<?> players) {
            this.senderIsPlayer = senderIsPlayer;
            this.senderVector = senderVector;
            this.lookVector = lookVector;
            this.players = players;
        }

        @Override
        protected boolean isSenderPlayer() {
            return senderIsPlayer;
        }

        @Override
        protected Vector getSenderVector() {
            return senderVector;
        }

        @Override
        protected Vector getLookVector() {
            return lookVector;
        }

        @Override
        protected List<?> playerPossibilities(String query) {
            return players;
        }
    }
}

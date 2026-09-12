package art.arcane.iris.command;

import art.arcane.volmlib.util.director.DirectorOrigin;
import art.arcane.volmlib.util.director.annotations.Director;
import org.junit.Test;

import java.lang.reflect.Method;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class StudioScoreboardCommandTest {
    @Test
    public void scoreboardCommandIsPlayerScopedAndEntityScheduled() throws NoSuchMethodException {
        Method command = CommandStudio.class.getDeclaredMethod("scoreboard");
        Director director = command.getAnnotation(Director.class);

        assertEquals(DirectorOrigin.PLAYER, director.origin());
        assertFalse(director.sync());
        assertTrue(List.of(director.aliases()).contains("board"));
        assertTrue(List.of(director.aliases()).contains("sidebar"));
    }
}

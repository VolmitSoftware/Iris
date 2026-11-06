/*
 * Iris is a World Generator for Minecraft Servers
 * Copyright (c) 2026 Arcane Arts (Volmit Software)
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

package art.arcane.iris.core.modules;

import art.arcane.iris.core.IrisSettings;
import art.arcane.iris.core.loader.IrisData;
import art.arcane.iris.engine.framework.Engine;
import art.arcane.iris.engine.object.world.dimension.IrisDimension;
import art.arcane.iris.spi.IrisPlatforms;
import com.volmit.ModuleLog;
import com.volmit.host.ModuleLogs;
import com.volmit.world.PackWorld;
import org.junit.After;
import org.junit.Assume;
import org.junit.Before;
import org.junit.Test;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * A module's log is written to Iris's log with the module's id in front, follows Iris's debug setting and reports a failure like
 * Iris's own; the world a module is handed carries the module's log.
 */
public class ModuleLogTest {
    private static final String MODULE = "iris:decorant";

    private final ByteArrayOutputStream out = new ByteArrayOutputStream();
    private final ByteArrayOutputStream err = new ByteArrayOutputStream();
    private PrintStream originalOut;
    private PrintStream originalErr;
    private IrisSettings originalSettings;
    private IrisSettings settings;

    @Before
    public void captureTheProcessStreamsIrisLogsToWhenNoPlatformIsBound() {
        Assume.assumeFalse("a platform is bound, so the log does not go to the process streams", IrisPlatforms.isBound());
        originalOut = System.out;
        originalErr = System.err;
        originalSettings = IrisSettings.settings;
        System.setOut(new PrintStream(out, true, StandardCharsets.UTF_8));
        System.setErr(new PrintStream(err, true, StandardCharsets.UTF_8));
        settings = new IrisSettings();
        IrisSettings.settings = settings;
    }

    @After
    public void restoreTheProcessStreams() {
        if (originalOut == null) {
            return;
        }
        System.setOut(originalOut);
        System.setErr(originalErr);
        IrisSettings.settings = originalSettings;
    }

    @Test
    public void everyLevelCarriesTheModulesIdAndKeepsItsSeverity() {
        settings.getGeneral().debug = true;
        ModuleLog log = log();

        log.debug("shown for debugging");
        log.info("started");
        log.warn("unusual");
        log.error("failed", null);

        assertTrue(out(), out().contains("[Iris/DEBUG] [iris:decorant] shown for debugging"));
        assertTrue(out(), out().contains("[Iris/INFO] [iris:decorant] started"));
        assertTrue(err(), err().contains("[Iris/WARN] [iris:decorant] unusual"));
        assertTrue(err(), err().contains("[Iris/ERROR] [iris:decorant] failed"));
    }

    @Test
    public void debugFollowsIrisDebugSetting() {
        ModuleLog log = log();

        settings.getGeneral().debug = false;
        log.debug("while off");
        settings.getGeneral().debug = true;
        log.debug("while on");

        assertFalse(out(), out().contains("while off"));
        assertTrue(out(), out().contains("while on"));
    }

    @Test
    public void anErrorWithACauseIsReportedLikeIrisReportsItsOwnFailures() {
        log().error("Failed to place a plant", new IllegalStateException("boom"));

        assertTrue(err(), err().contains("[Iris/ERROR] [iris:decorant] Failed to place a plant"));
        assertTrue(err(), err().contains("java.lang.IllegalStateException: boom"));
    }

    @Test
    public void warnOnceWarnsOnceAndDebugsAfterwards() {
        settings.getGeneral().debug = false;
        ModuleLog log = log();

        log.warnOnce("empty:plains", "Empty block data for plains");
        log.warnOnce("empty:plains", "Empty block data for plains");

        assertEquals(err(), 1, err().split("Empty block data for plains", -1).length - 1);
        assertFalse(out(), out().contains("Empty block data"));
    }

    @Test
    public void theWorldOffersIrissOwnCodeTheEngine() {
        Engine engine = mock(Engine.class);
        when(engine.getData()).thenReturn(mock(IrisData.class));
        when(engine.getDimension()).thenReturn(new IrisDimension());
        PackWorld world = new IrisPackWorld(engine);

        assertSame(engine, world.extension(Engine.class).orElseThrow());
        assertTrue("nothing else is offered", world.extension(String.class).isEmpty());
    }

    @Test
    public void theWorldItselfLogsAsIris() {
        Engine engine = mock(Engine.class);
        when(engine.getData()).thenReturn(mock(IrisData.class));

        new IrisPackWorld(engine).log().warn("host line");

        assertTrue(err(), err().contains("[Iris/WARN] [iris] host line"));
    }

    private static ModuleLog log() {
        return ModuleLogs.labelled(MODULE, new IrisApiLogger("test"));
    }

    private String out() {
        return out.toString(StandardCharsets.UTF_8);
    }

    private String err() {
        return err.toString(StandardCharsets.UTF_8);
    }
}

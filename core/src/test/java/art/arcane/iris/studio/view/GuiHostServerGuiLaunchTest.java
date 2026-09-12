package art.arcane.iris.studio.view;

import art.arcane.iris.configuration.IrisSettings;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.awt.GraphicsEnvironment;

import static org.junit.Assert.assertEquals;

public class GuiHostServerGuiLaunchTest {
    private IrisSettings previousSettings;
    private boolean previousSuppressed;

    @Before
    public void before() {
        previousSettings = IrisSettings.settings;
        previousSuppressed = GuiHost.isDesktopSuppressed();
        IrisSettings.settings = new IrisSettings();
    }

    @After
    public void after() {
        GuiHost.suppressDesktop(previousSuppressed);
        IrisSettings.settings = previousSettings;
    }

    @Test
    public void serverGuiLaunchIsDisabledWhenNotRequested() {
        GuiHost.suppressDesktop(false);
        IrisSettings.settings.getGui().setUseServerLaunchedGuis(true);

        assertEquals(GuiHost.ServerGuiLaunch.DISABLED, GuiHost.serverGuiLaunch(false));
    }

    @Test
    public void serverGuiLaunchIsDisabledWhenSettingIsOff() {
        GuiHost.suppressDesktop(false);
        IrisSettings.settings.getGui().setUseServerLaunchedGuis(false);

        assertEquals(GuiHost.ServerGuiLaunch.DISABLED, GuiHost.serverGuiLaunch(true));
    }

    @Test
    public void serverGuiLaunchIsUnavailableWhenDesktopIsSuppressed() {
        GuiHost.suppressDesktop(true);
        IrisSettings.settings.getGui().setUseServerLaunchedGuis(true);

        assertEquals(GuiHost.ServerGuiLaunch.UNAVAILABLE, GuiHost.serverGuiLaunch(true));
    }

    @Test
    public void serverGuiLaunchOpensOnlyWithADisplayEnvironment() {
        GuiHost.suppressDesktop(false);
        IrisSettings.settings.getGui().setUseServerLaunchedGuis(true);

        GuiHost.ServerGuiLaunch expected = GraphicsEnvironment.isHeadless()
                ? GuiHost.ServerGuiLaunch.UNAVAILABLE
                : GuiHost.ServerGuiLaunch.OPEN;
        assertEquals(expected, GuiHost.serverGuiLaunch(true));
    }
}

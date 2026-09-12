package art.arcane.iris.diagnostics.splash;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;

public class IrisSplashComposerTest {
    @Test
    public void composeInfoUsesCurrentVersionWithoutStaleReleaseTag() {
        String[] info = IrisSplashComposer.composeInfo("4.0.0-26.2", "Paper 26.2", IrisSplashComposer.InfoStyle.PLAIN);

        assertEquals(" Iris, Dimension Engine [4.0]", info[1]);
        assertEquals(" Version: 4.0.0-26.2", info[2]);
        assertFalse(String.join("\n", info).contains("RC.1.1.6"));
    }

    @Test
    public void composeInfoShowsWebsiteAndKeepsSplashHeight() {
        String[] info = IrisSplashComposer.composeInfo("4.0.0-26.2", "Paper 26.2", IrisSplashComposer.InfoStyle.PLAIN);

        assertEquals(11, info.length);
        assertEquals(" By: Volmit Software (Arcane Arts)", info[3]);
        assertEquals(" Web: VolmitSoftware.com", info[4]);
        assertEquals(" Server: Paper 26.2", info[5]);
    }
}

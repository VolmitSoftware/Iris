package art.arcane.iris.diagnostics.splash;

import art.arcane.iris.pack.BuiltInPackUpdates;
import org.junit.Test;

import java.util.List;
import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;

public class IrisSplashComposerTest {
    @Test
    public void packLinesShowUpdatesAndKeepCustomPacks() {
        List<IrisSplashPackScanner.SplashPackMetadata> packs = List.of(
                new IrisSplashPackScanner.SplashPackMetadata("custom", "7"),
                new IrisSplashPackScanner.SplashPackMetadata("overworld", "4009"),
                new IrisSplashPackScanner.SplashPackMetadata("underworld", "1012"));
        assertEquals(List.of("Custom Dimensions: 3", "  custom v7", "  overworld v4009 -> v4010 available",
                        "  underworld v1012", "Update overworld: /iris download pack=overworld overwrite=true"),
                IrisSplashComposer.composePackLines(packs, Map.of(
                        "overworld", new BuiltInPackUpdates.Update("4010"),
                        "underworld", new BuiltInPackUpdates.Update("1012"))));
    }

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

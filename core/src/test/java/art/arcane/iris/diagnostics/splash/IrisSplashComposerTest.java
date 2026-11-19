package art.arcane.iris.diagnostics.splash;

import art.arcane.iris.pack.BuiltInPackUpdates;
import org.junit.Test;

import java.util.List;
import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;

public class IrisSplashComposerTest {
    @Test
    public void updateLinesNameOnlyOutdatedPacks() {
        List<IrisSplashPackScanner.SplashPackMetadata> packs = List.of(
                new IrisSplashPackScanner.SplashPackMetadata("custom", "7"),
                new IrisSplashPackScanner.SplashPackMetadata("overworld", "4009"),
                new IrisSplashPackScanner.SplashPackMetadata("underworld", "1012"));
        assertEquals(List.of(
                        "  Pack overworld v4010 is available. You have v4009.",
                        "  Update it:",
                        "    /iris download pack=overworld overwrite=true"),
                IrisSplashComposer.composeUpdateLines(packs, Map.of(
                        "overworld", new BuiltInPackUpdates.Update("4010"),
                        "underworld", new BuiltInPackUpdates.Update("1012"))).stream()
                        .map(line -> line.replaceAll("§.", ""))
                        .toList());
        assertEquals("custom v7, overworld v4009, underworld v1012", IrisSplashComposer.packSummary(packs));
    }

    @Test
    public void detailRowsFillTheSpareSplashRows() {
        String[] info = IrisSplashComposer.composeInfo("4.0.0-26.2", "Paper 26.2", IrisSplashComposer.InfoStyle.PLAIN,
                List.of(List.of(new IrisSplashComposer.Detail("SIMD", "256-bit"),
                        new IrisSplashComposer.Detail("Injection", "deferred"))));

        assertEquals(11, info.length);
        assertEquals(" SIMD          256-bit   Injection deferred", info[8]);
    }

    @Test
    public void composeInfoUsesCurrentVersionWithoutStaleReleaseTag() {
        String[] info = IrisSplashComposer.composeInfo("4.0.0-26.2", "Paper 26.2", IrisSplashComposer.InfoStyle.PLAIN);

        assertEquals(" Iris, Dimension Engine [4.0]", info[1]);
        assertEquals(" Version       4.0.0-26.2", info[2]);
        assertFalse(String.join("\n", info).contains("RC.1.1.6"));
    }

    @Test
    public void composeInfoShowsWebsiteAndKeepsSplashHeight() {
        String[] info = IrisSplashComposer.composeInfo("4.0.0-26.2", "Paper 26.2", IrisSplashComposer.InfoStyle.PLAIN);

        assertEquals(11, info.length);
        assertEquals(" By            Volmit Software (Arcane Arts)", info[3]);
        assertEquals(" Web           VolmitSoftware.com", info[4]);
        assertEquals(" Server        Paper 26.2", info[5]);
    }
}

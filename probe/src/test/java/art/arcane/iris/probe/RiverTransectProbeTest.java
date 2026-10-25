package art.arcane.iris.probe;

import art.arcane.iris.generation.hydrology.config.IrisHydrology;
import art.arcane.iris.generation.hydrology.plan.HydrologySettings;
import art.arcane.iris.generation.hydrology.plan.WindowKey;
import art.arcane.iris.generation.hydrology.plan.WindowPlan;
import art.arcane.iris.generation.hydrology.raster.WaterRole;
import art.arcane.iris.generation.hydrology.water.Reach;
import art.arcane.iris.generation.hydrology.water.ReachStyle;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.File;
import java.nio.file.Files;
import java.util.Arrays;
import java.util.List;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

public class RiverTransectProbeTest {
    @Rule
    public TemporaryFolder folder = new TemporaryFolder();

    @Test
    public void stationsSitAtTenThirtyFiftySeventyAndNinetyPercentOfTheLength() {
        assertArrayEquals(new int[]{2, 6, 10, 14, 18}, RiverTransectProbe.stations(straight(21, 0f, 100f, 4f)));
        assertArrayEquals(new int[]{1, 1, 1, 1, 1}, RiverTransectProbe.stations(straight(2, 0f, 100f, 4f)));
    }

    @Test
    public void sectionReadsColumnsAcrossTheFlow() {
        Reach reach = straight(11, 0f, 100f, 4f);
        RiverTransectProbe.Section section = RiverTransectProbe.section(reach, 5, (x, z) -> Math.abs(z - 100) <= 2
                ? new RiverTransectProbe.Column(70, 66, 68, WaterRole.CHANNEL)
                : z > 140 ? null : new RiverTransectProbe.Column(70, 70, RiverTransectProbe.NO_WATER, WaterRole.BANK));
        RiverTransectProbe.Column[] columns = section.columns();
        assertEquals(2 * RiverTransectProbe.SECTION_HALF + 1, columns.length);
        assertEquals(0.5D, section.station(), 1e-6D);
        assertEquals(68, columns[RiverTransectProbe.SECTION_HALF].head());
        assertEquals(68, columns[RiverTransectProbe.SECTION_HALF + 2].head());
        assertEquals(RiverTransectProbe.NO_WATER, columns[RiverTransectProbe.SECTION_HALF + 3].head());
        assertEquals(RiverTransectProbe.NO_WATER, columns[RiverTransectProbe.SECTION_HALF - 3].head());
        assertNull(columns[2 * RiverTransectProbe.SECTION_HALF]);
        assertEquals(WaterRole.BANK, columns[0].role());
    }

    @Test
    public void summaryListsReachesPondsAndSections() {
        HydrologySettings settings = HydrologySettings.compile(new IrisHydrology().setEnabled(true), 1L, 40, 0, 320);
        Reach reach = straight(11, 0f, 100f, 4f);
        RiverTransectProbe.Section section = RiverTransectProbe.section(reach, 5,
                (x, z) -> new RiverTransectProbe.Column(70, 66, Math.abs(z - 100) <= 2 ? 68 : RiverTransectProbe.NO_WATER, WaterRole.CHANNEL));
        List<String> summary = RiverTransectProbe.summary(new WindowKey(0, 0),
                new WindowPlan(new WindowKey(0, 0), List.of(reach), List.of(), List.of()), List.of(),
                List.of(section), settings);
        assertEquals("window 0,0 reaches=1 ponds=0 lakes=0 sections=1", summary.get(0));
        assertTrue(summary.get(1), summary.get(1).startsWith("reach 7 tier=stream length=40 from=0,100@70 to=40,100@60"));
        assertEquals("section reach=7 station=50% at=20,100 wetColumns=5 deepest=2", summary.get(2));
    }

    @Test
    public void rendersPlanSectionsAndSummaryForASyntheticWindow() throws Exception {
        try (SyntheticHydrology fixture = new SyntheticHydrology()) {
            HydrologyProbe.Inputs inputs = fixture.inputs();
            HydrologyWindows.WindowRange range = new HydrologyWindows.WindowRange(4, 4, 4, 4);
            HydrologyPlanHarness.Plans plans = new HydrologyPlanHarness.Plans(inputs.settings(), inputs.terrain(), inputs.naturalHeight(), range);
            File output = folder.newFolder("transect");
            RiverTransectProbe.Rendered rendered = RiverTransectProbe.render(plans, new WindowKey(4, 4), inputs.naturalHeight(), range, output);
            assertEquals(plans.window(new WindowKey(4, 4)).plan().reaches().size(), rendered.reaches());
            assertEquals(plans.window(new WindowKey(4, 4)).plan().ponds().size(), rendered.ponds());
            assertTrue("reaches " + rendered.reaches(), rendered.reaches() > 0);
            assertTrue("sections " + rendered.sections(), rendered.sections() > 0);
            BufferedImage plan = ImageIO.read(new File(rendered.directory(), "plan.png"));
            assertEquals(WindowKey.SIZE, plan.getWidth());
            assertEquals(WindowKey.SIZE, plan.getHeight());
            assertTrue(new File(rendered.directory(), "sections.png").isFile());
            List<String> summary = Files.readAllLines(new File(rendered.directory(), "summary.txt").toPath());
            assertTrue(summary.get(0), summary.get(0).startsWith("window 4,4 reaches=" + rendered.reaches() + " ponds=" + rendered.ponds()));
            assertTrue(rendered.line(), rendered.line().startsWith("IRIS_RIVER_TRANSECT version=2 wx=4 wz=4 "));
        }
    }

    private static Reach straight(int points, float x0, float z, float spacing) {
        HydrologySettings settings = HydrologySettings.compile(new IrisHydrology().setEnabled(true), 1L, 40, 0, 320);
        float[] xs = new float[points];
        float[] zs = new float[points];
        int[] grade = new int[points];
        float[] halfWidth = new float[points];
        float[] depth = new float[points];
        ReachStyle[] style = new ReachStyle[points];
        for (int point = 0; point < points; point++) {
            xs[point] = x0 + point * spacing;
            zs[point] = z;
            grade[point] = 70 - point;
            halfWidth[point] = 2f;
            depth[point] = 1f;
        }
        Arrays.fill(style, ReachStyle.RUN);
        return new Reach(7L, settings.tiers().get(0), 0L, xs, zs, grade, halfWidth, depth, style, 1000f);
    }
}

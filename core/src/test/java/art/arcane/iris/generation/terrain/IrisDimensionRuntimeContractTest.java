package art.arcane.iris.generation.terrain;

import art.arcane.iris.pack.value.IrisRange;
import art.arcane.iris.world.IrisEnvironment;

import org.junit.Test;

import static art.arcane.iris.generation.terrain.IrisDimensionTypeOptions.TriState.TRUE;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

public class IrisDimensionRuntimeContractTest {
    @Test
    public void exactTypeAndHeightContractPasses() {
        IrisDimensionRuntimeContract expected = expectedContract();
        IrisDimensionRuntimeContract actual = new IrisDimensionRuntimeContract("iris:overworld", -256, 768, 512);

        expected.requireExact("test world", actual);
        expected.requireHeight("test world", -256, 768);
    }

    @Test
    public void tallPackOnVanillaHeightWorldIsRejected() {
        IrisDimensionRuntimeContract expected = expectedContract();
        IrisDimensionRuntimeContract actual = new IrisDimensionRuntimeContract("minecraft:overworld", -64, 384, 384);

        try {
            expected.requireExact("Bukkit world 'world'", actual);
            fail("Vanilla height world must be rejected");
        } catch (IrisDimensionContractException e) {
            assertTrue(e.getMessage().contains("Bukkit world 'world'"));
            assertTrue(e.getMessage().contains("iris:overworld"));
            assertTrue(e.getMessage().contains("-256..512"));
            assertTrue(e.getMessage().contains("minecraft:overworld"));
            assertTrue(e.getMessage().contains("-64..320"));
            assertTrue(e.getMessage().contains("Generation was refused"));
        }
    }

    @Test
    public void wrongIrisTypeKeyIsRejectedEvenWhenHeightMatches() {
        IrisDimensionRuntimeContract expected = expectedContract();
        IrisDimensionRuntimeContract actual = new IrisDimensionRuntimeContract("iris:other", -256, 768, 512);

        try {
            expected.requireExact("test world", actual);
            fail("Wrong Iris dimension type must be rejected");
        } catch (IrisDimensionContractException e) {
            assertTrue(e.getMessage().contains("iris:other"));
        }
    }

    @Test
    public void logicalHeightMismatchIsRejected() {
        IrisDimensionRuntimeContract expected = expectedContract();
        IrisDimensionRuntimeContract actual = new IrisDimensionRuntimeContract("iris:overworld", -256, 768, 384);

        try {
            expected.requireExact("test world", actual);
            fail("Wrong logical height must be rejected");
        } catch (IrisDimensionContractException e) {
            assertTrue(e.getMessage().contains("logical height 384"));
        }
    }

    @Test
    public void hotloadMismatchReportsReplacementAgainstLiveRuntime() {
        IrisDimension active = new IrisDimension();
        active.setLoadKey("overworld");
        active.setDimensionHeight(new IrisRange(-256, 512));
        active.setLogicalHeight(512);
        IrisDimension replacement = new IrisDimension();
        replacement.setLoadKey("overworld");
        replacement.setDimensionHeight(new IrisRange(-384, 640));
        replacement.setLogicalHeight(640);

        try {
            IrisDimensionRuntimeContract.requireHotloadCompatible("Studio world", active, replacement, "iris");
            fail("A hotload cannot replace the live vertical contract");
        } catch (IrisDimensionContractException e) {
            assertTrue(e.getMessage().contains("requires Iris dimension type iris:overworld with range -384..640"));
            assertTrue(e.getMessage().contains("loaded runtime uses iris:overworld with range -256..512"));
        }
    }

    @Test
    public void hotloadRejectsEnvironmentChanges() {
        IrisDimension active = hotloadDimension();
        IrisDimension replacement = hotloadDimension();
        replacement.setEnvironment(IrisEnvironment.NETHER);

        assertHotloadRejected(active, replacement, "active environment is NORMAL");
    }

    @Test
    public void hotloadRejectsCustomEnvironmentEvenWhenItsBaseTypeMatchesNormal() {
        IrisDimension active = hotloadDimension();
        IrisDimension replacement = hotloadDimension();
        replacement.setEnvironment(IrisEnvironment.CUSTOM);

        assertHotloadRejected(active, replacement, "replacement environment is CUSTOM");
    }

    @Test
    public void hotloadRejectsEffectiveDimensionOptionChanges() {
        IrisDimension active = hotloadDimension();
        IrisDimension replacement = hotloadDimension();
        replacement.setDimensionOptions(new IrisDimensionTypeOptions().coordinateScale(2D));

        assertHotloadRejected(active, replacement, "dimensionOptions");
    }

    @Test
    public void hotloadRejectsFullbrightChangesThatAlterTheGeneratedType() {
        IrisDimension active = hotloadDimension();
        IrisDimension replacement = hotloadDimension();
        replacement.setFullbright(true);

        assertHotloadRejected(active, replacement, "fullbright");
    }

    @Test
    public void hotloadAllowsExplicitOptionsEquivalentToTheBaseTemplate() {
        IrisDimension active = hotloadDimension();
        IrisDimension replacement = hotloadDimension();
        replacement.setDimensionOptions(new IrisDimensionTypeOptions().skylight(TRUE));

        IrisDimensionRuntimeContract.requireHotloadCompatible("Studio world", active, replacement, "iris");
    }

    @Test
    public void hotloadAllowsTerrainOnlyDimensionChanges() {
        IrisDimension active = hotloadDimension();
        IrisDimension replacement = hotloadDimension();
        replacement.setFluidHeight(80);

        IrisDimensionRuntimeContract.requireHotloadCompatible("Studio world", active, replacement, "iris");
    }

    private IrisDimensionRuntimeContract expectedContract() {
        IrisDimension dimension = new IrisDimension();
        dimension.setLoadKey("overworld");
        dimension.setDimensionHeight(new IrisRange(-256, 512));
        dimension.setLogicalHeight(512);
        return IrisDimensionRuntimeContract.expected(dimension, "iris");
    }

    private IrisDimension hotloadDimension() {
        IrisDimension dimension = new IrisDimension();
        dimension.setLoadKey("overworld");
        dimension.setEnvironment(IrisEnvironment.NORMAL);
        dimension.setDimensionHeight(new IrisRange(-64, 320));
        dimension.setLogicalHeight(384);
        return dimension;
    }

    private void assertHotloadRejected(IrisDimension active, IrisDimension replacement, String messageFragment) {
        try {
            IrisDimensionRuntimeContract.requireHotloadCompatible("Studio world", active, replacement, "iris");
            fail("A hotload cannot replace the generated dimension type contract");
        } catch (IrisDimensionContractException e) {
            assertTrue(e.getMessage().contains(messageFragment));
        }
    }
}

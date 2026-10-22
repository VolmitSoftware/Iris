package art.arcane.iris.generation.runtime;

import art.arcane.iris.generation.biome.IrisBiome;
import art.arcane.iris.generation.biome.IrisBiomeGeneratorLink;
import art.arcane.iris.generation.noise.IrisGenerator;
import art.arcane.iris.generation.noise.IrisInterpolator;
import art.arcane.volmlib.util.collection.KList;
import art.arcane.volmlib.util.interpolation.InterpolationMethod;
import org.junit.Test;

import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertSame;

public class IrisComplexContributingGroupsTest {
    @Test
    public void groupsWithZeroBoundsInEveryBaseBiomeAreDropped() {
        IrisComplex.GeneratorGroup flat = group(InterpolationMethod.BILINEAR_STARCAST_6, 7D, "default");
        IrisComplex.GeneratorGroup hills = group(InterpolationMethod.BILINEAR_STARCAST_9, 12D, "hills", "cliffs");
        IrisComplex.GeneratorGroup shoreOnly = group(InterpolationMethod.BILINEAR_STARCAST_3, 30D, "ocean");
        IrisBiome plains = biome(link("default", 0, 0), link("hills", -4, 12));
        IrisBiome desert = biome(link("default", 0, 0), link("cliffs", 0, 3));

        IrisComplex.GeneratorGroup[] contributing = IrisComplex.contributingGroups(
                new IrisComplex.GeneratorGroup[]{flat, hills, shoreOnly}, List.of(plains, desert));

        assertEquals(1, contributing.length);
        assertSame(hills, contributing[0]);
    }

    @Test
    public void aNegativeOnlyOrMaximumOnlyLinkKeepsItsGroup() {
        IrisComplex.GeneratorGroup sinks = group(InterpolationMethod.BILINEAR, 16D, "sinks");
        IrisComplex.GeneratorGroup peaks = group(InterpolationMethod.STARCAST_12, 42D, "peaks");

        IrisComplex.GeneratorGroup[] contributing = IrisComplex.contributingGroups(
                new IrisComplex.GeneratorGroup[]{sinks, peaks},
                List.of(biome(link("sinks", -6, 0)), biome(link("peaks", 0, 9))));

        assertEquals(2, contributing.length);
        assertSame(sinks, contributing[0]);
        assertSame(peaks, contributing[1]);
    }

    @Test
    public void linksThatCancelAcrossGeneratorsSumToZeroBoundsAndAreDropped() {
        IrisComplex.GeneratorGroup pair = group(InterpolationMethod.BILINEAR_STARCAST_9, 52D, "rise", "fall");

        IrisComplex.GeneratorGroup[] contributing = IrisComplex.contributingGroups(
                new IrisComplex.GeneratorGroup[]{pair},
                List.of(biome(link("rise", -5, 5), link("fall", 5, -5))));

        assertEquals(0, contributing.length);
    }

    @Test
    public void noBaseBiomesDropsEveryGroup() {
        IrisComplex.GeneratorGroup hills = group(InterpolationMethod.BILINEAR_STARCAST_9, 12D, "hills");

        assertEquals(0, IrisComplex.contributingGroups(new IrisComplex.GeneratorGroup[]{hills}, List.of()).length);
    }

    private static IrisComplex.GeneratorGroup group(InterpolationMethod function, double scale, String... keys) {
        IrisInterpolator interpolator = new IrisInterpolator().setFunction(function).setHorizontalScale(scale);
        IrisGenerator[] generators = new IrisGenerator[keys.length];
        for (int index = 0; index < keys.length; index++) {
            IrisGenerator generator = new IrisGenerator();
            generator.setLoadKey(keys[index]);
            generator.setInterpolator(interpolator);
            generators[index] = generator;
        }
        return new IrisComplex.GeneratorGroup(interpolator, generators);
    }

    private static IrisBiome biome(IrisBiomeGeneratorLink... links) {
        IrisBiome biome = new IrisBiome();
        biome.setGenerators(new KList<>(List.of(links)));
        return biome;
    }

    private static IrisBiomeGeneratorLink link(String generator, int min, int max) {
        return new IrisBiomeGeneratorLink().setGenerator(generator).setMin(min).setMax(max);
    }
}

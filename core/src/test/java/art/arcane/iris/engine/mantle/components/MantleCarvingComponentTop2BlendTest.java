package art.arcane.iris.engine.mantle.components;

import art.arcane.iris.engine.object.IrisCaveProfile;
import org.junit.BeforeClass;
import org.junit.Test;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.Assert.assertEquals;

public class MantleCarvingComponentTop2BlendTest {
    private static Constructor<?> weightedProfileConstructor;
    private static Method limitMethod;
    private static Method expandTileMethod;
    private static Field profileField;
    private static Field columnWeightsField;

    @BeforeClass
    public static void setup() throws Exception {
        Class<?> weightedProfileClass = Class.forName("art.arcane.iris.engine.mantle.components.MantleCarvingComponent$WeightedProfile");
        weightedProfileConstructor = weightedProfileClass.getDeclaredConstructor(IrisCaveProfile.class, double[].class, double.class, Class.forName("art.arcane.iris.engine.object.IrisRange"));
        weightedProfileConstructor.setAccessible(true);
        limitMethod = MantleCarvingComponent.class.getDeclaredMethod("limitAndMergeBlendedProfiles", List.class, int.class, int.class);
        limitMethod.setAccessible(true);
        expandTileMethod = MantleCarvingComponent.class.getDeclaredMethod("expandTileWeightsToColumns", double[].class);
        expandTileMethod.setAccessible(true);
        profileField = weightedProfileClass.getDeclaredField("profile");
        profileField.setAccessible(true);
        columnWeightsField = weightedProfileClass.getDeclaredField("columnWeights");
        columnWeightsField.setAccessible(true);
    }

    @Test
    public void topTwoProfilesAreKeptAndDroppedWeightsAreMergedIntoDominantColumns() throws Exception {
        WeightedInput input = createWeightedProfiles();
        List<?> limited = invokeLimit(input.weightedProfiles(), 2);
        assertEquals(2, limited.size());

        Map<IrisCaveProfile, double[]> byProfile = extractWeightsByProfile(limited);
        IrisCaveProfile first = input.profiles().first();
        IrisCaveProfile second = input.profiles().second();

        assertEquals(1.0D, byProfile.get(first)[1], 0D);
        assertEquals(1.0D, byProfile.get(second)[0], 0D);
    }

    @Test
    public void topTwoMergeIsDeterministicAcrossRuns() throws Exception {
        WeightedInput firstInput = createWeightedProfiles();
        WeightedInput secondInput = createWeightedProfiles();
        List<?> first = invokeLimit(firstInput.weightedProfiles(), 2);
        List<?> second = invokeLimit(secondInput.weightedProfiles(), 2);

        Map<IrisCaveProfile, double[]> firstByProfile = extractWeightsByProfile(first);
        Map<IrisCaveProfile, double[]> secondByProfile = extractWeightsByProfile(second);

        assertEquals(firstByProfile.get(firstInput.profiles().first())[0], secondByProfile.get(secondInput.profiles().first())[0], 0D);
        assertEquals(firstByProfile.get(firstInput.profiles().first())[1], secondByProfile.get(secondInput.profiles().first())[1], 0D);
        assertEquals(firstByProfile.get(firstInput.profiles().second())[0], secondByProfile.get(secondInput.profiles().second())[0], 0D);
        assertEquals(firstByProfile.get(firstInput.profiles().second())[1], secondByProfile.get(secondInput.profiles().second())[1], 0D);
    }

    @Test
    public void tileWeightsExpandIntoFourColumnsPerTile() throws Exception {
        double[] tileWeights = new double[64];
        tileWeights[0] = 0.42D;
        tileWeights[9] = 0.73D;
        double[] expanded = invokeExpand(tileWeights);

        assertEquals(0.42D, expanded[(0 << 4) | 0], 0D);
        assertEquals(0.42D, expanded[(0 << 4) | 1], 0D);
        assertEquals(0.42D, expanded[(1 << 4) | 0], 0D);
        assertEquals(0.42D, expanded[(1 << 4) | 1], 0D);

        assertEquals(0.73D, expanded[(2 << 4) | 2], 0D);
        assertEquals(0.73D, expanded[(2 << 4) | 3], 0D);
        assertEquals(0.73D, expanded[(3 << 4) | 2], 0D);
        assertEquals(0.73D, expanded[(3 << 4) | 3], 0D);
    }

    private WeightedInput createWeightedProfiles() throws Exception {
        IrisCaveProfile first = new IrisCaveProfile().setEnabled(true).setBaseWeight(1.31D);
        IrisCaveProfile second = new IrisCaveProfile().setEnabled(true).setBaseWeight(1.17D);
        IrisCaveProfile third = new IrisCaveProfile().setEnabled(true).setBaseWeight(0.93D);
        Profiles profiles = new Profiles(first, second, third);

        double[] firstWeights = new double[64];
        firstWeights[0] = 0.2D;
        firstWeights[1] = 0.8D;

        double[] secondWeights = new double[64];
        secondWeights[0] = 0.7D;
        secondWeights[1] = 0.1D;

        double[] thirdWeights = new double[64];
        thirdWeights[0] = 0.3D;
        thirdWeights[1] = 0.4D;

        List<Object> weighted = new ArrayList<>();
        weighted.add(weightedProfileConstructor.newInstance(first, firstWeights, average(firstWeights), null));
        weighted.add(weightedProfileConstructor.newInstance(second, secondWeights, average(secondWeights), null));
        weighted.add(weightedProfileConstructor.newInstance(third, thirdWeights, average(thirdWeights), null));
        return new WeightedInput(weighted, profiles);
    }

    private List<?> invokeLimit(List<Object> weightedProfiles, int limit) throws Exception {
        return (List<?>) limitMethod.invoke(null, weightedProfiles, limit, 64);
    }

    private double[] invokeExpand(double[] tileWeights) throws Exception {
        return (double[]) expandTileMethod.invoke(null, (Object) tileWeights);
    }

    private Map<IrisCaveProfile, double[]> extractWeightsByProfile(List<?> weightedProfiles) throws Exception {
        Map<IrisCaveProfile, double[]> byProfile = new IdentityHashMap<>();
        for (Object weightedProfile : weightedProfiles) {
            IrisCaveProfile profile = (IrisCaveProfile) profileField.get(weightedProfile);
            double[] weights = (double[]) columnWeightsField.get(weightedProfile);
            byProfile.put(profile, weights);
        }
        return byProfile;
    }

    private double average(double[] weights) {
        double total = 0D;
        for (double weight : weights) {
            total += weight;
        }
        return total / weights.length;
    }

    private record Profiles(IrisCaveProfile first, IrisCaveProfile second, IrisCaveProfile third) {
    }

    private record WeightedInput(List<Object> weightedProfiles, Profiles profiles) {
    }
}

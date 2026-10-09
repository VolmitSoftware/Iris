package art.arcane.iris.engine.mantle.components;

import art.arcane.iris.builtin.carve.CaveProfile;
import art.arcane.iris.engine.object.TestSettings;
import art.arcane.iris.engine.object.value.IrisRange;
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

public class CaveCarvingTop2BlendTest {
    private static Constructor<?> weightedProfileConstructor;
    private static Method limitMethod;
    private static Field profileField;
    private static Field columnWeightsField;

    @BeforeClass
    public static void setup() throws Exception {
        Class<?> weightedProfileClass = Class.forName("art.arcane.iris.engine.mantle.components.CaveCarving$WeightedProfile");
        weightedProfileConstructor = weightedProfileClass.getDeclaredConstructor(CaveProfile.class, double[].class, double.class, Class.forName("art.arcane.iris.engine.object.value.IrisRange"), int.class);
        weightedProfileConstructor.setAccessible(true);
        limitMethod = CaveCarving.class.getDeclaredMethod("limitAndMergeBlendedProfiles", List.class, int.class, int.class);
        limitMethod.setAccessible(true);
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

        Map<CaveProfile, double[]> byProfile = extractWeightsByProfile(limited);
        CaveProfile first = input.profiles().first();
        CaveProfile second = input.profiles().second();

        assertEquals(1.0D, byProfile.get(first)[1], 0D);
        assertEquals(1.0D, byProfile.get(second)[0], 0D);
    }

    @Test
    public void topTwoMergeIsDeterministicAcrossRuns() throws Exception {
        WeightedInput firstInput = createWeightedProfiles();
        WeightedInput secondInput = createWeightedProfiles();
        List<?> first = invokeLimit(firstInput.weightedProfiles(), 2);
        List<?> second = invokeLimit(secondInput.weightedProfiles(), 2);

        Map<CaveProfile, double[]> firstByProfile = extractWeightsByProfile(first);
        Map<CaveProfile, double[]> secondByProfile = extractWeightsByProfile(second);

        assertEquals(firstByProfile.get(firstInput.profiles().first())[0], secondByProfile.get(secondInput.profiles().first())[0], 0D);
        assertEquals(firstByProfile.get(firstInput.profiles().first())[1], secondByProfile.get(secondInput.profiles().first())[1], 0D);
        assertEquals(firstByProfile.get(firstInput.profiles().second())[0], secondByProfile.get(secondInput.profiles().second())[0], 0D);
        assertEquals(firstByProfile.get(firstInput.profiles().second())[1], secondByProfile.get(secondInput.profiles().second())[1], 0D);
    }

    @Test
    public void topTwoMergePreservesIndependentWeightsAtHighColumnIndexes() throws Exception {
        WeightedInput input = createWeightedProfiles();
        List<?> limited = invokeLimit(input.weightedProfiles(), 2);

        Map<CaveProfile, double[]> byProfile = extractWeightsByProfile(limited);
        CaveProfile first = input.profiles().first();
        CaveProfile second = input.profiles().second();

        assertEquals(1.0D, byProfile.get(first)[255], 0D);
        assertEquals(1.0D, byProfile.get(second)[254], 0D);
    }

    @Test
    public void topTwoTieBreaksBySequenceNotIdentity() throws Exception {
        CaveProfile first = TestSettings.of(CaveProfile.class, "{\"enabled\":true,\"baseWeight\":1.0}");
        CaveProfile second = TestSettings.of(CaveProfile.class, "{\"enabled\":true,\"baseWeight\":1.0}");
        CaveProfile third = TestSettings.of(CaveProfile.class, "{\"enabled\":true,\"baseWeight\":1.0}");

        double[] weightsA = new double[256];
        double[] weightsB = new double[256];
        double[] weightsC = new double[256];
        weightsA[0] = 0.5D;
        weightsB[0] = 0.5D;
        weightsC[0] = 0.5D;

        List<Object> weighted = new ArrayList<>();
        weighted.add(weightedProfileConstructor.newInstance(first, weightsA, average(weightsA), null, 0));
        weighted.add(weightedProfileConstructor.newInstance(second, weightsB, average(weightsB), null, 1));
        weighted.add(weightedProfileConstructor.newInstance(third, weightsC, average(weightsC), null, 2));

        List<?> limited = invokeLimit(weighted, 2);
        assertEquals(2, limited.size());

        Map<CaveProfile, double[]> byProfile = extractWeightsByProfile(limited);
        assertEquals(true, byProfile.containsKey(first));
        assertEquals(true, byProfile.containsKey(second));
        assertEquals(false, byProfile.containsKey(third));
    }

    private WeightedInput createWeightedProfiles() throws Exception {
        CaveProfile first = TestSettings.of(CaveProfile.class, "{\"enabled\":true,\"baseWeight\":1.31}");
        CaveProfile second = TestSettings.of(CaveProfile.class, "{\"enabled\":true,\"baseWeight\":1.17}");
        CaveProfile third = TestSettings.of(CaveProfile.class, "{\"enabled\":true,\"baseWeight\":0.93}");
        Profiles profiles = new Profiles(first, second, third);

        double[] firstWeights = new double[256];
        firstWeights[0] = 0.2D;
        firstWeights[1] = 0.8D;
        firstWeights[255] = 0.6D;

        double[] secondWeights = new double[256];
        secondWeights[0] = 0.7D;
        secondWeights[1] = 0.1D;
        secondWeights[254] = 0.7D;

        double[] thirdWeights = new double[256];
        thirdWeights[0] = 0.3D;
        thirdWeights[1] = 0.4D;
        thirdWeights[254] = 0.3D;
        thirdWeights[255] = 0.4D;

        List<Object> weighted = new ArrayList<>();
        weighted.add(weightedProfileConstructor.newInstance(first, firstWeights, average(firstWeights), null, 0));
        weighted.add(weightedProfileConstructor.newInstance(second, secondWeights, average(secondWeights), null, 1));
        weighted.add(weightedProfileConstructor.newInstance(third, thirdWeights, average(thirdWeights), null, 2));
        return new WeightedInput(weighted, profiles);
    }

    private List<?> invokeLimit(List<Object> weightedProfiles, int limit) throws Exception {
        return (List<?>) limitMethod.invoke(null, weightedProfiles, limit, 256);
    }

    private Map<CaveProfile, double[]> extractWeightsByProfile(List<?> weightedProfiles) throws Exception {
        Map<CaveProfile, double[]> byProfile = new IdentityHashMap<>();
        for (Object weightedProfile : weightedProfiles) {
            CaveProfile profile = (CaveProfile) profileField.get(weightedProfile);
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

    private record Profiles(CaveProfile first, CaveProfile second, CaveProfile third) {
    }

    private record WeightedInput(List<Object> weightedProfiles, Profiles profiles) {
    }
}

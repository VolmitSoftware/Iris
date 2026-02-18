package art.arcane.iris.util.project.uniques.features;

import art.arcane.volmlib.util.math.RNG;
import art.arcane.iris.util.project.uniques.UFeature;
import art.arcane.iris.util.project.uniques.UFeatureMeta;
import art.arcane.iris.util.project.uniques.UImage;
import java.util.function.Consumer;

public class UFNOOP implements UFeature {
    @Override
    public void render(UImage image, RNG rng, double t, Consumer<Double> progressor, UFeatureMeta meta) {
    }
}

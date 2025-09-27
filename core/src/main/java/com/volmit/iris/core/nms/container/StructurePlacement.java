package com.volmit.iris.core.nms.container;

import com.google.gson.JsonObject;
import com.volmit.iris.engine.object.IrisJigsawStructurePlacement.SpreadType;
import lombok.*;
import lombok.experimental.Accessors;
import lombok.experimental.SuperBuilder;
import org.apache.commons.math3.fraction.Fraction;

import java.util.List;

@Data
@SuperBuilder
@Accessors(fluent = true, chain = true)
public abstract class StructurePlacement {
    private final int salt;
    private final float frequency;
    private final List<Structure> structures;

    public abstract JsonObject toJson(String structure);

    protected JsonObject createBase(String structure) {
        JsonObject object = new JsonObject();
        object.addProperty("structure", structure);
        object.addProperty("salt", salt);
        return object;
    }

    public int frequencyToSpacing() {
        var frac = new Fraction(Math.max(Math.min(frequency, 1), 0.000000001f));
        return (int) Math.round(Math.sqrt((double) frac.getDenominator() / frac.getNumerator()));
    }

    @Getter
    @Accessors(chain = true, fluent = true)
    @EqualsAndHashCode(callSuper = true)
    @SuperBuilder
    public static class RandomSpread extends StructurePlacement {
        private final int spacing;
        private final int separation;
        private final SpreadType spreadType;

        @Override
        public JsonObject toJson(String structure) {
            JsonObject object = createBase(structure);
            object.addProperty("spacing", Math.max(spacing, frequencyToSpacing()));
            object.addProperty("separation", separation);
            object.addProperty("spreadType", spreadType.name());
            return object;
        }
    }

    @Getter
    @EqualsAndHashCode(callSuper = true)
    @SuperBuilder
    public static class ConcentricRings extends StructurePlacement {
        private final int distance;
        private final int spread;
        private final int count;

        @Override
        public JsonObject toJson(String structure) {
            return null;
        }
    }

    public record Structure(
            int weight,
            String key,
            List<String> tags
    ) {

        public boolean isValid() {
            return weight > 0 && key != null;
        }
    }
}

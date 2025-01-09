package com.volmit.iris.engine.object;

import com.dfsek.paralithic.functions.dynamic.Context;
import com.dfsek.paralithic.functions.dynamic.DynamicFunction;
import com.dfsek.paralithic.node.Statefulness;
import com.volmit.iris.core.loader.IrisData;
import com.volmit.iris.engine.object.annotations.Desc;
import com.volmit.iris.engine.object.annotations.MinNumber;
import com.volmit.iris.engine.object.annotations.Required;
import com.volmit.iris.engine.object.annotations.Snippet;
import com.volmit.iris.util.collection.KMap;
import com.volmit.iris.util.math.RNG;
import lombok.*;
import lombok.experimental.Accessors;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

@Snippet("expression-function")
@Accessors(chain = true)
@NoArgsConstructor
@AllArgsConstructor
@Desc("Represents a function to use in your expression. Do not set the name to x, y, or z, also don't duplicate names.")
@Data
@EqualsAndHashCode(callSuper = false)
public class IrisExpressionFunction implements DynamicFunction {
    @Required
    @Desc("The function to assign this value to. Do not set the name to x, y, or z")
    private String name;

    @Desc("If defined, this variable will use a generator style as it's value")
    private IrisGeneratorStyle styleValue = null;

    @Desc("If defined, iris will use an internal stream from the engine as it's value")
    private IrisEngineStreamType engineStreamValue = null;

    @MinNumber(2)
    @Desc("Number of arguments for the function")
    private int args = 2;

    @Getter(AccessLevel.NONE)
    @Setter(AccessLevel.NONE)
    private transient final KMap<FunctionContext, Provider> cache = new KMap<>();
    private transient IrisData data;

    public boolean isValid() {
        return styleValue != null || engineStreamValue != null;
    }

    @Override
    public int getArgNumber() {
        if (engineStreamValue != null) return 2;
        return Math.max(args, 2);
    }

    @NotNull
    @Override
    public Statefulness statefulness() {
        return Statefulness.STATEFUL;
    }

    @Override
    public double eval(double... doubles) {
        return 0;
    }

    @Override
    public double eval(@Nullable Context raw, double... args) {
        return cache.computeIfAbsent((FunctionContext) raw, context -> {
            assert context != null;
            if (engineStreamValue != null) {
                var stream = engineStreamValue.get(data.getEngine());
                return d -> stream.get(d[0], d[1]);
            }

            if (styleValue != null) {
                return styleValue.create(context.rng, data)::noise;
            }

            return d -> Double.NaN;
        }).eval(args);
    }

    public record FunctionContext(@NonNull RNG rng) implements Context {
        @Override
        public boolean equals(Object o) {
            if (o == null || getClass() != o.getClass()) return false;
            FunctionContext that = (FunctionContext) o;
            return rng.getSeed() == that.rng.getSeed();
        }

        @Override
        public int hashCode() {
            return Long.hashCode(rng.getSeed());
        }
    }

    @FunctionalInterface
    private interface Provider {
        double eval(double... args);
    }
}

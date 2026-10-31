package art.arcane.iris.pack.datapack.export.lower;

import java.util.List;

final class VanillaInterval {
    static final VanillaInterval NAI = new VanillaInterval(Float.NaN, Float.NaN);
    static final VanillaInterval UNKNOWN = new VanillaInterval(Float.NaN, Float.NaN);
    private static final VanillaInterval INFINITE = new VanillaInterval(Float.NEGATIVE_INFINITY, Float.POSITIVE_INFINITY);
    private static final VanillaInterval ZERO_TO_ONE = new VanillaInterval(0.0F, 1.0F);

    private final float min;
    private final float max;

    private VanillaInterval(float min, float max) {
        this.min = min;
        this.max = max;
    }

    static VanillaInterval of(float min, float max) {
        if (Float.isNaN(min) || Float.isNaN(max) || max < min) {
            return UNKNOWN;
        }
        if (min == Float.NEGATIVE_INFINITY && max == Float.POSITIVE_INFINITY) {
            return INFINITE;
        }
        return new VanillaInterval(min, max);
    }

    static VanillaInterval ofExact(float value) {
        return of(value, value);
    }

    static VanillaInterval ofSymmetric(float range) {
        return of(-range, range);
    }

    static VanillaInterval encapsulating(List<VanillaInterval> intervals) {
        float min = Float.POSITIVE_INFINITY;
        float max = Float.NEGATIVE_INFINITY;
        for (VanillaInterval interval : intervals) {
            if (interval == UNKNOWN) {
                return UNKNOWN;
            }
            if (!interval.isNaI()) {
                min = Math.min(interval.min, min);
                max = Math.max(interval.max, max);
            }
        }
        return max < min ? NAI : of(min, max);
    }

    static VanillaInterval encapsulating(float first, float second) {
        if (Float.isNaN(first) && Float.isNaN(second)) {
            return NAI;
        }
        if (Float.isNaN(first)) {
            return ofExact(second);
        }
        return Float.isNaN(second) ? ofExact(first) : of(Math.min(first, second), Math.max(first, second));
    }

    static VanillaInterval add(VanillaInterval left, VanillaInterval right) {
        float min = left.min + right.min;
        float max = left.max + right.max;
        return !Float.isNaN(min) && !Float.isNaN(max) ? of(min, max) : NAI;
    }

    static VanillaInterval sub(VanillaInterval left, VanillaInterval right) {
        float min = left.min - right.max;
        float max = left.max - right.min;
        return !Float.isNaN(min) && !Float.isNaN(max) ? of(min, max) : NAI;
    }

    static VanillaInterval mul(VanillaInterval left, VanillaInterval right) {
        if (left.isNaI() || right.isNaI()) {
            return NAI;
        }
        float minMin = mulBound(left.min, right.min);
        float minMax = mulBound(left.min, right.max);
        float maxMin = mulBound(left.max, right.min);
        float maxMax = mulBound(left.max, right.max);
        return of(Math.min(Math.min(minMin, minMax), Math.min(maxMin, maxMax)), Math.max(Math.max(minMin, minMax), Math.max(maxMin, maxMax)));
    }

    static VanillaInterval div(VanillaInterval left, VanillaInterval right) {
        return mul(left, reciprocal(right));
    }

    static VanillaInterval min(VanillaInterval left, VanillaInterval right) {
        return !left.isNaI() && !right.isNaI() ? of(Math.min(left.min, right.min), Math.min(left.max, right.max)) : NAI;
    }

    static VanillaInterval max(VanillaInterval left, VanillaInterval right) {
        return !left.isNaI() && !right.isNaI() ? of(Math.max(left.min, right.min), Math.max(left.max, right.max)) : NAI;
    }

    static VanillaInterval clamp(VanillaInterval input, float min, float max) {
        if (min > max) {
            return UNKNOWN;
        }
        if (input.isNaI()) {
            return NAI;
        }
        if (input.min >= max) {
            return of(max, max);
        }
        return input.max <= min ? of(min, min) : of(Math.max(input.min, min), Math.min(input.max, max));
    }

    static VanillaInterval abs(VanillaInterval input) {
        if (input.isNaI()) {
            return NAI;
        }
        float max = Math.max(Math.abs(input.min), Math.abs(input.max));
        return input.contains(0.0F) ? of(0.0F, max) : of(Math.min(Math.abs(input.min), Math.abs(input.max)), max);
    }

    static VanillaInterval square(VanillaInterval input) {
        if (input.isNaI()) {
            return NAI;
        }
        float max = Math.max(input.min * input.min, input.max * input.max);
        return input.contains(0.0F) ? of(0.0F, max) : of(Math.min(input.min * input.min, input.max * input.max), max);
    }

    static VanillaInterval pow(VanillaInterval base, VanillaInterval exponent) {
        if (base.isNaI() || exponent.isNaI()) {
            return NAI;
        }
        if (base.min == base.max) {
            return pow(base.min, exponent);
        }
        VanillaInterval result = encapsulating(List.of(pow(base.min, exponent), pow(base.max, exponent)));
        if (base.contains(0.0F)) {
            if (base.max > 0.0F) {
                result = encapsulating(List.of(result, pow(0.0F, exponent)));
            }
            if (base.min < 0.0F) {
                result = encapsulating(List.of(result, pow(-0.0F, exponent)));
            }
        }
        return result;
    }

    static VanillaInterval log(VanillaInterval input) {
        if (input.max < 0.0F) {
            return NAI;
        }
        return mapMonotonic(max(input, ofExact(0.0F)), value -> (float) Math.log(value));
    }

    static VanillaInterval mapMonotonic(VanillaInterval input, FloatOperator operator) {
        if (input.isNaI()) {
            return NAI;
        }
        float mappedMin = operator.apply(input.min);
        float mappedMax = operator.apply(input.max);
        if (Float.isNaN(mappedMin) || Float.isNaN(mappedMax)) {
            return UNKNOWN;
        }
        return of(Math.min(mappedMin, mappedMax), Math.max(mappedMin, mappedMax));
    }

    static VanillaInterval lerp(VanillaInterval alpha, VanillaInterval first, VanillaInterval second) {
        if (alpha.isNaI() || first.isNaI() || second.isNaI()) {
            return NAI;
        }
        return encapsulating(List.of(lerp(alpha, first.min, second.min), lerp(alpha, first.max, second.min), lerp(alpha, first.min, second.max),
                lerp(alpha, first.max, second.max)));
    }

    static VanillaInterval sign(VanillaInterval input) {
        if (input.isNaI()) {
            return NAI;
        }
        if (input.min == input.max) {
            return ofExact(Math.signum(input.min));
        }
        if (!input.contains(0.0F)) {
            return ofExact(input.min > 0.0F ? 1.0F : -1.0F);
        }
        if (input.min == 0.0F) {
            return ZERO_TO_ONE;
        }
        return input.max == 0.0F ? of(-1.0F, 0.0F) : of(-1.0F, 1.0F);
    }

    boolean isNaI() {
        return this == NAI;
    }

    boolean isKnown() {
        return this != UNKNOWN;
    }

    boolean contains(float value) {
        return value >= min && value <= max;
    }

    float min() {
        return min;
    }

    float max() {
        return max;
    }

    private static float mulBound(float left, float right) {
        return left != 0.0F && right != 0.0F ? left * right : 0.0F;
    }

    private static VanillaInterval reciprocal(VanillaInterval input) {
        if (input.isNaI() || input.min == 0.0F && input.max == 0.0F) {
            return NAI;
        }
        if (!input.contains(0.0F)) {
            return of(1.0F / input.max, 1.0F / input.min);
        }
        if (input.max == 0.0F) {
            return of(Float.NEGATIVE_INFINITY, 1.0F / input.min);
        }
        return input.min == 0.0F ? of(1.0F / input.max, Float.POSITIVE_INFINITY) : INFINITE;
    }

    private static VanillaInterval encapsulating(VanillaInterval first, float second) {
        if (Float.isNaN(second)) {
            return first;
        }
        return first.isNaI() ? ofExact(second) : of(Math.min(first.min, second), Math.max(first.max, second));
    }

    private static VanillaInterval pow(float base, VanillaInterval exponent) {
        if (Float.isNaN(base) || exponent.isNaI()) {
            return NAI;
        }
        if (exponent.min == exponent.max) {
            float value = (float) Math.pow(base, exponent.min);
            return Float.isNaN(value) ? NAI : ofExact(value);
        }
        if (base == 0.0F) {
            return mul(powZeroBase(exponent), ofExact(Math.copySign(1.0F, base)));
        }
        if (base == 1.0F) {
            return ofExact(1.0F);
        }
        return base > 0.0F ? powPositiveBase(base, exponent) : powNegativeBase(base, exponent);
    }

    private static VanillaInterval powPositiveBase(float base, VanillaInterval exponent) {
        if (Float.isFinite(exponent.min) && Float.isFinite(exponent.max)) {
            return encapsulating((float) Math.pow(base, exponent.min), (float) Math.pow(base, exponent.max));
        }
        return powInfiniteExponent(base, exponent);
    }

    private static VanillaInterval powZeroBase(VanillaInterval exponent) {
        if (!exponent.contains(0.0F)) {
            return exponent.max < 0.0F ? ofExact(Float.POSITIVE_INFINITY) : ofExact(0.0F);
        }
        if (exponent.max == 0.0F) {
            return of(1.0F, Float.POSITIVE_INFINITY);
        }
        return exponent.min == 0.0F ? ZERO_TO_ONE : of(0.0F, Float.POSITIVE_INFINITY);
    }

    private static VanillaInterval powInfiniteExponent(float base, VanillaInterval exponent) {
        if (Float.isInfinite(exponent.min) && Float.isInfinite(exponent.max)) {
            return of(0.0F, Float.POSITIVE_INFINITY);
        }
        if (Float.isInfinite(exponent.min)) {
            return base < 1.0F ? of((float) Math.pow(base, exponent.max), Float.POSITIVE_INFINITY) : of(0.0F, (float) Math.pow(base, exponent.max));
        }
        return base < 1.0F ? of(0.0F, (float) Math.pow(base, exponent.min)) : of((float) Math.pow(base, exponent.min), Float.POSITIVE_INFINITY);
    }

    private static VanillaInterval powNegativeBase(float base, VanillaInterval exponent) {
        float exponentMinInt = (float) Math.ceil(exponent.min);
        float exponentMaxInt = (float) Math.floor(exponent.max);
        if (exponentMaxInt < exponentMinInt) {
            return NAI;
        }
        float baseToMinInt = (float) Math.pow(base, exponentMinInt);
        float baseToMaxInt = (float) Math.pow(base, exponentMaxInt);
        VanillaInterval result = encapsulating(baseToMinInt, baseToMaxInt);
        if (Float.isInfinite(exponentMinInt)) {
            result = encapsulating(result, -baseToMinInt);
        } else if (exponentMinInt + 1.0F < exponentMaxInt) {
            result = encapsulating(result, (float) Math.pow(base, exponentMinInt + 1.0F));
        }
        if (Float.isInfinite(exponentMaxInt)) {
            result = encapsulating(result, -baseToMaxInt);
        } else if (exponentMaxInt - 1.0F > exponentMinInt) {
            result = encapsulating(result, (float) Math.pow(base, exponentMaxInt - 1.0F));
        }
        return result;
    }

    private static VanillaInterval lerp(VanillaInterval alpha, float first, float second) {
        if (Float.isFinite(first) && Float.isFinite(second)) {
            return encapsulating(first + mulBound(alpha.min, second - first), first + mulBound(alpha.max, second - first));
        }
        if (first == second) {
            return ofExact(first);
        }
        float newMin = lerpInfiniteBound(alpha.min, first, second);
        float newMax = lerpInfiniteBound(alpha.max, first, second);
        return !Float.isNaN(newMin) && !Float.isNaN(newMax) ? encapsulating(newMin, newMax) : NAI;
    }

    private static float lerpInfiniteBound(float alpha, float first, float second) {
        float firstPart = mulBound(1.0F - alpha, first);
        float secondPart = mulBound(alpha, second);
        if (!Float.isInfinite(firstPart) || !Float.isInfinite(secondPart)) {
            return firstPart + secondPart;
        }
        if (alpha <= 0.0F) {
            return second > first ? Float.NEGATIVE_INFINITY : Float.POSITIVE_INFINITY;
        }
        if (alpha >= 1.0F) {
            return second > first ? Float.POSITIVE_INFINITY : Float.NEGATIVE_INFINITY;
        }
        return Float.NaN;
    }

    @FunctionalInterface
    interface FloatOperator {
        float apply(float value);
    }
}

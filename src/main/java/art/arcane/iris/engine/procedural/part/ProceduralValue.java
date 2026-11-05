/*
 * Iris is a World Generator for Minecraft Bukkit Servers
 * Copyright (c) 2022 Arcane Arts (Volmit Software)
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */
package art.arcane.iris.engine.procedural.part;

import art.arcane.volmlib.util.math.RNG;
import com.volmit.config.Desc;
import com.volmit.config.JsonForms;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * A number a pack author may write as a constant ({@code 5}), a random range ({@code [3, 8]} or
 * {@code {"min": 3, "max": 8}}), a curve of keyframes ({@code [[0, 1], [0.6, 0.8], [1, 0.2]]}) or a named curve
 * ({@code {"shape": "power", "exponent": 3, "from": 0, "to": 1}}). Each field says what a curve runs over.
 */
@Desc("A number, a [min, max] range rolled once per use, or a curve: [[t, value], ...] keyframes or {\"shape\": ..., \"from\": ..., \"to\": ...}.")
@JsonForms(ProceduralValue.Forms.class)
public final class ProceduralValue {
    private final Object written;
    private final Form form;

    private ProceduralValue(Object written, Form form) {
        this.written = written;
        this.form = form;
    }

    public static ProceduralValue constant(double value) {
        return new ProceduralValue(value, new Constant(value));
    }

    public static ProceduralValue range(double min, double max) {
        return new ProceduralValue(List.of(min, max), new Range(min, max));
    }

    /** This value with any range rolled once, so a curve or constant is all that is left. */
    public ProceduralValue fix(RNG rng) {
        if (form instanceof Range range) {
            return constant(range.roll(rng));
        }
        return this;
    }

    /** The value at {@code t}, from 0 to 1; a range gives its middle, so fix it first to roll it. */
    public double at(double t) {
        return form.at(Math.max(0, Math.min(1, t)));
    }

    /** The value at {@code t}, with a range rolled with {@code rng}. */
    public double sample(RNG rng, double t) {
        return fix(rng).at(t);
    }

    public boolean isCurve() {
        return form instanceof Keyframes || form instanceof Shape;
    }

    /** A short reading of the value for labels: {@code 12}, {@code 4–9} or {@code curve}. */
    public String summary() {
        return switch (form) {
            case Constant constant -> shortNumber(constant.value());
            case Range range -> shortNumber(range.min()) + "–" + shortNumber(range.max());
            case Keyframes ignored -> "curve";
            case Shape ignored -> "curve";
        };
    }

    private static String shortNumber(double value) {
        return value == Math.rint(value) ? Long.toString((long) value) : String.format(Locale.ROOT, "%.4g", value).replaceAll("\\.?0+$", "");
    }

    @Override
    public String toString() {
        return written.toString();
    }

    private sealed interface Form permits Constant, Range, Keyframes, Shape {
        double at(double t);
    }

    private record Constant(double value) implements Form {
        @Override
        public double at(double t) {
            return value;
        }
    }

    private record Range(double min, double max) implements Form {
        double roll(RNG rng) {
            return min == max ? min : rng.d(Math.min(min, max), Math.max(min, max));
        }

        @Override
        public double at(double t) {
            return (min + max) / 2;
        }
    }

    private record Keyframes(double[] times, double[] values) implements Form {
        @Override
        public double at(double t) {
            if (t <= times[0]) {
                return values[0];
            }
            for (int i = 1; i < times.length; i++) {
                if (t <= times[i]) {
                    double span = times[i] - times[i - 1];
                    double blend = span <= 0 ? 1 : (t - times[i - 1]) / span;
                    return values[i - 1] + (values[i] - values[i - 1]) * blend;
                }
            }
            return values[values.length - 1];
        }
    }

    private record Shape(CurveShape shape, double from, double to, Map<?, ?> parameters) implements Form {
        @Override
        public double at(double t) {
            return from + (to - from) * shape.at(t, parameters);
        }
    }

    /** The named curves, each running from 0 at t = 0 toward 1, stretched between a value's from and to. */
    public enum CurveShape {
        LINEAR(List.of()) {
            @Override
            double at(double t, Map<?, ?> p) {
                return t;
            }
        },
        SMOOTH(List.of()) {
            @Override
            double at(double t, Map<?, ?> p) {
                return t * t * (3 - 2 * t);
            }
        },
        POWER(List.of(new Parameter("exponent", 2))) {
            @Override
            double at(double t, Map<?, ?> p) {
                return Math.pow(t, parameter(p, "exponent"));
            }
        },
        SIGMOID(List.of(new Parameter("steepness", 10), new Parameter("mid", 0.5))) {
            @Override
            double at(double t, Map<?, ?> p) {
                return 1 / (1 + Math.exp(-parameter(p, "steepness") * (t - parameter(p, "mid"))));
            }
        },
        BELL(List.of(new Parameter("mid", 0.5), new Parameter("width", 0.15))) {
            @Override
            double at(double t, Map<?, ?> p) {
                double spread = Math.max(1e-6, parameter(p, "width"));
                return Math.exp(-0.5 * Math.pow((t - parameter(p, "mid")) / spread, 2));
            }
        },
        PERIODIC(List.of(new Parameter("periods", 4), new Parameter("sharpness", 8))) {
            @Override
            double at(double t, Map<?, ?> p) {
                double wave = Math.max(0, Math.sin(2 * Math.PI * t * parameter(p, "periods")));
                return Math.pow(wave, parameter(p, "sharpness"));
            }
        },
        SINE(List.of(new Parameter("periods", 1))) {
            @Override
            double at(double t, Map<?, ?> p) {
                return 0.5 + 0.5 * Math.sin(2 * Math.PI * t * parameter(p, "periods"));
            }
        };

        private final List<Parameter> parameters;

        CurveShape(List<Parameter> parameters) {
            this.parameters = parameters;
        }

        abstract double at(double t, Map<?, ?> parameters);

        /** What shapes this curve beyond its from and to, each with the value it has unless the curve sets it. */
        public List<Parameter> parameters() {
            return parameters;
        }

        /** As a value writes it, like {@code power}. */
        public String key() {
            return name().toLowerCase(Locale.ROOT);
        }

        final double parameter(Map<?, ?> written, String name) {
            if (written.containsKey(name)) {
                return Forms.number(written.get(name), name);
            }
            return parameters.stream().filter(parameter -> parameter.name().equals(name)).findFirst().orElseThrow().fallback();
        }

        public record Parameter(String name, double fallback) {
        }
    }

    /** Reads the forms a pack writes and writes a value back exactly as it was read. */
    static final class Forms implements JsonForms.Codec<ProceduralValue> {
        private static final String SHAPE = "shape";
        private static final String MIN = "min";
        private static final String MAX = "max";
        private static final String FROM = "from";
        private static final String TO = "to";
        private static final double DEFAULT_FROM = 0;
        private static final double DEFAULT_TO = 1;

        @Override
        public ProceduralValue read(Object json) {
            return new ProceduralValue(json, formOf(json));
        }

        @Override
        public Object write(ProceduralValue value) {
            return value.written;
        }

        private static Form formOf(Object json) {
            return switch (json) {
                case Number number -> new Constant(number.doubleValue());
                case List<?> list -> listForm(list);
                case Map<?, ?> object when object.containsKey(SHAPE) -> shapeForm(object);
                case Map<?, ?> object when object.containsKey(MIN) && object.containsKey(MAX) ->
                        new Range(number(object.get(MIN), MIN), number(object.get(MAX), MAX));
                case Map<?, ?> object -> throw new IllegalArgumentException("A value needs min and max, or a shape: " + object);
                default -> throw new IllegalArgumentException("A value is a number, a [min, max] range or a curve, not " + json);
            };
        }

        private static Form listForm(List<?> list) {
            if (list.isEmpty()) {
                throw new IllegalArgumentException("A value can not be an empty list");
            }
            if (list.getFirst() instanceof Number) {
                if (list.size() != 2) {
                    throw new IllegalArgumentException("A range is written [min, max]: " + list);
                }
                return new Range(number(list.get(0), MIN), number(list.get(1), MAX));
            }
            return keyframes(list);
        }

        private static Form keyframes(List<?> list) {
            List<double[]> keys = new ArrayList<>();
            for (Object key : list) {
                if (!(key instanceof List<?> pair) || pair.size() != 2) {
                    throw new IllegalArgumentException("A keyframe is written [t, value]: " + key);
                }
                keys.add(new double[]{number(pair.get(0), "t"), number(pair.get(1), "value")});
            }
            keys.sort((a, b) -> Double.compare(a[0], b[0]));
            double[] times = new double[keys.size()];
            double[] values = new double[keys.size()];
            for (int i = 0; i < keys.size(); i++) {
                times[i] = keys.get(i)[0];
                values[i] = keys.get(i)[1];
            }
            return new Keyframes(times, values);
        }

        private static Form shapeForm(Map<?, ?> object) {
            String name = String.valueOf(object.get(SHAPE)).toUpperCase(Locale.ROOT);
            CurveShape shape;
            try {
                shape = CurveShape.valueOf(name);
            } catch (IllegalArgumentException unknown) {
                throw new IllegalArgumentException("Unknown curve shape " + name.toLowerCase(Locale.ROOT));
            }
            double from = object.containsKey(FROM) ? number(object.get(FROM), FROM) : DEFAULT_FROM;
            double to = object.containsKey(TO) ? number(object.get(TO), TO) : DEFAULT_TO;
            return new Shape(shape, from, to, object);
        }

        static double number(Object written, String name) {
            if (written instanceof Number number) {
                return number.doubleValue();
            }
            throw new IllegalArgumentException(name + " needs a number, not " + written);
        }
    }
}

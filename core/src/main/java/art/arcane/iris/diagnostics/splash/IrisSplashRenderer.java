package art.arcane.iris.diagnostics.splash;

import java.util.concurrent.ThreadLocalRandom;

public final class IrisSplashRenderer {
    private static final int SPLASH_WIDTH = 53;
    private static final int SPLASH_HEIGHT = 11;
    private static final double SPLASH_CENTER_X = 26.0;
    private static final double SPLASH_CENTER_Y = 5.0;
    private static final double SPLASH_ASPECT = 2.15;
    private static final double SPLASH_EYE_A = 5.6;
    private static final double SPLASH_EYE_B = 4.6;
    private static final SplashTone PLAIN_TONE = (glyph) -> "";

    private IrisSplashRenderer() {
    }

    public static String[] renderPlain() {
        return render(PLAIN_TONE);
    }

    public static String[] render(SplashTone tone) {
        SplashTone splashTone = tone == null ? PLAIN_TONE : tone;
        ThreadLocalRandom random = ThreadLocalRandom.current();
        double headThickness = 3.7 + random.nextDouble() * 0.6;
        double tipReach = 19.0 + random.nextDouble() * 4.0;
        double notchX = 3.5 + random.nextDouble() * 1.5;
        double[][][] spines = splashFinSpines(tipReach);
        double[][][] voids = splashNotchVoids(notchX);
        String[] lines = new String[SPLASH_HEIGHT];
        for (int y = 0; y < SPLASH_HEIGHT; y++) {
            StringBuilder line = new StringBuilder();
            String activeTone = null;
            for (int x = 0; x < SPLASH_WIDTH; x++) {
                char glyph = splashEyeGlyph(x, y);
                if (glyph == 0) {
                    glyph = splashFinGlyph(x, y, spines, voids, headThickness);
                }
                if (glyph == 0 || glyph == ' ') {
                    line.append(' ');
                    continue;
                }
                String glyphTone = splashTone.tone(glyph);
                if (!glyphTone.isEmpty() && !glyphTone.equals(activeTone)) {
                    line.append(glyphTone);
                    activeTone = glyphTone;
                }
                line.append(glyph);
            }
            lines[y] = line.toString();
        }
        return lines;
    }

    private static char splashEyeGlyph(int x, int y) {
        if (y == 5 && Math.abs(x - 26) <= 1) {
            if (x == 25) {
                return '(';
            }
            if (x == 27) {
                return ')';
            }
            return ' ';
        }
        double radius = splashEyeRadius(x, y);
        if (radius <= 0.50) {
            return '@';
        }
        if (radius <= 0.72) {
            return '%';
        }
        if (radius <= 0.88) {
            return '*';
        }
        return 0;
    }

    private static char splashFinGlyph(int x, int y, double[][][] spines, double[][][] voids, double headThickness) {
        if (splashEyeRadius(x, y) <= 1.26) {
            return 0;
        }
        double voidDistance = Double.MAX_VALUE;
        for (double[][] notch : voids) {
            for (double[] sample : notch) {
                double voidRadius = Math.max(0.25, 0.95 - 0.65 * sample[2]);
                double dx = (x - sample[0]) / SPLASH_ASPECT;
                double dy = y - sample[1];
                double distance = Math.sqrt(dx * dx + dy * dy) / voidRadius;
                if (distance < voidDistance) {
                    voidDistance = distance;
                }
            }
        }
        if (voidDistance < 1.0) {
            return 0;
        }
        double best = Double.MAX_VALUE;
        double bestT = 0.0;
        for (double[][] spine : spines) {
            for (int i = 0; i < spine.length; i++) {
                double t = i / (double) (spine.length - 1);
                double thickness = headThickness * Math.pow(1.0 - t, 1.3) + 0.6;
                double dx = (x - spine[i][0]) / SPLASH_ASPECT;
                double dy = y - spine[i][1];
                double distance = Math.sqrt(dx * dx + dy * dy) / thickness;
                if (distance < best) {
                    best = distance;
                    bestT = t;
                }
            }
        }
        double shade = best
                + Math.max(0.0, 3.0 - Math.min(x, SPLASH_WIDTH - 1 - x)) * 0.12
                + Math.max(0.0, 0.10 - bestT) * 1.5
                + Math.max(0.0, 1.30 - voidDistance) * 0.15;
        if (shade > 1.0) {
            return 0;
        }
        int level = shade <= 0.70 ? 0 : shade <= 0.90 ? 1 : 2;
        if (bestT > 0.97) {
            level += 2;
        } else if (bestT > 0.88) {
            level += 1;
        }
        return switch (Math.min(level, 2)) {
            case 0 -> '#';
            case 1 -> '=';
            default -> '-';
        };
    }

    private static double splashEyeRadius(int x, int y) {
        double dx = (x - SPLASH_CENTER_X) / SPLASH_ASPECT;
        double dy = y - SPLASH_CENTER_Y;
        double nx = dx / SPLASH_EYE_A;
        double ny = dy / SPLASH_EYE_B;
        return Math.sqrt(nx * nx + ny * ny);
    }

    private static double[][][] splashNotchVoids(double notchX) {
        double[][] control = {{notchX + 0.8, 12.0}, {notchX, 9.6}, {notchX - 1.4, 7.8}};
        int samples = 31;
        double[][] left = new double[samples][3];
        double[][] right = new double[samples][3];
        for (int i = 0; i < samples; i++) {
            double t = i / (double) (samples - 1);
            double u = 1.0 - t;
            double bx = u * u * control[0][0] + 2.0 * u * t * control[1][0] + t * t * control[2][0];
            double by = u * u * control[0][1] + 2.0 * u * t * control[1][1] + t * t * control[2][1];
            left[i][0] = bx;
            left[i][1] = by;
            left[i][2] = t;
            right[i][0] = (SPLASH_WIDTH - 1.0) - bx;
            right[i][1] = (SPLASH_HEIGHT - 1.0) - by;
            right[i][2] = t;
        }
        return new double[][][]{left, right};
    }

    private static double[][][] splashFinSpines(double tipReach) {
        double[][] control = {{-1.5, 9.8}, {0.4, 4.4}, {5.2, 1.0}, {tipReach, 0.4}};
        int samples = 61;
        double[][] left = new double[samples][2];
        double[][] right = new double[samples][2];
        for (int i = 0; i < samples; i++) {
            double t = i / (double) (samples - 1);
            double u = 1.0 - t;
            double bx = u * u * u * control[0][0] + 3.0 * u * u * t * control[1][0] + 3.0 * u * t * t * control[2][0] + t * t * t * control[3][0];
            double by = u * u * u * control[0][1] + 3.0 * u * u * t * control[1][1] + 3.0 * u * t * t * control[2][1] + t * t * t * control[3][1];
            left[i][0] = bx;
            left[i][1] = by;
            right[i][0] = (SPLASH_WIDTH - 1.0) - bx;
            right[i][1] = (SPLASH_HEIGHT - 1.0) - by;
        }
        return new double[][][]{left, right};
    }

    public interface SplashTone {
        String tone(char glyph);
    }
}

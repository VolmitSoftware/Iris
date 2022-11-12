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

package com.volmit.iris.util.noise;

public class CloverNoise implements NoiseGenerator {
    private final Noise2D n2;
    private final Noise3D n3;

    public CloverNoise(long seed) {
        n2 = new CloverNoise.Noise2D(seed);
        n3 = new CloverNoise.Noise3D(seed);
    }

    @Override
    public double noise(double x) {
        return n2.noise(x, 0);
    }

    @Override
    public double noise(double x, double z) {
        return n2.noise(x, z);
    }

    @Override
    public double noise(double x, double y, double z) {
        if (z == 0) {
            return n2.noise(x, y);
        }

        return n3.noise(x, y, z);
    }

    /**
     * Java implementation of 2D Clover Noise. See https://github.com/ValgoBoi/clover-noise
     */
    public static class Noise2D {
        private static final long HASH_A = 25214903917L;
        private static final long HASH_C = 11L;
        private static final long HASH_M = 0x1000000000000L;
        private static final double POINT_SPREAD = 0.3;
        private static final double CURL_DX = 0.0001;
        private final long seed;

        /**
         * Constructs a new 2D Clover Noise generator with a specific seed.
         *
         * @param seed The seed for the noise generator.
         */
        public Noise2D(long seed) {
            this.seed = seed;
        }

        /**
         * Constructs a new 2D Clover Noise generator, with the seed automatically set to the system time.
         */
        public Noise2D() {
            this(System.currentTimeMillis());
        }

        private long doHash(long input, long seed) {
            input += seed;

            if (input < 0) {
                input += HASH_M;
            }

            input *= HASH_A;
            input += HASH_C;
            input %= HASH_M;

            return input;
        }

        private double hash(Vector2 position) {
            long hash = doHash(seed, (long) Math.floor(position.getX()));
            hash = doHash(hash, (long) Math.floor(position.getY()));
            hash = doHash(hash, hash * (long) Math.floor(position.getX() + position.getY()));
            if (hash < 0) {
                hash += HASH_M;
            }

            return (double) hash / HASH_M;
        }

        private Vector2 offset(Vector2 position) {
            double hash = hash(position);
            double scale = Math.floor(hash * 50 + 1) / 100;
            Vector2 offset = new Vector2(Math.sin(hash * Math.PI * 100), Math.cos(hash * Math.PI * 100)).mult(scale).add(0.5);
            return position.add(offset.mult(POINT_SPREAD * 2)).add(0.5 - POINT_SPREAD);
        }

        /**
         * Generates 2D Clover Noise at a specific point.
         *
         * @param p The point to generate noise at.
         * @return The value of noise, from 0 to 1.
         */
        public double noise(Vector2 p) {
            Vector2 p_floor = p.floor();

            Vector2 c_11 = offset(p_floor);
            Vector2 c_10 = offset(p_floor.add(0, -1));
            Vector2 c_12 = offset(p_floor.add(0, 1));
            Vector2 c_01 = offset(p_floor.add(-1, 0));
            Vector2 c_21 = offset(p_floor.add(1, 0));

            Vector2 d_p_c11 = p.sub(c_11).yx();
            Vector2 m_p_c11 = d_p_c11.mult(c_11);

            double side_nx = m_p_c11.sub(d_p_c11.mult(c_01)).ymx();
            double side_px = m_p_c11.sub(d_p_c11.mult(c_21)).ymx();

            Vector2 a, c, d;

            if (side_nx < 0 && p.x < c_11.x || side_px > 0 && p.x > c_11.x) {
                double side_py = m_p_c11.sub(d_p_c11.mult(c_12)).ymx();

                if (side_py > 0) {
                    a = c_12;
                    c = c_01;
                    d = new Vector2(-1, 1);
                } else {
                    a = c_21;
                    c = c_12;
                    d = new Vector2(1, 1);
                }
            } else {
                double side_ny = m_p_c11.sub(d_p_c11.mult(c_10)).ymx();

                if (side_ny > 0) {
                    a = c_10;
                    c = c_21;
                    d = new Vector2(1, -1);
                } else {
                    a = c_01;
                    c = c_10;
                    d = new Vector2(-1, -1);
                }
            }

            d = offset(p_floor.add(d));

            Vector2 f = a;
            Vector2 g = c;
            Vector2 h = d;

            Vector2 ac = a.sub(c);
            Vector2 bd = c_11.sub(d);

            if (ac.x * ac.x + ac.y * ac.y < bd.x * bd.x + bd.y * bd.y) {
                Vector2 pa = p.sub(a);

                if (pa.x * ac.y - pa.y * ac.x > 0.) {
                    h = c_11;
                }
            } else {
                Vector2 pb = p.sub(c_11);

                if (pb.x * bd.y - pb.y * bd.x > 0) {
                    f = c_11;
                } else {
                    g = c_11;
                }
            }

            Vector2 bc_v0 = g.sub(f);
            Vector2 bc_v1 = h.sub(f);
            Vector2 bc_v2 = p.sub(f);
            double den = 1 / (bc_v0.x * bc_v1.y - bc_v1.x * bc_v0.y);
            double v = (bc_v2.x * bc_v1.y - bc_v1.x * bc_v2.y) * den;
            double w = (bc_v0.x * bc_v2.y - bc_v2.x * bc_v0.y) * den;
            double u = 1 - v - w;

            v = v * v * v;
            w = w * w * w;
            u = u * u * u;
            double s = 1 / (u + v + w);
            v *= s;
            w *= s;
            u *= s;

            double fv = hash(f.floor());
            double gv = hash(g.floor());
            double hv = hash(h.floor());

            return u * fv + v * gv + w * hv;
        }

        /**
         * Generates 2D Clover Noise at a specific point.
         *
         * @param x The x coordinate of the point.
         * @param y The y coordinate of the point.
         * @return The value of noise, from 0 to 1.
         */
        public double noise(double x, double y) {
            return noise(new Vector2(x, y));
        }

        /**
         * Generates fractal 2D Clover Noise at a specific point.
         *
         * @param p          The point to generate noise at.
         * @param iterations The number of iterations for the fractal noise.
         * @return The value of noise, from 0 to 1.
         */
        public double fractalNoise(Vector2 p, int iterations) {
            double total = 0;
            double weight = 1;
            double div = 0;

            for (int i = 0; i < iterations; i++) {
                total += noise(p.mult(1 / weight)) * weight;
                div += weight;

                weight *= 0.4;
            }

            return total / div;
        }

        /**
         * Generates fractal 2D Clover Noise at a specific point.
         *
         * @param x          The x coordinate of the point.
         * @param y          The y coordinate of the point.
         * @param iterations The number of iterations for the fractal noise.
         * @return The value of noise, from 0 to 1.
         */
        public double fractalNoise(double x, double y, int iterations) {
            return fractalNoise(new Vector2(x, y), iterations);
        }

        /**
         * Generates curl 2D Clover Noise at a specific point.
         *
         * @param p The point to generate noise at.
         * @return The value of curl noise, a normalized 2D vector.
         */
        public Vector2 curlNoise(Vector2 p) {
            double v = noise(p);
            double x = noise(p.add(CURL_DX, 0));
            double y = noise(p.add(0, CURL_DX));

            return new Vector2(v - x, v - y).normalize();
        }

        /**
         * Generates curl 2D Clover Noise at a specific point.
         *
         * @param x The x coordinate of the point.
         * @param y The y coordinate of the point.
         * @return The value of curl noise, a normalized 2D vector.
         */
        public Vector2 curlNoise(double x, double y) {
            return curlNoise(new Vector2(x, y));
        }

        /**
         * Generates fractal curl 2D Clover Noise at a specific point.
         *
         * @param p          The point to generate noise at.
         * @param iterations The number of iterations for the fractal noise.
         * @return The value of curl noise, a normalized 2D vector.
         */
        public Vector2 fractalCurlNoise(Vector2 p, int iterations) {
            double v = fractalNoise(p, iterations);
            double x = fractalNoise(p.add(CURL_DX, 0), iterations);
            double y = fractalNoise(p.add(0, CURL_DX), iterations);

            return new Vector2(v - x, v - y).normalize();
        }

        /**
         * Generates fractal curl 2D Clover Noise at a specific point.
         *
         * @param x          The x coordinate of the point.
         * @param y          The y coordinate of the point.
         * @param iterations The number of iterations for the fractal noise.
         * @return The value of curl noise, a normalized 2D vector.
         */
        public Vector2 fractalCurlNoise(double x, double y, int iterations) {
            return fractalCurlNoise(new Vector2(x, y), iterations);
        }

        /**
         * Generates 2D Frost Noise at a specific point. See https://github.com/ValgoBoi/clover-noise/blob/master/variations/frost.md
         *
         * @param p The point to generate noise at.
         * @return The value of noise, from 0 to 1.
         */
        public double frostNoise(Vector2 p) {
            Vector2 curl_1 = fractalCurlNoise(p, 3).mult(fractalNoise(p, 2) * 0.4 + 0.3);
            Vector2 p_1 = p.add(curl_1);
            Vector2 curl_2 = fractalCurlNoise(p_1, 4).mult(fractalNoise(p_1, 3) * 0.1 + 0.05);
            Vector2 p_2 = p_1.add(curl_2);

            return fractalNoise(p_2, 5) - fractalNoise(p_1, 3) * 0.5 + fractalNoise(p, 2) * 0.3;
        }

        /**
         * Generates 2D Frost Noise at a specific point. See https://github.com/ValgoBoi/clover-noise/blob/master/variations/frost.md
         *
         * @param x The x coordinate of the point.
         * @param y The y coordinate of the point.
         * @return The value of noise, from 0 to 1.
         */
        public double frostNoise(double x, double y) {
            return frostNoise(new Vector2(x, y));
        }

        /**
         * Generates 2D Marble Noise at a specific point. See https://github.com/ValgoBoi/clover-noise/blob/master/variations/marble.md
         *
         * @param p The point to generate noise at.
         * @return The value of noise, from 0 to 1.
         */
        public double marbleNoise(Vector2 p) {
            Vector2 p_2 = p.mult(0.6);
            double d1 = Math.max(1 - Math.abs(fractalNoise(p_2.add(100), 4) - fractalNoise(p_2.add(200), 3)) * 3, 0);
            d1 = d1 * d1 * d1;
            d1 *= fractalNoise(p_2.add(300), 2) * 0.3;

            Vector2 curl_1 = fractalCurlNoise(p.add(400), 3).mult(fractalNoise(p.add(500), 2) * 0.05);

            Vector2 p_3 = p.mult(1.2);
            double d2 = Math.max(1 - Math.abs(fractalNoise(p_3.add(curl_1).add(600), 4) - fractalNoise(p_3.add(curl_1).add(700), 3)) * 2, 0);
            d2 = d2 * d2 * d2;
            d2 *= fractalNoise(p_3.add(800), 2) * 0.5;

            double v = 1 - fractalNoise(p.add(900), 5);
            v = 1 - v * v * v;

            return Math.min(Math.max(v - d1 - d2, 0), 1);
        }

        /**
         * Generates 2D Marble Noise at a specific point. See https://github.com/ValgoBoi/clover-noise/blob/master/variations/marble.md
         *
         * @param x The x coordinate of the point.
         * @param y The y coordinate of the point.
         * @return The value of noise, from 0 to 1.
         */
        public double marbleNoise(double x, double y) {
            return marbleNoise(new Vector2(x, y));
        }
    }

    public static class Noise3D {
        private static final long HASH_A = 25214903917L;
        private static final long HASH_C = 11L;
        private static final long HASH_M = 0x1000000000000L;
        private static final double POINT_SPREAD = 0.2;
        private static final double CURL_DX = 0.0001;
        private final long seed;

        public Noise3D(long seed) {
            this.seed = seed;
        }

        public Noise3D() {
            this(System.currentTimeMillis());
        }

        private long doHash(long input, long seed) {
            input += seed;

            if (input < 0) {
                input += HASH_M;
            }

            input *= HASH_A;
            input += HASH_C;
            input %= HASH_M;

            return input;
        }

        private double hash(Vector3 position) {
            long hash = doHash(seed, (long) Math.floor(position.getX()));
            hash = doHash(hash, (long) Math.floor(position.getY()));
            hash = doHash(hash, (long) Math.floor(position.getZ()));
            hash = doHash(hash, hash * (long) Math.floor(position.getX() + position.getY() + position.getZ()));
            if (hash < 0) {
                hash += HASH_M;
            }

            return (double) hash / HASH_M;
        }

        private Vector3 offset(Vector3 position) {
            double hash = hash(position);
            double theta = hash * Math.PI * 2000;
            double height = (((Math.floor(hash * 1000) + 0.5) / 100) % 1 - 0.5) * Math.PI / 2;
            double layer = Math.floor(hash * 10 + 1) / 10;
            Vector3 offset = new Vector3(Math.sin(theta) * Math.cos(height), Math.sin(height), Math.cos(theta) * Math.cos(height)).mult(layer).add(0.5);
            return position.add(offset.mult(POINT_SPREAD * 2).add(0.5 - POINT_SPREAD));
        }

        private boolean boundary(Vector3 p, Vector3 c_00, Vector3 c_10, Vector3 c_20, Vector3 c_01, Vector3 c_11, Vector3 c_21, Vector3 c_02, Vector3 c_12, Vector3 c_22) {
            Vector2 d_p_c11 = p.yx().sub(c_11.yx());
            Vector2 m_p_c11 = d_p_c11.mult(c_11.xy());

            double side_nx = m_p_c11.sub(d_p_c11.mult(c_01.xy())).ymx();
            double side_px = m_p_c11.sub(d_p_c11.mult(c_21.xy())).ymx();

            Vector3 a, b, c, d;

            if (side_nx < 0 && p.x < c_11.x || side_px > 0 && p.x >= c_11.x) {
                double side_py = m_p_c11.sub(d_p_c11.mult(c_12.xy())).ymx();

                if (side_py > 0.) {
                    a = c_01;
                    b = c_02;
                    c = c_12;
                    d = c_11;
                } else {
                    a = c_11;
                    b = c_12;
                    c = c_22;
                    d = c_21;
                }
            } else {
                double side_ny = m_p_c11.sub(d_p_c11.mult(c_10.xy())).ymx();

                if (side_ny > 0.) {
                    a = c_10;
                    b = c_11;
                    c = c_21;
                    d = c_20;
                } else {
                    a = c_00;
                    b = c_01;
                    c = c_11;
                    d = c_10;
                }
            }

            Vector3 f = a;
            Vector3 g = c;
            Vector3 h = d;

            Vector3 ac = a.sub(c);
            Vector3 pa = p.sub(a);

            if (pa.x * ac.y - pa.y * ac.x > 0) {
                h = b;
            }

            Vector2 bc_v0 = g.xy().sub(f.xy());
            Vector2 bc_v1 = h.xy().sub(f.xy());
            Vector2 bc_v2 = p.xy().sub(f.xy());
            double den = 1 / (bc_v0.x * bc_v1.y - bc_v1.x * bc_v0.y);
            double v = (bc_v2.x * bc_v1.y - bc_v1.x * bc_v2.y) * den;
            double w = (bc_v0.x * bc_v2.y - bc_v2.x * bc_v0.y) * den;
            double u = 1 - v - w;

            return p.z < u * f.z + v * g.z + w * h.z;
        }

        /**
         * Generates 3D Clover Noise at a specific point.
         *
         * @param p The point to generate noise at.
         * @return The value of noise, from 0 to 1.
         */
        public double noise(Vector3 p) {
            Vector3 p_floor = p.floor();

            Vector3 c_111 = offset(p_floor);
            Vector3 c_100 = offset(p_floor.add(0, -1, -1));
            Vector3 c_010 = offset(p_floor.add(-1, 0, -1));
            Vector3 c_110 = offset(p_floor.add(0, 0, -1));
            Vector3 c_210 = offset(p_floor.add(1, 0, -1));
            Vector3 c_120 = offset(p_floor.add(0, 1, -1));
            Vector3 c_001 = offset(p_floor.add(-1, -1, 0));
            Vector3 c_101 = offset(p_floor.add(0, -1, 0));
            Vector3 c_201 = offset(p_floor.add(1, -1, 0));
            Vector3 c_011 = offset(p_floor.add(-1, 0, 0));
            Vector3 c_211 = offset(p_floor.add(1, 0, 0));
            Vector3 c_021 = offset(p_floor.add(-1, 1, 0));
            Vector3 c_121 = offset(p_floor.add(0, 1, 0));
            Vector3 c_221 = offset(p_floor.add(1, 1, 0));
            Vector3 c_102 = offset(p_floor.add(0, -1, 1));
            Vector3 c_012 = offset(p_floor.add(-1, 0, 1));
            Vector3 c_112 = offset(p_floor.add(0, 0, 1));
            Vector3 c_212 = offset(p_floor.add(1, 0, 1));
            Vector3 c_122 = offset(p_floor.add(0, 1, 1));

            boolean x_bound = boundary(p.yzx(), c_100.yzx(), c_110.yzx(), c_120.yzx(), c_101.yzx(), c_111.yzx(), c_121.yzx(), c_102.yzx(), c_112.yzx(), c_122.yzx());
            boolean y_bound = boundary(p.xzy(), c_010.xzy(), c_110.xzy(), c_210.xzy(), c_011.xzy(), c_111.xzy(), c_211.xzy(), c_012.xzy(), c_112.xzy(), c_212.xzy());
            boolean z_bound = boundary(p, c_001, c_101, c_201, c_011, c_111, c_211, c_021, c_121, c_221);

            Vector3 a, b, c, d, e, f, g, h;

            if (x_bound) {
                if (y_bound) {
                    if (z_bound) {
                        a = offset(p_floor.add(-1, -1, -1));
                        b = c_001;
                        c = c_010;
                        d = c_011;
                        e = c_100;
                        f = c_101;
                        g = c_110;
                        h = c_111;
                    } else {
                        a = c_001;
                        b = offset(p_floor.add(-1, -1, 1));
                        c = c_011;
                        d = c_012;
                        e = c_101;
                        f = c_102;
                        g = c_111;
                        h = c_112;
                    }
                } else {
                    if (z_bound) {
                        a = c_010;
                        b = c_011;
                        c = offset(p_floor.add(-1, 1, -1));
                        d = c_021;
                        e = c_110;
                        f = c_111;
                        g = c_120;
                        h = c_121;
                    } else {
                        a = c_011;
                        b = c_012;
                        c = c_021;
                        d = offset(p_floor.add(-1, 1, 1));
                        e = c_111;
                        f = c_112;
                        g = c_121;
                        h = c_122;
                    }
                }
            } else {
                if (y_bound) {
                    if (z_bound) {
                        a = c_100;
                        b = c_101;
                        c = c_110;
                        d = c_111;
                        e = offset(p_floor.add(1, -1, -1));
                        f = c_201;
                        g = c_210;
                        h = c_211;
                    } else {
                        a = c_101;
                        b = c_102;
                        c = c_111;
                        d = c_112;
                        e = c_201;
                        f = offset(p_floor.add(1, -1, 1));
                        g = c_211;
                        h = c_212;
                    }
                } else {
                    if (z_bound) {
                        a = c_110;
                        b = c_111;
                        c = c_120;
                        d = c_121;
                        e = c_210;
                        f = c_211;
                        g = offset(p_floor.add(1, 1, -1));
                        h = c_221;
                    } else {
                        a = c_111;
                        b = c_112;
                        c = c_121;
                        d = c_122;
                        e = c_211;
                        f = c_212;
                        g = c_221;
                        h = offset(p_floor.add(1, 1, 1));
                    }
                }
            }

            Vector3 ah = a.sub(h);
            Vector3 pa = p.sub(a);

            double plane_b = ah.cross(b.sub(h)).mult(pa).xpypz();
            double plane_c = ah.cross(c.sub(h)).mult(pa).xpypz();
            double plane_d = ah.cross(d.sub(h)).mult(pa).xpypz();
            double plane_e = ah.cross(e.sub(h)).mult(pa).xpypz();
            double plane_f = ah.cross(f.sub(h)).mult(pa).xpypz();
            double plane_g = ah.cross(g.sub(h)).mult(pa).xpypz();

            Vector3 i, j, k, l;

            i = a;
            j = h;

            if (plane_b > 0 && plane_d <= 0) {
                k = b;
                l = d;
            } else if (plane_d > 0 && plane_c <= 0) {
                k = d;
                l = c;
            } else if (plane_c > 0 && plane_g <= 0) {
                k = c;
                l = g;
            } else if (plane_g > 0 && plane_e <= 0) {
                k = g;
                l = e;
            } else if (plane_e > 0 && plane_f <= 0) {
                k = e;
                l = f;
            } else {
                k = f;
                l = b;
            }

            Vector3 bc_ap = p.sub(i);
            Vector3 bc_bp = p.sub(j);

            Vector3 bc_ab = j.sub(i);
            Vector3 bc_ac = k.sub(i);
            Vector3 bc_ad = l.sub(i);

            Vector3 bc_bc = k.sub(j);
            Vector3 bc_bd = l.sub(j);

            double bc_va6 = bc_bp.mult(bc_bd.cross(bc_bc)).xpypz();
            double bc_vb6 = bc_ap.mult(bc_ac.cross(bc_ad)).xpypz();
            double bc_vc6 = bc_ap.mult(bc_ad.cross(bc_ab)).xpypz();
            double bc_vd6 = bc_ap.mult(bc_ab.cross(bc_ac)).xpypz();
            double bc_v6 = 1 / bc_ab.mult(bc_ac.cross(bc_ad)).xpypz();

            double v = bc_va6 * bc_v6;
            double w = bc_vb6 * bc_v6;
            double t = bc_vc6 * bc_v6;
            double u = bc_vd6 * bc_v6;

            double fiu = u * u * u * (1 - v * w * t);
            double fiv = v * v * v * (1 - u * w * t);
            double fiw = w * w * w * (1 - v * u * t);
            double fit = t * t * t * (1 - v * w * u);
            double s = fiu + fiv + fiw + fit;
            fiu /= s;
            fiv /= s;
            fiw /= s;
            fit /= s;

            double iv = hash(i.floor());
            double jv = hash(j.floor());
            double kv = hash(k.floor());
            double lv = hash(l.floor());

            return fiv * iv + fiw * jv + fit * kv + fiu * lv;
        }

        /**
         * Generates 3D Clover Noise at a specific point.
         *
         * @param x The x coordinate of the point.
         * @param y The y coordinate of the point.
         * @param z The z coordinate of the point.
         * @return The value of noise, from 0 to 1.
         */
        public double noise(double x, double y, double z) {
            return noise(new Vector3(x, y, z));
        }

        /**
         * Generates fractal 3D Clover Noise at a specific point.
         *
         * @param p          The point to generate noise at.
         * @param iterations The number of iterations for the fractal noise.
         * @return The value of noise, from 0 to 1.
         */
        public double fractalNoise(Vector3 p, int iterations) {
            double total = 0;
            double weight = 1;
            double div = 0;

            for (int i = 0; i < iterations; i++) {
                total += noise(p.mult(1 / weight)) * weight;
                div += weight;

                weight *= 0.4;
            }

            return total / div;
        }

        /**
         * Generates fractal 3D Clover Noise at a specific point.
         *
         * @param x          The x coordinate of the point.
         * @param y          The y coordinate of the point.
         * @param z          The z coordinate of the point.
         * @param iterations The number of iterations for the fractal noise.
         * @return The value of noise, from 0 to 1.
         */
        public double fractalNoise(double x, double y, double z, int iterations) {
            return fractalNoise(new Vector3(x, y, z), iterations);
        }

        /**
         * Generates curl 3D Clover Noise at a specific point.
         *
         * @param p The point to generate noise at.
         * @return The value of curl noise, a normalized 3D vector.
         */
        public Vector3 curlNoise(Vector3 p) {
            double v = noise(p);
            double x = noise(p.add(CURL_DX, 0, 0));
            double y = noise(p.add(0, CURL_DX, 0));
            double z = noise(p.add(0, 0, CURL_DX));

            return new Vector3(v - x, v - y, v - z).normalize();
        }

        /**
         * Generates curl 3D Clover Noise at a specific point.
         *
         * @param x The x coordinate of the point.
         * @param y The y coordinate of the point.
         * @param z The z coordinate of the point.
         * @return The value of curl noise, a normalized 3D vector.
         */
        public Vector3 curlNoise(double x, double y, double z) {
            return curlNoise(new Vector3(x, y, z));
        }

        /**
         * Generates fractal curl 3D Clover Noise at a specific point.
         *
         * @param p          The point to generate noise at.
         * @param iterations The number of iterations for the fractal noise.
         * @return The value of curl noise, a normalized 3D vector.
         */
        public Vector3 fractalCurlNoise(Vector3 p, int iterations) {
            double v = fractalNoise(p, iterations);
            double x = fractalNoise(p.add(CURL_DX, 0, 0), iterations);
            double y = fractalNoise(p.add(0, CURL_DX, 0), iterations);
            double z = fractalNoise(p.add(0, 0, CURL_DX), iterations);

            return new Vector3(v - x, v - y, v - z).normalize();
        }

        /**
         * Generates fractal curl 3D Clover Noise at a specific point.
         *
         * @param x          The x coordinate of the point.
         * @param y          The y coordinate of the point.
         * @param z          The z coordinate of the point.
         * @param iterations The number of iterations for the fractal noise.
         * @return The value of curl noise, a normalized 3D vector.
         */
        public Vector3 fractalCurlNoise(double x, double y, double z, int iterations) {
            return fractalCurlNoise(new Vector3(x, y, z), iterations);
        }

        /**
         * Generates 3D Frost Noise at a specific point. See https://github.com/ValgoBoi/clover-noise/blob/master/variations/frost.md
         *
         * @param p The point to generate noise at.
         * @return The value of noise, from 0 to 1.
         */
        public double frostNoise(Vector3 p) {
            Vector3 curl_1 = fractalCurlNoise(p, 3).mult(fractalNoise(p, 2) * 0.4 + 0.3);
            Vector3 p_1 = p.add(curl_1);
            Vector3 curl_2 = fractalCurlNoise(p_1, 4).mult(fractalNoise(p_1, 3) * 0.1 + 0.05);
            Vector3 p_2 = p_1.add(curl_2);

            return fractalNoise(p_2, 5) - fractalNoise(p_1, 3) * 0.5 + fractalNoise(p, 2) * 0.3;
        }

        /**
         * Generates 3D Frost Noise at a specific point. See https://github.com/ValgoBoi/clover-noise/blob/master/variations/frost.md
         *
         * @param x The x coordinate of the point.
         * @param y The y coordinate of the point.
         * @param z The z coordinate of the point.
         * @return The value of noise, from 0 to 1.
         */
        public double frostNoise(double x, double y, double z) {
            return frostNoise(new Vector3(x, y, z));
        }

        /**
         * Generates 3D Marble Noise at a specific point. See https://github.com/ValgoBoi/clover-noise/blob/master/variations/marble.md
         *
         * @param p The point to generate noise at.
         * @return The value of noise, from 0 to 1.
         */
        public double marbleNoise(Vector3 p) {
            Vector3 p_2 = p.mult(0.6);
            double d1 = Math.max(1 - Math.abs(fractalNoise(p_2.add(100), 4) - fractalNoise(p_2.add(200), 3)) * 3, 0);
            d1 = d1 * d1 * d1;
            d1 *= fractalNoise(p_2.add(300), 2) * 0.3;

            Vector3 curl_1 = fractalCurlNoise(p.add(400), 3).mult(fractalNoise(p.add(500), 2) * 0.05);

            Vector3 p_3 = p.mult(1.2);
            double d2 = Math.max(1 - Math.abs(fractalNoise(p_3.add(curl_1).add(600), 4) - fractalNoise(p_3.add(curl_1).add(700), 3)) * 2, 0);
            d2 = d2 * d2 * d2;
            d2 *= fractalNoise(p_3.add(800), 2) * 0.5;

            double v = 1 - fractalNoise(p.add(900), 5);
            v = 1 - v * v * v;

            return Math.min(Math.max(v - d1 - d2, 0), 1);
        }

        /**
         * Generates 3D Marble Noise at a specific point. See https://github.com/ValgoBoi/clover-noise/blob/master/variations/marble.md
         *
         * @param x The x coordinate of the point.
         * @param y The y coordinate of the point.
         * @param z The z coordinate of the point.
         * @return The value of noise, from 0 to 1.
         */
        public double marbleNoise(double x, double y, double z) {
            return marbleNoise(new Vector3(x, y, z));
        }
    }

    /**
     * A 2-dimensional Vector, with methods relevant to the Clover Noise algorithm.
     */
    public static class Vector2 {
        private double x, y;

        public Vector2(double x, double y) {
            this.x = x;
            this.y = y;
        }

        public Vector2() {
            this(0, 0);
        }

        public double getX() {
            return x;
        }

        public void setX(double x) {
            this.x = x;
        }

        public double getY() {
            return y;
        }

        public void setY(double y) {
            this.y = y;
        }

        public double ymx() {
            return y - x;
        }

        public Vector2 yx() {
            return new Vector2(y, x);
        }

        public Vector2 negate() {
            return new Vector2(-x, -y);
        }

        public Vector2 floor() {
            return new Vector2(Math.floor(x), Math.floor(y));
        }

        public Vector2 normalize() {
            double len2 = x * x + y * y;
            double len = Math.sqrt(len2);

            return new Vector2(x / len, y / len);
        }

        public Vector2 add(Vector2 a) {
            return new Vector2(x + a.x, y + a.y);
        }

        public Vector2 add(double xa, double ya) {
            return add(new Vector2(xa, ya));
        }

        public Vector2 add(double a) {
            return add(a, a);
        }

        public Vector2 sub(Vector2 s) {
            return add(s.negate());
        }

        public Vector2 sub(double xs, double ys) {
            return sub(new Vector2(xs, ys));
        }

        public Vector2 sub(double s) {
            return sub(s, s);
        }

        public Vector2 mult(Vector2 m) {
            return new Vector2(x * m.x, y * m.y);
        }

        public Vector2 mult(double xm, double ym) {
            return mult(new Vector2(xm, ym));
        }

        public Vector2 mult(double m) {
            return mult(m, m);
        }
    }

    /**
     * A 3-dimensional Vector, with methods relevant to the Clover Noise algorithm.
     */
    public static class Vector3 {
        double x, y, z;

        public Vector3(double x, double y, double z) {
            this.x = x;
            this.y = y;
            this.z = z;
        }

        public Vector3() {
            this(0, 0, 0);
        }

        public double getX() {
            return x;
        }

        public void setX(double x) {
            this.x = x;
        }

        public double getY() {
            return y;
        }

        public void setY(double y) {
            this.y = y;
        }

        public double getZ() {
            return z;
        }

        public void setZ(double z) {
            this.z = z;
        }

        public Vector2 xy() {
            return new Vector2(x, y);
        }

        public Vector2 yx() {
            return new Vector2(y, x);
        }

        public Vector3 yzx() {
            return new Vector3(y, z, x);
        }

        public Vector3 xzy() {
            return new Vector3(x, z, y);
        }

        public double xpypz() {
            return x + y + z;
        }

        public Vector3 negate() {
            return new Vector3(-x, -y, -z);
        }

        public Vector3 floor() {
            return new Vector3(Math.floor(x), Math.floor(y), Math.floor(z));
        }

        public Vector3 normalize() {
            double len2 = x * x + y * y + z * z;
            double len = Math.sqrt(len2);

            return new Vector3(x / len, y / len, z / len);
        }

        public Vector3 add(Vector3 a) {
            return new Vector3(x + a.x, y + a.y, z + a.z);
        }

        public Vector3 add(double xa, double ya, double za) {
            return add(new Vector3(xa, ya, za));
        }

        public Vector3 add(double a) {
            return add(a, a, a);
        }

        public Vector3 sub(Vector3 s) {
            return add(s.negate());
        }

        public Vector3 sub(double xs, double ys, double zs) {
            return sub(new Vector3(xs, ys, zs));
        }

        public Vector3 sub(double s) {
            return sub(s, s, s);
        }

        public Vector3 mult(Vector3 m) {
            return new Vector3(x * m.x, y * m.y, z * m.z);
        }

        public Vector3 mult(double mx, double my, double mz) {
            return mult(new Vector3(mx, my, mz));
        }

        public Vector3 mult(double m) {
            return mult(m, m, m);
        }

        public Vector3 cross(Vector3 c) {
            return new Vector3(y * c.z - z * c.y, z * c.x - x * c.z, x * c.y - y * c.x);
        }
    }
}
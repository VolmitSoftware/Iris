/*
 * Iris is a World Generator for Minecraft Bukkit Servers
 * Copyright (c) 2021 Arcane Arts (Volmit Software)
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

package com.volmit.iris.util.interpolation;

import com.volmit.iris.util.function.NoiseProvider;

public class Starcast {
    public static double starcast(int x, int z, double r, double checks, boolean optimized, NoiseProvider n) {
        if (optimized) {
            if (checks == 3) return sc3(x, z, r, n);
            else if (checks == 5) return sc5(x, z, r, n);
            else if (checks == 6) return sc6(x, z, r, n);
            else if (checks == 7) return sc7(x, z, r, n);
            else if (checks == 9) return sc9(x, z, r, n);
            else if (checks == 12) return sc12(x, z, r, n);
            else if (checks == 24) return sc24(x, z, r, n);
            else if (checks == 32) return sc32(x, z, r, n);
            else if (checks == 48) return sc48(x, z, r, n);
            else if (checks == 64) return sc64(x, z, r, n);
        }

        double m = 360D / checks;
        double v = 0;

        for (int i = 0; i < 360; i += m) {
            double sin = Math.sin(Math.toRadians(i));
            double cos = Math.cos(Math.toRadians(i));
            double cx = x + ((r * cos) - (r * sin));
            double cz = z + ((r * sin) + (r * cos));
            v += n.noise(cx, cz);
        }

        return v / checks;
    }

    public static double starcast(int x, int z, double r, double checks, NoiseProvider n) {
        return starcast(x, z, r, checks, true, n);
    }

    private static final double F3C0 = 1;
    private static final double F3S0 = 0;
    private static final double F3C1 = -0.4999999999999997779553950749686919152736663818359375;
    private static final double F3S1 = 0.86602540378443870761060452423407696187496185302734375;
    private static final double F3C2 = -0.500000000000000444089209850062616169452667236328125;
    private static final double F3S2 = -0.86602540378443837454369713668711483478546142578125;

    private static double sc3(int x, int z, double r, NoiseProvider n) {
        return (n.noise(x + ((r * F3C0) - (r * F3S0)), z + ((r * F3S0) + (r * F3C0)))
                + n.noise(x + ((r * F3C1) - (r * F3S1)), z + ((r * F3S1) + (r * F3C1)))
                + n.noise(x + ((r * F3C2) - (r * F3S2)), z + ((r * F3S2) + (r * F3C2)))) / 3.0D;
    }

    private static final double F5C0 = 1;
    private static final double F5S0 = 0;
    private static final double F5C1 = 0.30901699437494745126286943559534847736358642578125;
    private static final double F5S1 = 0.95105651629515353118193843329208903014659881591796875;
    private static final double F5C2 = -0.80901699437494734024056697307969443500041961669921875;
    private static final double F5S2 = 0.58778525229247324812575925534474663436412811279296875;
    private static final double F5C3 = -0.80901699437494756228517189811100251972675323486328125;
    private static final double F5S3 = -0.58778525229247302608115433031343854963779449462890625;
    private static final double F5C4 = 0.3090169943749472292182645105640403926372528076171875;
    private static final double F5S4 = -0.951056516295153642204240895807743072509765625;

    private static double sc5(int x, int z, double r, NoiseProvider n) {
        return (n.noise(x + ((r * F5C0) - (r * F5S0)), z + ((r * F5S0) + (r * F5C0)))
                + n.noise(x + ((r * F5C1) - (r * F5S1)), z + ((r * F5S1) + (r * F5C1)))
                + n.noise(x + ((r * F5C2) - (r * F5S2)), z + ((r * F5S2) + (r * F5C2)))
                + n.noise(x + ((r * F5C3) - (r * F5S3)), z + ((r * F5S3) + (r * F5C3)))
                + n.noise(x + ((r * F5C4) - (r * F5S4)), z + ((r * F5S4) + (r * F5C4)))) / 5.0D;
    }

    private static final double F6C0 = 1;
    private static final double F6S0 = 0;
    private static final double F6C1 = 0.50000000000000011102230246251565404236316680908203125;
    private static final double F6S1 = 0.8660254037844385965883020617184229195117950439453125;
    private static final double F6C2 = -0.4999999999999997779553950749686919152736663818359375;
    private static final double F6S2 = 0.86602540378443870761060452423407696187496185302734375;
    private static final double F6C3 = -1;
    private static final double F6S3 = 0.000000000000000122464679914735320717376402945839660462569212467758006379625612680683843791484832763671875;
    private static final double F6C4 = -0.500000000000000444089209850062616169452667236328125;
    private static final double F6S4 = -0.86602540378443837454369713668711483478546142578125;
    private static final double F6C5 = 0.50000000000000011102230246251565404236316680908203125;
    private static final double F6S5 = -0.8660254037844385965883020617184229195117950439453125;

    private static double sc6(int x, int z, double r, NoiseProvider n) {
        return (n.noise(x + ((r * F6C0) - (r * F6S0)), z + ((r * F6S0) + (r * F6C0)))
                + n.noise(x + ((r * F6C1) - (r * F6S1)), z + ((r * F6S1) + (r * F6C1)))
                + n.noise(x + ((r * F6C2) - (r * F6S2)), z + ((r * F6S2) + (r * F6C2)))
                + n.noise(x + ((r * F6C3) - (r * F6S3)), z + ((r * F6S3) + (r * F6C3)))
                + n.noise(x + ((r * F6C4) - (r * F6S4)), z + ((r * F6S4) + (r * F6C4)))
                + n.noise(x + ((r * F6C5) - (r * F6S5)), z + ((r * F6S5) + (r * F6C5)))) / 6.0D;
    }

    private static final double F7C0 = 1;
    private static final double F7S0 = 0;
    private static final double F7C1 = 0.6293203910498375019955119569203816354274749755859375;
    private static final double F7S1 = 0.77714596145697090179282895405776798725128173828125;
    private static final double F7C2 = -0.207911690817759342575499204031075350940227508544921875;
    private static final double F7S2 = 0.9781476007338056888329447247087955474853515625;
    private static final double F7C3 = -0.891006524188367787786546614370308816432952880859375;
    private static final double F7S3 = 0.45399049973954685999188995992881245911121368408203125;
    private static final double F7C4 = -0.91354545764260086659902526662335731089115142822265625;
    private static final double F7S4 = -0.406736643075800208269043878317461349070072174072265625;
    private static final double F7C5 = -0.25881904510252062845410137015278451144695281982421875;
    private static final double F7S5 = -0.96592582628906831221371476203785277903079986572265625;
    private static final double F7C6 = 0.587785252292472915058851867797784507274627685546875;
    private static final double F7S6 = -0.80901699437494756228517189811100251972675323486328125;
    private static final double F7C7 = 0.99862953475457383323288240717374719679355621337890625;
    private static final double F7S7 = -0.052335956242944368932423770957029773853719234466552734375;

    private static double sc7(int x, int z, double r, NoiseProvider n) {
        return (n.noise(x + ((r * F7C0) - (r * F7S0)), z + ((r * F7S0) + (r * F7C0)))
                + n.noise(x + ((r * F7C1) - (r * F7S1)), z + ((r * F7S1) + (r * F7C1)))
                + n.noise(x + ((r * F7C2) - (r * F7S2)), z + ((r * F7S2) + (r * F7C2)))
                + n.noise(x + ((r * F7C3) - (r * F7S3)), z + ((r * F7S3) + (r * F7C3)))
                + n.noise(x + ((r * F7C4) - (r * F7S4)), z + ((r * F7S4) + (r * F7C4)))
                + n.noise(x + ((r * F7C5) - (r * F7S5)), z + ((r * F7S5) + (r * F7C5)))
                + n.noise(x + ((r * F7C6) - (r * F7S6)), z + ((r * F7S6) + (r * F7C6)))
                + n.noise(x + ((r * F7C7) - (r * F7S7)), z + ((r * F7S7) + (r * F7C7)))) / 7.0D;
    }

    private static final double F9C0 = 1;
    private static final double F9S0 = 0;
    private static final double F9C1 = 0.76604444311897801345168090847437269985675811767578125;
    private static final double F9S1 = 0.642787609686539251896419955301098525524139404296875;
    private static final double F9C2 = 0.17364817766693041445336120887077413499355316162109375;
    private static final double F9S2 = 0.98480775301220802031565426659653894603252410888671875;
    private static final double F9C3 = -0.4999999999999997779553950749686919152736663818359375;
    private static final double F9S3 = 0.86602540378443870761060452423407696187496185302734375;
    private static final double F9C4 = -0.93969262078590831688273965482949279248714447021484375;
    private static final double F9S4 = 0.342020143325668879441536773811094462871551513671875;
    private static final double F9C5 = -0.939692620785908427905042117345146834850311279296875;
    private static final double F9S5 = -0.3420201433256686573969318487797863781452178955078125;
    private static final double F9C6 = -0.500000000000000444089209850062616169452667236328125;
    private static final double F9S6 = -0.86602540378443837454369713668711483478546142578125;
    private static final double F9C7 = 0.17364817766692997036415135880815796554088592529296875;
    private static final double F9S7 = -0.98480775301220813133795672911219298839569091796875;
    private static final double F9C8 = 0.76604444311897779140707598344306461513042449951171875;
    private static final double F9S8 = -0.64278760968653958496332734284806065261363983154296875;

    private static double sc9(int x, int z, double r, NoiseProvider n) {
        return (n.noise(x + ((r * F9C0) - (r * F9S0)), z + ((r * F9S0) + (r * F9C0)))
                + n.noise(x + ((r * F9C1) - (r * F9S1)), z + ((r * F9S1) + (r * F9C1)))
                + n.noise(x + ((r * F9C2) - (r * F9S2)), z + ((r * F9S2) + (r * F9C2)))
                + n.noise(x + ((r * F9C3) - (r * F9S3)), z + ((r * F9S3) + (r * F9C3)))
                + n.noise(x + ((r * F9C4) - (r * F9S4)), z + ((r * F9S4) + (r * F9C4)))
                + n.noise(x + ((r * F9C5) - (r * F9S5)), z + ((r * F9S5) + (r * F9C5)))
                + n.noise(x + ((r * F9C6) - (r * F9S6)), z + ((r * F9S6) + (r * F9C6)))
                + n.noise(x + ((r * F9C7) - (r * F9S7)), z + ((r * F9S7) + (r * F9C7)))
                + n.noise(x + ((r * F9C8) - (r * F9S8)), z + ((r * F9S8) + (r * F9C8)))) / 9.0D;
    }

    private static final double F12C0 = 1;
    private static final double F12S0 = 0;
    private static final double F12C1 = 0.86602540378443870761060452423407696187496185302734375;
    private static final double F12S1 = 0.499999999999999944488848768742172978818416595458984375;
    private static final double F12C2 = 0.50000000000000011102230246251565404236316680908203125;
    private static final double F12S2 = 0.8660254037844385965883020617184229195117950439453125;
    private static final double F12C3 = 0.0000000000000000612323399573676603586882014729198302312846062338790031898128063403419218957424163818359375;
    private static final double F12S3 = 1;
    private static final double F12C4 = -0.4999999999999997779553950749686919152736663818359375;
    private static final double F12S4 = 0.86602540378443870761060452423407696187496185302734375;
    private static final double F12C5 = -0.86602540378443870761060452423407696187496185302734375;
    private static final double F12S5 = 0.499999999999999944488848768742172978818416595458984375;
    private static final double F12C6 = -1;
    private static final double F12S6 = 0.000000000000000122464679914735320717376402945839660462569212467758006379625612680683843791484832763671875;
    private static final double F12C7 = -0.8660254037844385965883020617184229195117950439453125;
    private static final double F12S7 = -0.50000000000000011102230246251565404236316680908203125;
    private static final double F12C8 = -0.500000000000000444089209850062616169452667236328125;
    private static final double F12S8 = -0.86602540378443837454369713668711483478546142578125;
    private static final double F12C9 = -0.00000000000000018369701987210296875011296034045003113559498615810217092558787044254131615161895751953125;
    private static final double F12S9 = -1;
    private static final double F12C10 = 0.50000000000000011102230246251565404236316680908203125;
    private static final double F12S10 = -0.8660254037844385965883020617184229195117950439453125;
    private static final double F12C11 = 0.86602540378443837454369713668711483478546142578125;
    private static final double F12S11 = -0.500000000000000444089209850062616169452667236328125;

    private static double sc12(int x, int z, double r, NoiseProvider n) {
        return (n.noise(x + ((r * F12C0) - (r * F12S0)), z + ((r * F12S0) + (r * F12C0)))
                + n.noise(x + ((r * F12C1) - (r * F12S1)), z + ((r * F12S1) + (r * F12C1)))
                + n.noise(x + ((r * F12C2) - (r * F12S2)), z + ((r * F12S2) + (r * F12C2)))
                + n.noise(x + ((r * F12C3) - (r * F12S3)), z + ((r * F12S3) + (r * F12C3)))
                + n.noise(x + ((r * F12C4) - (r * F12S4)), z + ((r * F12S4) + (r * F12C4)))
                + n.noise(x + ((r * F12C5) - (r * F12S5)), z + ((r * F12S5) + (r * F12C5)))
                + n.noise(x + ((r * F12C6) - (r * F12S6)), z + ((r * F12S6) + (r * F12C6)))
                + n.noise(x + ((r * F12C7) - (r * F12S7)), z + ((r * F12S7) + (r * F12C7)))
                + n.noise(x + ((r * F12C8) - (r * F12S8)), z + ((r * F12S8) + (r * F12C8)))
                + n.noise(x + ((r * F12C9) - (r * F12S9)), z + ((r * F12S9) + (r * F12C9)))
                + n.noise(x + ((r * F12C10) - (r * F12S10)), z + ((r * F12S10) + (r * F12C10)))
                + n.noise(x + ((r * F12C11) - (r * F12S11)), z + ((r * F12S11) + (r * F12C11)))) / 12.0D;
    }

    private static final double F24C0 = 1;
    private static final double F24S0 = 0;
    private static final double F24C1 = 0.96592582628906831221371476203785277903079986572265625;
    private static final double F24S1 = 0.25881904510252073947640383266843855381011962890625;
    private static final double F24C2 = 0.86602540378443870761060452423407696187496185302734375;
    private static final double F24S2 = 0.499999999999999944488848768742172978818416595458984375;
    private static final double F24C3 = 0.70710678118654757273731092936941422522068023681640625;
    private static final double F24S3 = 0.707106781186547461715008466853760182857513427734375;
    private static final double F24C4 = 0.50000000000000011102230246251565404236316680908203125;
    private static final double F24S4 = 0.8660254037844385965883020617184229195117950439453125;
    private static final double F24C5 = 0.25881904510252073947640383266843855381011962890625;
    private static final double F24S5 = 0.96592582628906831221371476203785277903079986572265625;
    private static final double F24C6 = 0.0000000000000000612323399573676603586882014729198302312846062338790031898128063403419218957424163818359375;
    private static final double F24S6 = 1;
    private static final double F24C7 = -0.25881904510252085049870629518409259617328643798828125;
    private static final double F24S7 = 0.96592582628906831221371476203785277903079986572265625;
    private static final double F24C8 = -0.4999999999999997779553950749686919152736663818359375;
    private static final double F24S8 = 0.86602540378443870761060452423407696187496185302734375;
    private static final double F24C9 = -0.707106781186547461715008466853760182857513427734375;
    private static final double F24S9 = 0.70710678118654757273731092936941422522068023681640625;
    private static final double F24C10 = -0.86602540378443870761060452423407696187496185302734375;
    private static final double F24S10 = 0.499999999999999944488848768742172978818416595458984375;
    private static final double F24C11 = -0.965925826289068201191412299522198736667633056640625;
    private static final double F24S11 = 0.258819045102521017032159988957573659718036651611328125;
    private static final double F24C12 = -1;
    private static final double F24S12 = 0.000000000000000122464679914735320717376402945839660462569212467758006379625612680683843791484832763671875;
    private static final double F24C13 = -0.96592582628906831221371476203785277903079986572265625;
    private static final double F24S13 = -0.258819045102520794987555063926265574991703033447265625;
    private static final double F24C14 = -0.8660254037844385965883020617184229195117950439453125;
    private static final double F24S14 = -0.50000000000000011102230246251565404236316680908203125;
    private static final double F24C15 = -0.7071067811865476837596133918850682675838470458984375;
    private static final double F24S15 = -0.707106781186547461715008466853760182857513427734375;
    private static final double F24C16 = -0.500000000000000444089209850062616169452667236328125;
    private static final double F24S16 = -0.86602540378443837454369713668711483478546142578125;
    private static final double F24C17 = -0.25881904510252062845410137015278451144695281982421875;
    private static final double F24S17 = -0.96592582628906831221371476203785277903079986572265625;
    private static final double F24C18 = -0.00000000000000018369701987210296875011296034045003113559498615810217092558787044254131615161895751953125;
    private static final double F24S18 = -1;
    private static final double F24C19 = 0.258819045102520295387193982605822384357452392578125;
    private static final double F24S19 = -0.9659258262890684232360172245535068213939666748046875;
    private static final double F24C20 = 0.50000000000000011102230246251565404236316680908203125;
    private static final double F24S20 = -0.8660254037844385965883020617184229195117950439453125;
    private static final double F24C21 = 0.70710678118654735069270600433810614049434661865234375;
    private static final double F24S21 = -0.7071067811865476837596133918850682675838470458984375;
    private static final double F24C22 = 0.86602540378443837454369713668711483478546142578125;
    private static final double F24S22 = -0.500000000000000444089209850062616169452667236328125;
    private static final double F24C23 = 0.96592582628906831221371476203785277903079986572265625;
    private static final double F24S23 = -0.258819045102520683965252601410611532628536224365234375;

    private static double sc24(int x, int z, double r, NoiseProvider n) {
        return (n.noise(x + ((r * F24C0) - (r * F24S0)), z + ((r * F24S0) + (r * F24C0)))
                + n.noise(x + ((r * F24C1) - (r * F24S1)), z + ((r * F24S1) + (r * F24C1)))
                + n.noise(x + ((r * F24C2) - (r * F24S2)), z + ((r * F24S2) + (r * F24C2)))
                + n.noise(x + ((r * F24C3) - (r * F24S3)), z + ((r * F24S3) + (r * F24C3)))
                + n.noise(x + ((r * F24C4) - (r * F24S4)), z + ((r * F24S4) + (r * F24C4)))
                + n.noise(x + ((r * F24C5) - (r * F24S5)), z + ((r * F24S5) + (r * F24C5)))
                + n.noise(x + ((r * F24C6) - (r * F24S6)), z + ((r * F24S6) + (r * F24C6)))
                + n.noise(x + ((r * F24C7) - (r * F24S7)), z + ((r * F24S7) + (r * F24C7)))
                + n.noise(x + ((r * F24C8) - (r * F24S8)), z + ((r * F24S8) + (r * F24C8)))
                + n.noise(x + ((r * F24C9) - (r * F24S9)), z + ((r * F24S9) + (r * F24C9)))
                + n.noise(x + ((r * F24C10) - (r * F24S10)), z + ((r * F24S10) + (r * F24C10)))
                + n.noise(x + ((r * F24C11) - (r * F24S11)), z + ((r * F24S11) + (r * F24C11)))
                + n.noise(x + ((r * F24C12) - (r * F24S12)), z + ((r * F24S12) + (r * F24C12)))
                + n.noise(x + ((r * F24C13) - (r * F24S13)), z + ((r * F24S13) + (r * F24C13)))
                + n.noise(x + ((r * F24C14) - (r * F24S14)), z + ((r * F24S14) + (r * F24C14)))
                + n.noise(x + ((r * F24C15) - (r * F24S15)), z + ((r * F24S15) + (r * F24C15)))
                + n.noise(x + ((r * F24C16) - (r * F24S16)), z + ((r * F24S16) + (r * F24C16)))
                + n.noise(x + ((r * F24C17) - (r * F24S17)), z + ((r * F24S17) + (r * F24C17)))
                + n.noise(x + ((r * F24C18) - (r * F24S18)), z + ((r * F24S18) + (r * F24C18)))
                + n.noise(x + ((r * F24C19) - (r * F24S19)), z + ((r * F24S19) + (r * F24C19)))
                + n.noise(x + ((r * F24C20) - (r * F24S20)), z + ((r * F24S20) + (r * F24C20)))
                + n.noise(x + ((r * F24C21) - (r * F24S21)), z + ((r * F24S21) + (r * F24C21)))
                + n.noise(x + ((r * F24C22) - (r * F24S22)), z + ((r * F24S22) + (r * F24C22)))
                + n.noise(x + ((r * F24C23) - (r * F24S23)), z + ((r * F24S23) + (r * F24C23)))) / 24.0D;
    }

    private static final double F32C0 = 1;
    private static final double F32S0 = 0;
    private static final double F32C1 = 0.9816271834476639757127713892259635031223297119140625;
    private static final double F32S1 = 0.1908089953765448043565555735767702572047710418701171875;
    private static final double F32C2 = 0.92718385456678742428948680753819644451141357421875;
    private static final double F32S2 = 0.374606593415912014766178117497474886476993560791015625;
    private static final double F32C3 = 0.838670567945424050293468098971061408519744873046875;
    private static final double F32S3 = 0.54463903501502708426329490976058878004550933837890625;
    private static final double F32C4 = 0.71933980033865119185776393351261503994464874267578125;
    private static final double F32S4 = 0.6946583704589972541043607634492218494415283203125;
    private static final double F32C5 = 0.57357643635104615942310601894860155880451202392578125;
    private static final double F32S5 = 0.81915204428899179855960710483486764132976531982421875;
    private static final double F32C6 = 0.406736643075800208269043878317461349070072174072265625;
    private static final double F32S6 = 0.91354545764260086659902526662335731089115142822265625;
    private static final double F32C7 = 0.224951054343864920159745679484331049025058746337890625;
    private static final double F32S7 = 0.97437006478523524588553073044749908149242401123046875;
    private static final double F32C8 = 0.034899496702501080214187112460422213189303874969482421875;
    private static final double F32S8 = 0.99939082701909576211818375668372027575969696044921875;
    private static final double F32C9 = -0.1564344650402308134484741231062798760831356048583984375;
    private static final double F32S9 = 0.987688340595137770350220307591371238231658935546875;
    private static final double F32C10 = -0.342020143325668712908083080037613399326801300048828125;
    private static final double F32S10 = 0.939692620785908427905042117345146834850311279296875;
    private static final double F32C11 = -0.515038074910054266553061097511090338230133056640625;
    private static final double F32S11 = 0.8571673007021123336102164103067480027675628662109375;
    private static final double F32C12 = -0.66913060635885823757007528911344707012176513671875;
    private static final double F32S12 = 0.7431448254773942441175904605188407003879547119140625;
    private static final double F32C13 = -0.79863551004729294024997443557367660105228424072265625;
    private static final double F32S13 = 0.60181502315204815634075430352822877466678619384765625;
    private static final double F32C14 = -0.8987940462991670376169395240140147507190704345703125;
    private static final double F32S14 = 0.43837114678907729281576166613376699388027191162109375;
    private static final double F32C15 = -0.965925826289068201191412299522198736667633056640625;
    private static final double F32S15 = 0.258819045102521017032159988957573659718036651611328125;
    private static final double F32C16 = -0.9975640502598241976528470331686548888683319091796875;
    private static final double F32S16 = 0.06975647374412552448319502218510024249553680419921875;
    private static final double F32C17 = -0.992546151641322094150154953240416944026947021484375;
    private static final double F32S17 = -0.12186934340514730956694933183825924061238765716552734375;
    private static final double F32C18 = -0.951056516295153642204240895807743072509765625;
    private static final double F32S18 = -0.309016994374947284729415741821867413818836212158203125;
    private static final double F32C19 = -0.87461970713939585220231265338952653110027313232421875;
    private static final double F32S19 = -0.484809620246336947513299264755914919078350067138671875;
    private static final double F32C20 = -0.76604444311897801345168090847437269985675811767578125;
    private static final double F32S20 = -0.642787609686539251896419955301098525524139404296875;
    private static final double F32C21 = -0.62932039104983783506241934446734376251697540283203125;
    private static final double F32S21 = -0.77714596145697056872592156651080586016178131103515625;
    private static final double F32C22 = -0.469471562785890750291173389996401965618133544921875;
    private static final double F32S22 = -0.88294759285892698841280434862710535526275634765625;
    private static final double F32C23 = -0.29237170472273710242205879694665782153606414794921875;
    private static final double F32S23 = -0.9563047559630353244841671767062507569789886474609375;
    private static final double F32C24 = -0.104528463267653359825004599770181812345981597900390625;
    private static final double F32S24 = -0.99452189536827340088365190240438096225261688232421875;
    private static final double F32C25 = 0.0871557427476578883140945208651828579604625701904296875;
    private static final double F32S25 = -0.9961946980917455451987052583717741072177886962890625;
    private static final double F32C26 = 0.275637355816999385371701691838097758591175079345703125;
    private static final double F32S26 = -0.96126169593831878312784056106465868651866912841796875;
    private static final double F32C27 = 0.45399049973954663794728503489750437438488006591796875;
    private static final double F32S27 = -0.89100652418836789880884907688596285879611968994140625;
    private static final double F32C28 = 0.61566147532565851374641852089553140103816986083984375;
    private static final double F32S28 = -0.7880107536067217921527117141522467136383056640625;
    private static final double F32C29 = 0.7547095802227719030241814834880642592906951904296875;
    private static final double F32S29 = -0.65605902899050738685815531425760127604007720947265625;
    private static final double F32C30 = 0.86602540378443837454369713668711483478546142578125;
    private static final double F32S30 = -0.500000000000000444089209850062616169452667236328125;
    private static final double F32C31 = 0.94551857559931684615861513520940206944942474365234375;
    private static final double F32S31 = -0.3255681544571566998769185374840162694454193115234375;
    private static final double F32C32 = 0.99026806874157025095684048210387118160724639892578125;
    private static final double F32S32 = -0.1391731009600658819369556340461713261902332305908203125;

    private static double sc32(int x, int z, double r, NoiseProvider n) {
        return (n.noise(x + ((r * F32C0) - (r * F32S0)), z + ((r * F32S0) + (r * F32C0)))
                + n.noise(x + ((r * F32C1) - (r * F32S1)), z + ((r * F32S1) + (r * F32C1)))
                + n.noise(x + ((r * F32C2) - (r * F32S2)), z + ((r * F32S2) + (r * F32C2)))
                + n.noise(x + ((r * F32C3) - (r * F32S3)), z + ((r * F32S3) + (r * F32C3)))
                + n.noise(x + ((r * F32C4) - (r * F32S4)), z + ((r * F32S4) + (r * F32C4)))
                + n.noise(x + ((r * F32C5) - (r * F32S5)), z + ((r * F32S5) + (r * F32C5)))
                + n.noise(x + ((r * F32C6) - (r * F32S6)), z + ((r * F32S6) + (r * F32C6)))
                + n.noise(x + ((r * F32C7) - (r * F32S7)), z + ((r * F32S7) + (r * F32C7)))
                + n.noise(x + ((r * F32C8) - (r * F32S8)), z + ((r * F32S8) + (r * F32C8)))
                + n.noise(x + ((r * F32C9) - (r * F32S9)), z + ((r * F32S9) + (r * F32C9)))
                + n.noise(x + ((r * F32C10) - (r * F32S10)), z + ((r * F32S10) + (r * F32C10)))
                + n.noise(x + ((r * F32C11) - (r * F32S11)), z + ((r * F32S11) + (r * F32C11)))
                + n.noise(x + ((r * F32C12) - (r * F32S12)), z + ((r * F32S12) + (r * F32C12)))
                + n.noise(x + ((r * F32C13) - (r * F32S13)), z + ((r * F32S13) + (r * F32C13)))
                + n.noise(x + ((r * F32C14) - (r * F32S14)), z + ((r * F32S14) + (r * F32C14)))
                + n.noise(x + ((r * F32C15) - (r * F32S15)), z + ((r * F32S15) + (r * F32C15)))
                + n.noise(x + ((r * F32C16) - (r * F32S16)), z + ((r * F32S16) + (r * F32C16)))
                + n.noise(x + ((r * F32C17) - (r * F32S17)), z + ((r * F32S17) + (r * F32C17)))
                + n.noise(x + ((r * F32C18) - (r * F32S18)), z + ((r * F32S18) + (r * F32C18)))
                + n.noise(x + ((r * F32C19) - (r * F32S19)), z + ((r * F32S19) + (r * F32C19)))
                + n.noise(x + ((r * F32C20) - (r * F32S20)), z + ((r * F32S20) + (r * F32C20)))
                + n.noise(x + ((r * F32C21) - (r * F32S21)), z + ((r * F32S21) + (r * F32C21)))
                + n.noise(x + ((r * F32C22) - (r * F32S22)), z + ((r * F32S22) + (r * F32C22)))
                + n.noise(x + ((r * F32C23) - (r * F32S23)), z + ((r * F32S23) + (r * F32C23)))
                + n.noise(x + ((r * F32C24) - (r * F32S24)), z + ((r * F32S24) + (r * F32C24)))
                + n.noise(x + ((r * F32C25) - (r * F32S25)), z + ((r * F32S25) + (r * F32C25)))
                + n.noise(x + ((r * F32C26) - (r * F32S26)), z + ((r * F32S26) + (r * F32C26)))
                + n.noise(x + ((r * F32C27) - (r * F32S27)), z + ((r * F32S27) + (r * F32C27)))
                + n.noise(x + ((r * F32C28) - (r * F32S28)), z + ((r * F32S28) + (r * F32C28)))
                + n.noise(x + ((r * F32C29) - (r * F32S29)), z + ((r * F32S29) + (r * F32C29)))
                + n.noise(x + ((r * F32C30) - (r * F32S30)), z + ((r * F32S30) + (r * F32C30)))
                + n.noise(x + ((r * F32C31) - (r * F32S31)), z + ((r * F32S31) + (r * F32C31)))
                + n.noise(x + ((r * F32C32) - (r * F32S32)), z + ((r * F32S32) + (r * F32C32)))) / 32.0D;
    }

    private static final double F48C0 = 1;
    private static final double F48S0 = 0;
    private static final double F48C1 = 0.99254615164132198312785249072476290166378021240234375;
    private static final double F48S1 = 0.12186934340514747610040302561174030415713787078857421875;
    private static final double F48C2 = 0.97029572627599647294260876151383854448795318603515625;
    private static final double F48S2 = 0.2419218955996677300479547056966111995279788970947265625;
    private static final double F48C3 = 0.93358042649720174299687869279296137392520904541015625;
    private static final double F48S3 = 0.3583679495453002683547083506709896028041839599609375;
    private static final double F48C4 = 0.88294759285892698841280434862710535526275634765625;
    private static final double F48S4 = 0.469471562785890805802324621254228986799716949462890625;
    private static final double F48C5 = 0.81915204428899179855960710483486764132976531982421875;
    private static final double F48S5 = 0.57357643635104604840080355643294751644134521484375;
    private static final double F48C6 = 0.7431448254773942441175904605188407003879547119140625;
    private static final double F48S6 = 0.66913060635885823757007528911344707012176513671875;
    private static final double F48C7 = 0.656059028990507275835852851741947233676910400390625;
    private static final double F48S7 = 0.75470958022277201404648394600371830165386199951171875;
    private static final double F48C8 = 0.5591929034707467938147829045192338526248931884765625;
    private static final double F48S8 = 0.82903757255504173517834942686022259294986724853515625;
    private static final double F48C9 = 0.453990499739546804480738728670985437929630279541015625;
    private static final double F48S9 = 0.891006524188367787786546614370308816432952880859375;
    private static final double F48C10 = 0.342020143325668823930385542553267441689968109130859375;
    private static final double F48S10 = 0.93969262078590831688273965482949279248714447021484375;
    private static final double F48C11 = 0.224951054343864920159745679484331049025058746337890625;
    private static final double F48S11 = 0.97437006478523524588553073044749908149242401123046875;
    private static final double F48C12 = 0.10452846326765345696951925447137909941375255584716796875;
    private static final double F48S12 = 0.9945218953682732898613494398887269198894500732421875;
    private static final double F48C13 = -0.01745240643728347695873281963940826244652271270751953125;
    private static final double F48S13 = 0.9998476951563912695775115935248322784900665283203125;
    private static final double F48C14 = -0.139173100960065354581018937096814624965190887451171875;
    private static final double F48S14 = 0.9902680687415703619791429446195252239704132080078125;
    private static final double F48C15 = -0.25881904510252085049870629518409259617328643798828125;
    private static final double F48S15 = 0.96592582628906831221371476203785277903079986572265625;
    private static final double F48C16 = -0.37460659341591207027732934875530190765857696533203125;
    private static final double F48S16 = 0.92718385456678742428948680753819644451141357421875;
    private static final double F48C17 = -0.4848096202463370030244504960137419402599334716796875;
    private static final double F48S17 = 0.87461970713939585220231265338952653110027313232421875;
    private static final double F48C18 = -0.58778525229247302608115433031343854963779449462890625;
    private static final double F48S18 = 0.80901699437494745126286943559534847736358642578125;
    private static final double F48C19 = -0.6819983600624983655080768585321493446826934814453125;
    private static final double F48S19 = 0.73135370161917057085787519099540077149868011474609375;
    private static final double F48C20 = -0.76604444311897790242937844595871865749359130859375;
    private static final double F48S20 = 0.6427876096865394739410248803324066102504730224609375;
    private static final double F48C21 = -0.83867056794542393927116563645540736615657806396484375;
    private static final double F48S21 = 0.54463903501502730630789983479189686477184295654296875;
    private static final double F48C22 = -0.8987940462991670376169395240140147507190704345703125;
    private static final double F48S22 = 0.43837114678907729281576166613376699388027191162109375;
    private static final double F48C23 = -0.94551857559931684615861513520940206944942474365234375;
    private static final double F48S23 = 0.32556815445715658885461607496836222708225250244140625;
    private static final double F48C24 = -0.9781476007338056888329447247087955474853515625;
    private static final double F48S24 = 0.2079116908177593148199235884021618403494358062744140625;
    private static final double F48C25 = -0.9961946980917455451987052583717741072177886962890625;
    private static final double F48S25 = 0.087155742747658193625426292783231474459171295166015625;
    private static final double F48C26 = -0.99939082701909576211818375668372027575969696044921875;
    private static final double F48S26 = -0.034899496702500899802945610872484394349157810211181640625;
    private static final double F48C27 = -0.987688340595137770350220307591371238231658935546875;
    private static final double F48S27 = -0.156434465040230730181747276219539344310760498046875;
    private static final double F48C28 = -0.9612616959383188941501430235803127288818359375;
    private static final double F48S28 = -0.27563735581699899679364307303330861032009124755859375;
    private static final double F48C29 = -0.92050485345244037471701403774204663932323455810546875;
    private static final double F48S29 = -0.390731128489273549231342030907399021089076995849609375;
    private static final double F48C30 = -0.8660254037844385965883020617184229195117950439453125;
    private static final double F48S30 = -0.50000000000000011102230246251565404236316680908203125;
    private static final double F48C31 = -0.798635510047292829227671973058022558689117431640625;
    private static final double F48S31 = -0.60181502315204837838535922855953685939311981201171875;
    private static final double F48C32 = -0.71933980033865108083546147099696099758148193359375;
    private static final double F48S32 = -0.69465837045899736512666322596487589180469512939453125;
    private static final double F48C33 = -0.62932039104983783506241934446734376251697540283203125;
    private static final double F48S33 = -0.77714596145697056872592156651080586016178131103515625;
    private static final double F48C34 = -0.52991926423320501182701036668731831014156341552734375;
    private static final double F48S34 = -0.84804809615642595677087456351728178560733795166015625;
    private static final double F48C35 = -0.42261826174069916373099431439186446368694305419921875;
    private static final double F48S35 = -0.90630778703665004769618462887592613697052001953125;
    private static final double F48C36 = -0.30901699437494756228517189811100251972675323486328125;
    private static final double F48S36 = -0.95105651629515353118193843329208903014659881591796875;
    private static final double F48C37 = -0.1908089953765446100675262641743756830692291259765625;
    private static final double F48S37 = -0.9816271834476639757127713892259635031223297119140625;
    private static final double F48C38 = -0.069756473744125579994346253442927263677120208740234375;
    private static final double F48S38 = -0.9975640502598241976528470331686548888683319091796875;
    private static final double F48C39 = 0.05233595624294394565989563261609873734414577484130859375;
    private static final double F48S39 = -0.99862953475457383323288240717374719679355621337890625;
    private static final double F48C40 = 0.17364817766692997036415135880815796554088592529296875;
    private static final double F48S40 = -0.98480775301220813133795672911219298839569091796875;
    private static final double F48C41 = 0.292371704722736713844000178141868673264980316162109375;
    private static final double F48S41 = -0.95630475596303543550646963922190479934215545654296875;
    private static final double F48C42 = 0.406736643075799764179834028254845179617404937744140625;
    private static final double F48S42 = -0.91354545764260108864363019165466539561748504638671875;
    private static final double F48C43 = 0.51503807491005415553075863499543629586696624755859375;
    private static final double F48S43 = -0.8571673007021123336102164103067480027675628662109375;
    private static final double F48C44 = 0.61566147532565851374641852089553140103816986083984375;
    private static final double F48S44 = -0.7880107536067217921527117141522467136383056640625;
    private static final double F48C45 = 0.70710678118654735069270600433810614049434661865234375;
    private static final double F48S45 = -0.7071067811865476837596133918850682675838470458984375;
    private static final double F48C46 = 0.7880107536067220141973166391835547983646392822265625;
    private static final double F48S46 = -0.61566147532565818067951113334856927394866943359375;
    private static final double F48C47 = 0.857167300702112111565611485275439918041229248046875;
    private static final double F48S47 = -0.5150380749100544885976660225423984229564666748046875;
    private static final double F48C48 = 0.9135454576426009776213277291390113532543182373046875;
    private static final double F48S48 = -0.40673664307580015275789264705963432788848876953125;
    private static final double F48C49 = 0.9563047559630353244841671767062507569789886474609375;
    private static final double F48S49 = -0.292371704722737157933210028204484842717647552490234375;
    private static final double F48C50 = 0.98480775301220802031565426659653894603252410888671875;
    private static final double F48S50 = -0.1736481776669303866977855932418606244027614593505859375;
    private static final double F48C51 = 0.99862953475457383323288240717374719679355621337890625;
    private static final double F48S51 = -0.052335956242944368932423770957029773853719234466552734375;

    private static double sc48(int x, int z, double r, NoiseProvider n) {
        return (n.noise(x + ((r * F48C0) - (r * F48S0)), z + ((r * F48S0) + (r * F48C0)))
                + n.noise(x + ((r * F48C1) - (r * F48S1)), z + ((r * F48S1) + (r * F48C1)))
                + n.noise(x + ((r * F48C2) - (r * F48S2)), z + ((r * F48S2) + (r * F48C2)))
                + n.noise(x + ((r * F48C3) - (r * F48S3)), z + ((r * F48S3) + (r * F48C3)))
                + n.noise(x + ((r * F48C4) - (r * F48S4)), z + ((r * F48S4) + (r * F48C4)))
                + n.noise(x + ((r * F48C5) - (r * F48S5)), z + ((r * F48S5) + (r * F48C5)))
                + n.noise(x + ((r * F48C6) - (r * F48S6)), z + ((r * F48S6) + (r * F48C6)))
                + n.noise(x + ((r * F48C7) - (r * F48S7)), z + ((r * F48S7) + (r * F48C7)))
                + n.noise(x + ((r * F48C8) - (r * F48S8)), z + ((r * F48S8) + (r * F48C8)))
                + n.noise(x + ((r * F48C9) - (r * F48S9)), z + ((r * F48S9) + (r * F48C9)))
                + n.noise(x + ((r * F48C10) - (r * F48S10)), z + ((r * F48S10) + (r * F48C10)))
                + n.noise(x + ((r * F48C11) - (r * F48S11)), z + ((r * F48S11) + (r * F48C11)))
                + n.noise(x + ((r * F48C12) - (r * F48S12)), z + ((r * F48S12) + (r * F48C12)))
                + n.noise(x + ((r * F48C13) - (r * F48S13)), z + ((r * F48S13) + (r * F48C13)))
                + n.noise(x + ((r * F48C14) - (r * F48S14)), z + ((r * F48S14) + (r * F48C14)))
                + n.noise(x + ((r * F48C15) - (r * F48S15)), z + ((r * F48S15) + (r * F48C15)))
                + n.noise(x + ((r * F48C16) - (r * F48S16)), z + ((r * F48S16) + (r * F48C16)))
                + n.noise(x + ((r * F48C17) - (r * F48S17)), z + ((r * F48S17) + (r * F48C17)))
                + n.noise(x + ((r * F48C18) - (r * F48S18)), z + ((r * F48S18) + (r * F48C18)))
                + n.noise(x + ((r * F48C19) - (r * F48S19)), z + ((r * F48S19) + (r * F48C19)))
                + n.noise(x + ((r * F48C20) - (r * F48S20)), z + ((r * F48S20) + (r * F48C20)))
                + n.noise(x + ((r * F48C21) - (r * F48S21)), z + ((r * F48S21) + (r * F48C21)))
                + n.noise(x + ((r * F48C22) - (r * F48S22)), z + ((r * F48S22) + (r * F48C22)))
                + n.noise(x + ((r * F48C23) - (r * F48S23)), z + ((r * F48S23) + (r * F48C23)))
                + n.noise(x + ((r * F48C24) - (r * F48S24)), z + ((r * F48S24) + (r * F48C24)))
                + n.noise(x + ((r * F48C25) - (r * F48S25)), z + ((r * F48S25) + (r * F48C25)))
                + n.noise(x + ((r * F48C26) - (r * F48S26)), z + ((r * F48S26) + (r * F48C26)))
                + n.noise(x + ((r * F48C27) - (r * F48S27)), z + ((r * F48S27) + (r * F48C27)))
                + n.noise(x + ((r * F48C28) - (r * F48S28)), z + ((r * F48S28) + (r * F48C28)))
                + n.noise(x + ((r * F48C29) - (r * F48S29)), z + ((r * F48S29) + (r * F48C29)))
                + n.noise(x + ((r * F48C30) - (r * F48S30)), z + ((r * F48S30) + (r * F48C30)))
                + n.noise(x + ((r * F48C31) - (r * F48S31)), z + ((r * F48S31) + (r * F48C31)))
                + n.noise(x + ((r * F48C32) - (r * F48S32)), z + ((r * F48S32) + (r * F48C32)))
                + n.noise(x + ((r * F48C33) - (r * F48S33)), z + ((r * F48S33) + (r * F48C33)))
                + n.noise(x + ((r * F48C34) - (r * F48S34)), z + ((r * F48S34) + (r * F48C34)))
                + n.noise(x + ((r * F48C35) - (r * F48S35)), z + ((r * F48S35) + (r * F48C35)))
                + n.noise(x + ((r * F48C36) - (r * F48S36)), z + ((r * F48S36) + (r * F48C36)))
                + n.noise(x + ((r * F48C37) - (r * F48S37)), z + ((r * F48S37) + (r * F48C37)))
                + n.noise(x + ((r * F48C38) - (r * F48S38)), z + ((r * F48S38) + (r * F48C38)))
                + n.noise(x + ((r * F48C39) - (r * F48S39)), z + ((r * F48S39) + (r * F48C39)))
                + n.noise(x + ((r * F48C40) - (r * F48S40)), z + ((r * F48S40) + (r * F48C40)))
                + n.noise(x + ((r * F48C41) - (r * F48S41)), z + ((r * F48S41) + (r * F48C41)))
                + n.noise(x + ((r * F48C42) - (r * F48S42)), z + ((r * F48S42) + (r * F48C42)))
                + n.noise(x + ((r * F48C43) - (r * F48S43)), z + ((r * F48S43) + (r * F48C43)))
                + n.noise(x + ((r * F48C44) - (r * F48S44)), z + ((r * F48S44) + (r * F48C44)))
                + n.noise(x + ((r * F48C45) - (r * F48S45)), z + ((r * F48S45) + (r * F48C45)))
                + n.noise(x + ((r * F48C46) - (r * F48S46)), z + ((r * F48S46) + (r * F48C46)))
                + n.noise(x + ((r * F48C47) - (r * F48S47)), z + ((r * F48S47) + (r * F48C47)))
                + n.noise(x + ((r * F48C48) - (r * F48S48)), z + ((r * F48S48) + (r * F48C48)))
                + n.noise(x + ((r * F48C49) - (r * F48S49)), z + ((r * F48S49) + (r * F48C49)))
                + n.noise(x + ((r * F48C50) - (r * F48S50)), z + ((r * F48S50) + (r * F48C50)))
                + n.noise(x + ((r * F48C51) - (r * F48S51)), z + ((r * F48S51) + (r * F48C51)))) / 48.0D;
    }

    private static final double F64C0 = 1;
    private static final double F64S0 = 0;
    private static final double F64C1 = 0.9961946980917455451987052583717741072177886962890625;
    private static final double F64S1 = 0.0871557427476581658698506771543179638683795928955078125;
    private static final double F64C2 = 0.98480775301220802031565426659653894603252410888671875;
    private static final double F64S2 = 0.1736481776669303311866343619840336032211780548095703125;
    private static final double F64C3 = 0.96592582628906831221371476203785277903079986572265625;
    private static final double F64S3 = 0.25881904510252073947640383266843855381011962890625;
    private static final double F64C4 = 0.939692620785908427905042117345146834850311279296875;
    private static final double F64S4 = 0.342020143325668712908083080037613399326801300048828125;
    private static final double F64C5 = 0.90630778703664993667388216636027209460735321044921875;
    private static final double F64S5 = 0.422618261740699441286750470680999569594860076904296875;
    private static final double F64C6 = 0.86602540378443870761060452423407696187496185302734375;
    private static final double F64S6 = 0.499999999999999944488848768742172978818416595458984375;
    private static final double F64C7 = 0.81915204428899179855960710483486764132976531982421875;
    private static final double F64S7 = 0.57357643635104604840080355643294751644134521484375;
    private static final double F64C8 = 0.76604444311897801345168090847437269985675811767578125;
    private static final double F64S8 = 0.642787609686539251896419955301098525524139404296875;
    private static final double F64C9 = 0.70710678118654757273731092936941422522068023681640625;
    private static final double F64S9 = 0.707106781186547461715008466853760182857513427734375;
    private static final double F64C10 = 0.64278760968653936291872241781675256788730621337890625;
    private static final double F64S10 = 0.76604444311897801345168090847437269985675811767578125;
    private static final double F64C11 = 0.57357643635104615942310601894860155880451202392578125;
    private static final double F64S11 = 0.81915204428899179855960710483486764132976531982421875;
    private static final double F64C12 = 0.50000000000000011102230246251565404236316680908203125;
    private static final double F64S12 = 0.8660254037844385965883020617184229195117950439453125;
    private static final double F64C13 = 0.422618261740699441286750470680999569594860076904296875;
    private static final double F64S13 = 0.90630778703664993667388216636027209460735321044921875;
    private static final double F64C14 = 0.342020143325668823930385542553267441689968109130859375;
    private static final double F64S14 = 0.93969262078590831688273965482949279248714447021484375;
    private static final double F64C15 = 0.25881904510252073947640383266843855381011962890625;
    private static final double F64S15 = 0.96592582628906831221371476203785277903079986572265625;
    private static final double F64C16 = 0.17364817766693041445336120887077413499355316162109375;
    private static final double F64S16 = 0.98480775301220802031565426659653894603252410888671875;
    private static final double F64C17 = 0.087155742747658138114275061525404453277587890625;
    private static final double F64S17 = 0.9961946980917455451987052583717741072177886962890625;
    private static final double F64C18 = 0.0000000000000000612323399573676603586882014729198302312846062338790031898128063403419218957424163818359375;
    private static final double F64S18 = 1;
    private static final double F64C19 = -0.08715574274765823525878971622660174034535884857177734375;
    private static final double F64S19 = 0.9961946980917455451987052583717741072177886962890625;
    private static final double F64C20 = -0.1736481776669303034310587463551200926303863525390625;
    private static final double F64S20 = 0.98480775301220802031565426659653894603252410888671875;
    private static final double F64C21 = -0.25881904510252085049870629518409259617328643798828125;
    private static final double F64S21 = 0.96592582628906831221371476203785277903079986572265625;
    private static final double F64C22 = -0.342020143325668712908083080037613399326801300048828125;
    private static final double F64S22 = 0.939692620785908427905042117345146834850311279296875;
    private static final double F64C23 = -0.422618261740699330264448008165345527231693267822265625;
    private static final double F64S23 = 0.90630778703665004769618462887592613697052001953125;
    private static final double F64C24 = -0.4999999999999997779553950749686919152736663818359375;
    private static final double F64S24 = 0.86602540378443870761060452423407696187496185302734375;
    private static final double F64C25 = -0.57357643635104615942310601894860155880451202392578125;
    private static final double F64S25 = 0.8191520442889916875373046423192135989665985107421875;
    private static final double F64C26 = -0.64278760968653936291872241781675256788730621337890625;
    private static final double F64S26 = 0.76604444311897801345168090847437269985675811767578125;
    private static final double F64C27 = -0.707106781186547461715008466853760182857513427734375;
    private static final double F64S27 = 0.70710678118654757273731092936941422522068023681640625;
    private static final double F64C28 = -0.76604444311897790242937844595871865749359130859375;
    private static final double F64S28 = 0.6427876096865394739410248803324066102504730224609375;
    private static final double F64C29 = -0.81915204428899190958190956735052168369293212890625;
    private static final double F64S29 = 0.57357643635104593737850109391729347407817840576171875;
    private static final double F64C30 = -0.86602540378443870761060452423407696187496185302734375;
    private static final double F64S30 = 0.499999999999999944488848768742172978818416595458984375;
    private static final double F64C31 = -0.90630778703664993667388216636027209460735321044921875;
    private static final double F64S31 = 0.4226182617406994967979017019388265907764434814453125;
    private static final double F64C32 = -0.93969262078590831688273965482949279248714447021484375;
    private static final double F64S32 = 0.342020143325668879441536773811094462871551513671875;
    private static final double F64C33 = -0.965925826289068201191412299522198736667633056640625;
    private static final double F64S33 = 0.258819045102521017032159988957573659718036651611328125;
    private static final double F64C34 = -0.98480775301220802031565426659653894603252410888671875;
    private static final double F64S34 = 0.1736481776669302756754831307262065820395946502685546875;
    private static final double F64C35 = -0.9961946980917455451987052583717741072177886962890625;
    private static final double F64S35 = 0.087155742747658193625426292783231474459171295166015625;
    private static final double F64C36 = -1;
    private static final double F64S36 = 0.000000000000000122464679914735320717376402945839660462569212467758006379625612680683843791484832763671875;
    private static final double F64C37 = -0.9961946980917455451987052583717741072177886962890625;
    private static final double F64S37 = -0.0871557427476579438252457521230098791420459747314453125;
    private static final double F64C38 = -0.98480775301220802031565426659653894603252410888671875;
    private static final double F64S38 = -0.173648177666930469964512440128601156175136566162109375;
    private static final double F64C39 = -0.96592582628906831221371476203785277903079986572265625;
    private static final double F64S39 = -0.258819045102520794987555063926265574991703033447265625;
    private static final double F64C40 = -0.939692620785908427905042117345146834850311279296875;
    private static final double F64S40 = -0.3420201433256686573969318487797863781452178955078125;
    private static final double F64C41 = -0.90630778703665004769618462887592613697052001953125;
    private static final double F64S41 = -0.42261826174069927475329677690751850605010986328125;
    private static final double F64C42 = -0.8660254037844385965883020617184229195117950439453125;
    private static final double F64S42 = -0.50000000000000011102230246251565404236316680908203125;
    private static final double F64C43 = -0.81915204428899179855960710483486764132976531982421875;
    private static final double F64S43 = -0.57357643635104615942310601894860155880451202392578125;
    private static final double F64C44 = -0.76604444311897801345168090847437269985675811767578125;
    private static final double F64S44 = -0.642787609686539251896419955301098525524139404296875;
    private static final double F64C45 = -0.7071067811865476837596133918850682675838470458984375;
    private static final double F64S45 = -0.707106781186547461715008466853760182857513427734375;
    private static final double F64C46 = -0.6427876096865394739410248803324066102504730224609375;
    private static final double F64S46 = -0.76604444311897790242937844595871865749359130859375;
    private static final double F64C47 = -0.57357643635104638146771094397990964353084564208984375;
    private static final double F64S47 = -0.81915204428899157651500217980355955660343170166015625;
    private static final double F64C48 = -0.500000000000000444089209850062616169452667236328125;
    private static final double F64S48 = -0.86602540378443837454369713668711483478546142578125;
    private static final double F64C49 = -0.42261826174069916373099431439186446368694305419921875;
    private static final double F64S49 = -0.90630778703665004769618462887592613697052001953125;
    private static final double F64C50 = -0.34202014332566854637462938626413233578205108642578125;
    private static final double F64S50 = -0.939692620785908427905042117345146834850311279296875;
    private static final double F64C51 = -0.25881904510252062845410137015278451144695281982421875;
    private static final double F64S51 = -0.96592582628906831221371476203785277903079986572265625;
    private static final double F64C52 = -0.1736481776669303311866343619840336032211780548095703125;
    private static final double F64S52 = -0.98480775301220802031565426659653894603252410888671875;
    private static final double F64C53 = -0.08715574274765824913657752404105849564075469970703125;
    private static final double F64S53 = -0.9961946980917455451987052583717741072177886962890625;
    private static final double F64C54 = -0.00000000000000018369701987210296875011296034045003113559498615810217092558787044254131615161895751953125;
    private static final double F64S54 = -1;
    private static final double F64C55 = 0.0871557427476578883140945208651828579604625701904296875;
    private static final double F64S55 = -0.9961946980917455451987052583717741072177886962890625;
    private static final double F64C56 = 0.17364817766692997036415135880815796554088592529296875;
    private static final double F64S56 = -0.98480775301220813133795672911219298839569091796875;
    private static final double F64C57 = 0.258819045102520295387193982605822384357452392578125;
    private static final double F64S57 = -0.9659258262890684232360172245535068213939666748046875;
    private static final double F64C58 = 0.34202014332566899046383923632674850523471832275390625;
    private static final double F64S58 = -0.93969262078590831688273965482949279248714447021484375;
    private static final double F64C59 = 0.42261826174069960782020416445448063313961029052734375;
    private static final double F64S59 = -0.90630778703664993667388216636027209460735321044921875;
    private static final double F64C60 = 0.50000000000000011102230246251565404236316680908203125;
    private static final double F64S60 = -0.8660254037844385965883020617184229195117950439453125;
    private static final double F64C61 = 0.57357643635104604840080355643294751644134521484375;
    private static final double F64S61 = -0.81915204428899179855960710483486764132976531982421875;
    private static final double F64C62 = 0.642787609686539251896419955301098525524139404296875;
    private static final double F64S62 = -0.7660444431189781244739833709900267422199249267578125;
    private static final double F64C63 = 0.70710678118654735069270600433810614049434661865234375;
    private static final double F64S63 = -0.7071067811865476837596133918850682675838470458984375;
    private static final double F64C64 = 0.76604444311897779140707598344306461513042449951171875;
    private static final double F64S64 = -0.64278760968653958496332734284806065261363983154296875;
    private static final double F64C65 = 0.81915204428899157651500217980355955660343170166015625;
    private static final double F64S65 = -0.573576436351046492490013406495563685894012451171875;
    private static final double F64C66 = 0.86602540378443837454369713668711483478546142578125;
    private static final double F64S66 = -0.500000000000000444089209850062616169452667236328125;
    private static final double F64C67 = 0.90630778703665004769618462887592613697052001953125;
    private static final double F64S67 = -0.422618261740699219242145545649691484868526458740234375;
    private static final double F64C68 = 0.939692620785908427905042117345146834850311279296875;
    private static final double F64S68 = -0.342020143325668601885780617521959356963634490966796875;
    private static final double F64C69 = 0.96592582628906831221371476203785277903079986572265625;
    private static final double F64S69 = -0.258819045102520683965252601410611532628536224365234375;
    private static final double F64C70 = 0.98480775301220802031565426659653894603252410888671875;
    private static final double F64S70 = -0.1736481776669303866977855932418606244027614593505859375;
    private static final double F64C71 = 0.9961946980917455451987052583717741072177886962890625;
    private static final double F64S71 = -0.08715574274765831852551656311334227211773395538330078125;

    private static double sc64(int x, int z, double r, NoiseProvider n) {
        return (n.noise(x + ((r * F64C0) - (r * F64S0)), z + ((r * F64S0) + (r * F64C0)))
                + n.noise(x + ((r * F64C1) - (r * F64S1)), z + ((r * F64S1) + (r * F64C1)))
                + n.noise(x + ((r * F64C2) - (r * F64S2)), z + ((r * F64S2) + (r * F64C2)))
                + n.noise(x + ((r * F64C3) - (r * F64S3)), z + ((r * F64S3) + (r * F64C3)))
                + n.noise(x + ((r * F64C4) - (r * F64S4)), z + ((r * F64S4) + (r * F64C4)))
                + n.noise(x + ((r * F64C5) - (r * F64S5)), z + ((r * F64S5) + (r * F64C5)))
                + n.noise(x + ((r * F64C6) - (r * F64S6)), z + ((r * F64S6) + (r * F64C6)))
                + n.noise(x + ((r * F64C7) - (r * F64S7)), z + ((r * F64S7) + (r * F64C7)))
                + n.noise(x + ((r * F64C8) - (r * F64S8)), z + ((r * F64S8) + (r * F64C8)))
                + n.noise(x + ((r * F64C9) - (r * F64S9)), z + ((r * F64S9) + (r * F64C9)))
                + n.noise(x + ((r * F64C10) - (r * F64S10)), z + ((r * F64S10) + (r * F64C10)))
                + n.noise(x + ((r * F64C11) - (r * F64S11)), z + ((r * F64S11) + (r * F64C11)))
                + n.noise(x + ((r * F64C12) - (r * F64S12)), z + ((r * F64S12) + (r * F64C12)))
                + n.noise(x + ((r * F64C13) - (r * F64S13)), z + ((r * F64S13) + (r * F64C13)))
                + n.noise(x + ((r * F64C14) - (r * F64S14)), z + ((r * F64S14) + (r * F64C14)))
                + n.noise(x + ((r * F64C15) - (r * F64S15)), z + ((r * F64S15) + (r * F64C15)))
                + n.noise(x + ((r * F64C16) - (r * F64S16)), z + ((r * F64S16) + (r * F64C16)))
                + n.noise(x + ((r * F64C17) - (r * F64S17)), z + ((r * F64S17) + (r * F64C17)))
                + n.noise(x + ((r * F64C18) - (r * F64S18)), z + ((r * F64S18) + (r * F64C18)))
                + n.noise(x + ((r * F64C19) - (r * F64S19)), z + ((r * F64S19) + (r * F64C19)))
                + n.noise(x + ((r * F64C20) - (r * F64S20)), z + ((r * F64S20) + (r * F64C20)))
                + n.noise(x + ((r * F64C21) - (r * F64S21)), z + ((r * F64S21) + (r * F64C21)))
                + n.noise(x + ((r * F64C22) - (r * F64S22)), z + ((r * F64S22) + (r * F64C22)))
                + n.noise(x + ((r * F64C23) - (r * F64S23)), z + ((r * F64S23) + (r * F64C23)))
                + n.noise(x + ((r * F64C24) - (r * F64S24)), z + ((r * F64S24) + (r * F64C24)))
                + n.noise(x + ((r * F64C25) - (r * F64S25)), z + ((r * F64S25) + (r * F64C25)))
                + n.noise(x + ((r * F64C26) - (r * F64S26)), z + ((r * F64S26) + (r * F64C26)))
                + n.noise(x + ((r * F64C27) - (r * F64S27)), z + ((r * F64S27) + (r * F64C27)))
                + n.noise(x + ((r * F64C28) - (r * F64S28)), z + ((r * F64S28) + (r * F64C28)))
                + n.noise(x + ((r * F64C29) - (r * F64S29)), z + ((r * F64S29) + (r * F64C29)))
                + n.noise(x + ((r * F64C30) - (r * F64S30)), z + ((r * F64S30) + (r * F64C30)))
                + n.noise(x + ((r * F64C31) - (r * F64S31)), z + ((r * F64S31) + (r * F64C31)))
                + n.noise(x + ((r * F64C32) - (r * F64S32)), z + ((r * F64S32) + (r * F64C32)))
                + n.noise(x + ((r * F64C33) - (r * F64S33)), z + ((r * F64S33) + (r * F64C33)))
                + n.noise(x + ((r * F64C34) - (r * F64S34)), z + ((r * F64S34) + (r * F64C34)))
                + n.noise(x + ((r * F64C35) - (r * F64S35)), z + ((r * F64S35) + (r * F64C35)))
                + n.noise(x + ((r * F64C36) - (r * F64S36)), z + ((r * F64S36) + (r * F64C36)))
                + n.noise(x + ((r * F64C37) - (r * F64S37)), z + ((r * F64S37) + (r * F64C37)))
                + n.noise(x + ((r * F64C38) - (r * F64S38)), z + ((r * F64S38) + (r * F64C38)))
                + n.noise(x + ((r * F64C39) - (r * F64S39)), z + ((r * F64S39) + (r * F64C39)))
                + n.noise(x + ((r * F64C40) - (r * F64S40)), z + ((r * F64S40) + (r * F64C40)))
                + n.noise(x + ((r * F64C41) - (r * F64S41)), z + ((r * F64S41) + (r * F64C41)))
                + n.noise(x + ((r * F64C42) - (r * F64S42)), z + ((r * F64S42) + (r * F64C42)))
                + n.noise(x + ((r * F64C43) - (r * F64S43)), z + ((r * F64S43) + (r * F64C43)))
                + n.noise(x + ((r * F64C44) - (r * F64S44)), z + ((r * F64S44) + (r * F64C44)))
                + n.noise(x + ((r * F64C45) - (r * F64S45)), z + ((r * F64S45) + (r * F64C45)))
                + n.noise(x + ((r * F64C46) - (r * F64S46)), z + ((r * F64S46) + (r * F64C46)))
                + n.noise(x + ((r * F64C47) - (r * F64S47)), z + ((r * F64S47) + (r * F64C47)))
                + n.noise(x + ((r * F64C48) - (r * F64S48)), z + ((r * F64S48) + (r * F64C48)))
                + n.noise(x + ((r * F64C49) - (r * F64S49)), z + ((r * F64S49) + (r * F64C49)))
                + n.noise(x + ((r * F64C50) - (r * F64S50)), z + ((r * F64S50) + (r * F64C50)))
                + n.noise(x + ((r * F64C51) - (r * F64S51)), z + ((r * F64S51) + (r * F64C51)))
                + n.noise(x + ((r * F64C52) - (r * F64S52)), z + ((r * F64S52) + (r * F64C52)))
                + n.noise(x + ((r * F64C53) - (r * F64S53)), z + ((r * F64S53) + (r * F64C53)))
                + n.noise(x + ((r * F64C54) - (r * F64S54)), z + ((r * F64S54) + (r * F64C54)))
                + n.noise(x + ((r * F64C55) - (r * F64S55)), z + ((r * F64S55) + (r * F64C55)))
                + n.noise(x + ((r * F64C56) - (r * F64S56)), z + ((r * F64S56) + (r * F64C56)))
                + n.noise(x + ((r * F64C57) - (r * F64S57)), z + ((r * F64S57) + (r * F64C57)))
                + n.noise(x + ((r * F64C58) - (r * F64S58)), z + ((r * F64S58) + (r * F64C58)))
                + n.noise(x + ((r * F64C59) - (r * F64S59)), z + ((r * F64S59) + (r * F64C59)))
                + n.noise(x + ((r * F64C60) - (r * F64S60)), z + ((r * F64S60) + (r * F64C60)))
                + n.noise(x + ((r * F64C61) - (r * F64S61)), z + ((r * F64S61) + (r * F64C61)))
                + n.noise(x + ((r * F64C62) - (r * F64S62)), z + ((r * F64S62) + (r * F64C62)))
                + n.noise(x + ((r * F64C63) - (r * F64S63)), z + ((r * F64S63) + (r * F64C63)))
                + n.noise(x + ((r * F64C64) - (r * F64S64)), z + ((r * F64S64) + (r * F64C64)))
                + n.noise(x + ((r * F64C65) - (r * F64S65)), z + ((r * F64S65) + (r * F64C65)))
                + n.noise(x + ((r * F64C66) - (r * F64S66)), z + ((r * F64S66) + (r * F64C66)))
                + n.noise(x + ((r * F64C67) - (r * F64S67)), z + ((r * F64S67) + (r * F64C67)))
                + n.noise(x + ((r * F64C68) - (r * F64S68)), z + ((r * F64S68) + (r * F64C68)))
                + n.noise(x + ((r * F64C69) - (r * F64S69)), z + ((r * F64S69) + (r * F64C69)))
                + n.noise(x + ((r * F64C70) - (r * F64S70)), z + ((r * F64S70) + (r * F64C70)))
                + n.noise(x + ((r * F64C71) - (r * F64S71)), z + ((r * F64S71) + (r * F64C71)))) / 64.0D;
    }
}

"""Reference values for WorldRandomTest and StageStreamsTest, computed from the specification in
WorldRandom's and StageStreams' documentation with Python integers and IEEE doubles, outside Java.

Run from IrisApi: python3 -B src/test/reference/world_random.py
"""
import math
import struct

MASK64 = (1 << 64) - 1
GOLDEN_GAMMA = 0x9E3779B97F4A7C15


def signed64(value):
    value &= MASK64
    return value - (1 << 64) if value >= 1 << 63 else value


def signed32(value):
    value &= (1 << 32) - 1
    return value - (1 << 32) if value >= 1 << 31 else value


def mix(z):
    z = ((z ^ (z >> 30)) * 0xBF58476D1CE4E5B9) & MASK64
    z = ((z ^ (z >> 27)) * 0x94D049BB133111EB) & MASK64
    return z ^ (z >> 31)


def fnv1a64(text):
    value = 0xCBF29CE484222325
    for byte in text.encode("utf-8"):
        value = ((value ^ byte) * 0x100000001B3) & MASK64
    return value


def stream_seed(invocation_seed, stage_id):
    return mix(mix(invocation_seed & MASK64) ^ fnv1a64(stage_id))


# fdlibm __ieee754_log (e_log.c), the algorithm StrictMath.log specifies.
LN2_HI = 6.93147180369123816490e-01
LN2_LO = 1.90821492927058770002e-10
TWO54 = 1.80143985094819840000e+16
LG = [6.666666666666735130e-01, 3.999999999940941908e-01, 2.857142874366239149e-01,
      2.222219843214978396e-01, 1.818357216161805012e-01, 1.531383769920937332e-01,
      1.479819860511658591e-01]


def high_word(x):
    return signed32(struct.unpack("<Q", struct.pack("<d", x))[0] >> 32)


def low_word(x):
    return struct.unpack("<Q", struct.pack("<d", x))[0] & 0xFFFFFFFF


def with_high_word(x, high):
    return struct.unpack("<d", struct.pack("<Q", ((high & 0xFFFFFFFF) << 32) | low_word(x)))[0]


def fdlibm_log(x):
    hx, lx = high_word(x), low_word(x)
    k = 0
    if hx < 0x00100000:
        if ((hx & 0x7FFFFFFF) | lx) == 0:
            return float("-inf")
        if hx < 0:
            return float("nan")
        k -= 54
        x *= TWO54
        hx = high_word(x)
    if hx >= 0x7FF00000:
        return x + x
    k += (hx >> 20) - 1023
    hx &= 0x000FFFFF
    i = (hx + 0x95F64) & 0x100000
    x = with_high_word(x, hx | (i ^ 0x3FF00000))
    k += i >> 20
    f = x - 1.0
    if (0x000FFFFF & (2 + hx)) < 3:
        if f == 0.0:
            if k == 0:
                return 0.0
            dk = float(k)
            return dk * LN2_HI + dk * LN2_LO
        r = f * f * (0.5 - 0.33333333333333333 * f)
        if k == 0:
            return f - r
        dk = float(k)
        return dk * LN2_HI - ((r - dk * LN2_LO) - f)
    s = f / (2.0 + f)
    dk = float(k)
    z = s * s
    i = hx - 0x6147A
    w = z * z
    j = 0x6B851 - hx
    t1 = w * (LG[1] + w * (LG[3] + w * LG[5]))
    t2 = z * (LG[0] + w * (LG[2] + w * (LG[4] + w * LG[6])))
    i |= j
    r = t2 + t1
    if i > 0:
        hfsq = 0.5 * f * f
        if k == 0:
            return f - (hfsq - s * (hfsq + r))
        return dk * LN2_HI - ((hfsq - (s * (hfsq + r) + dk * LN2_LO)) - f)
    if k == 0:
        return f - s * (f - r)
    return dk * LN2_HI - ((s * (f - r) - dk * LN2_LO) - f)


class WorldRandom:
    def __init__(self, seed):
        self.state = seed & MASK64

    def raw(self):
        """The next output as an unsigned 64-bit integer."""
        self.state = (self.state + GOLDEN_GAMMA) & MASK64
        return mix(self.state)

    def next_long(self):
        return signed64(self.raw())

    def next_int(self):
        return signed32(self.raw() >> 32)

    def bounded(self, width, bits):
        """Lemire: high half of x * width, rejecting while the low half is below 2^bits mod width."""
        threshold = (1 << bits) % width
        while True:
            x = self.raw() >> (64 - bits)
            product = x * width
            if product % (1 << bits) >= threshold:
                return product >> bits

    def next_int_bound(self, bound):
        return self.bounded(bound, 32)

    def next_int_range(self, origin, bound):
        return origin + self.bounded(bound - origin, 32)

    def next_long_bound(self, bound):
        return self.bounded(bound, 64)

    def next_long_range(self, origin, bound):
        return origin + self.bounded(bound - origin, 64)

    def next_boolean(self):
        return self.raw() >> 63 == 1

    def next_float(self):
        return (self.raw() >> 40) * 2.0 ** -24

    def next_double(self):
        return (self.raw() >> 11) * 2.0 ** -53

    def next_double_bound(self, bound):
        value = self.next_double() * bound
        return value if value < bound else below(bound)

    def next_double_range(self, origin, bound):
        unit = self.next_double()
        width = bound - origin
        if width != float("inf"):
            value = origin + unit * width
        else:
            value = 2 * (origin / 2 + unit * (bound / 2 - origin / 2))
        return value if value < bound else below(bound)

    def next_gaussian(self):
        while True:
            u = 2 * self.next_double() - 1
            v = 2 * self.next_double() - 1
            s = u * u + v * v
            if 0 < s < 1:
                return u * math.sqrt(-2 * fdlibm_log(s) / s)

    def chance(self, probability):
        return self.next_double() < probability

    def pick(self, items):
        return items[self.next_int_bound(len(items))]

    def shuffle(self, items):
        """Fisher-Yates from the last index down to 1, swapping each with a nextInt(i + 1) index."""
        for i in range(len(items) - 1, 0, -1):
            j = self.next_int_bound(i + 1)
            items[i], items[j] = items[j], items[i]


class CountingRandom(WorldRandom):
    def __init__(self, seed):
        super().__init__(seed)
        self.draws = 0

    def raw(self):
        self.draws += 1
        return super().raw()


def below(value):
    bits = struct.unpack("<q", struct.pack("<d", value))[0]
    return struct.unpack("<d", struct.pack("<q", bits - 1 if value > 0 else bits + 1))[0]


def draws(label, method, count=6, seed=42):
    random = WorldRandom(seed)
    print(f"{label}: {[method(random) for _ in range(count)]}")


if __name__ == "__main__":
    print(f"fnv1a64(iris:stilt/fill) = {signed64(fnv1a64('iris:stilt/fill'))}")
    print(f"streamSeed(42, iris:stilt/fill) = {signed64(stream_seed(42, 'iris:stilt/fill'))}")
    stage = WorldRandom(stream_seed(42, "iris:stilt/fill"))
    print(f"stage stream nextLong, nextInt, nextInt(1000000): "
          f"{stage.next_long()}, {stage.next_int()}, {stage.next_int_bound(1_000_000)}")

    draws("nextLong()", WorldRandom.next_long)
    draws("nextInt()", WorldRandom.next_int)
    draws("nextInt(10)", lambda r: r.next_int_bound(10))
    draws("nextInt(-5, 5)", lambda r: r.next_int_range(-5, 5))
    # width 3 * 2^30: one draw in four is rejected.
    draws("nextInt(-2^31, 2^30)", lambda r: r.next_int_range(-(1 << 31), 1 << 30), count=12)
    draws("nextInt(MIN, MAX)", lambda r: r.next_int_range(-(1 << 31), (1 << 31) - 1))
    draws("nextLong(1000)", lambda r: r.next_long_bound(1000))
    draws("nextLong(-1000, 1000)", lambda r: r.next_long_range(-1000, 1000))
    # width 3 * 2^62: one draw in four is rejected.
    draws("nextLong(-2^63, 2^62)", lambda r: r.next_long_range(-(1 << 63), 1 << 62), count=12)
    draws("nextLong(MIN, MAX)", lambda r: r.next_long_range(-(1 << 63), (1 << 63) - 1))
    draws("nextBoolean()", WorldRandom.next_boolean, count=12)
    draws("nextFloat()", WorldRandom.next_float)
    draws("nextDouble()", WorldRandom.next_double)
    draws("nextDouble(10.0)", lambda r: r.next_double_bound(10.0))
    draws("nextDouble(-2.5, 7.5)", lambda r: r.next_double_range(-2.5, 7.5))
    max_double = 1.7976931348623157e308
    draws("nextDouble(-MAX, MAX)", lambda r: r.next_double_range(-max_double, max_double))
    draws("nextGaussian()", WorldRandom.next_gaussian)
    draws("chance(0.25)", lambda r: r.chance(0.25), count=12)
    draws("pick(a..j)", lambda r: r.pick("abcdefghij"))
    for size in (0, 1, 2, 10):
        random = CountingRandom(42)
        items = list(range(size))
        random.shuffle(items)
        print(f"shuffle(0..{size - 1}): {items}, {random.draws} draws")
    # The first nextInt(2) is 1, which keeps [0, 1]; the second is 0, which swaps.
    random = WorldRandom(42)
    pair = [0, 1]
    random.shuffle(pair)
    first = list(pair)
    random.shuffle(pair)
    print(f"shuffle([0, 1]) twice on one stream: {first}, {pair}")

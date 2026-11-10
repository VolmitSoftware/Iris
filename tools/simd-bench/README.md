# Iris SIMD Kernel Benchmark

Standalone, portable microbenchmark that measures whether Iris's SIMD (Java
Vector API) kernels actually beat their scalar equivalents **on the CPU it runs
on**. The Volmit dev/test machines are Apple Silicon, where the wide-SIMD path
(4+ double lanes) does not exist. Copy the built artifact to a Windows/x86 box
(AVX2 = 4 lanes, AVX-512 = 8 lanes) and run it to get real numbers for that CPU.

The three array kernel classes are copied verbatim (logic byte-for-byte) from
`Iris/core/.../util/simd/`. The three 2D noise kernel classes are a candidate
implementation that Iris does not ship: it measured 0.07x against scalar on
2-lane NEON and was removed, and this harness keeps it so the same question can be
answered on a wider CPU before anyone rebuilds it. This tool is intentionally a
duplicate so it builds and runs on its own with no Gradle, no VolmLib, and no Iris
on the classpath.

## Requirements

- **JDK 25** (the tool is compiled with `--release 25`).
- The JDK must ship the `jdk.incubator.vector` incubator module (Temurin,
  Oracle, and all standard OpenJDK builds do).

## How to run

Windows:

```
run.bat
```

macOS / Linux:

```
./run.sh
```

The run scripts compile the sources and create the ignored `simd-bench.jar` locally when it is
missing. Use `build.bat` or `./build.sh` to rebuild it explicitly.

Or invoke directly (the `--add-modules` flag is required because the Vector API
is still an incubator module):

```
java --add-modules jdk.incubator.vector -jar simd-bench.jar
```

### Mode flag

By default it runs `both` (scalar and vector head-to-head in one run). You can
also do a two-run A/B:

```
run.bat --mode scalar
run.bat --mode vector
run.bat --mode both      (default)
```

`--mode=scalar` syntax works too. The correctness cross-check only runs in
`both` mode (it needs both implementations).

## How to read the output

- **Header** prints the JVM, `os.arch`, CPU count, and the preferred vector
  width. `DoubleVector pref: N lanes` is the SIMD width for `double` on this CPU
  (2 on 128-bit NEON, 4 on AVX2, 8 on AVX-512).
- **noise SIMD gate (aligned && doubleLanes>=4)** mirrors the real profitability
  gate in Iris's `VectorNoiseKernels2D`. It reports ENABLED on 4+ lane CPUs and
  DISABLED on 2-lane NEON. **This tool ignores the gate and force-measures the
  raw vector kernel anyway**, so you see the real number even where Iris would
  gate SIMD off.
- **Correctness cross-check** runs each kernel once with both impls on identical
  input and confirms they agree (roundToInt/noise are bit-exact; `sum` differs
  only by floating-point reduction order, checked with a relative tolerance).
  If anything says MISMATCH, do not trust the timing numbers below it.
- **speedup = scalar ns/op / vector ns/op.** `> 1.0` means SIMD is faster on
  this CPU; `< 1.0` means SIMD is slower. Verdict column: SIMD FASTER / SIMD
  SLOWER / NEUTRAL (within ~5%).
- `ns/op` for the array kernels is one full-array invocation (256 or 1024
  doubles). For noise it is one 256-element `simplexFractalFBM` call.
- The trailing **Checksum** lines exist only to keep the JIT from deleting the
  measured work. Ignore their values.

## Harness notes (why the numbers are trustworthy)

- Each op is warmed up 50,000 times before timing so the JIT has compiled the
  hot path.
- Every kernel output is folded into a running checksum (printed at the end) so
  no measured call is dead-code-eliminated.
- One input element is perturbed per iteration (a cheap store keyed off the loop
  counter) so the JIT cannot hoist a "constant" result out of the timing loop.
  This perturbation is identical for scalar and vector, so the comparison stays
  fair; it adds a tiny fixed cost to both sides that very slightly compresses the
  ratio on the cheapest kernels.
- Each (kernel, impl) is timed over 10 rounds; the **minimum** ns/op is reported
  (least-noisy statistic for a microbenchmark).
- Scalar and vector see the same seeded input for a given kernel.

## Caveats

- This is an **isolated-kernel vacuum microbench**, not full worldgen. It says
  whether the raw kernel is faster on this CPU, not what end-to-end generation
  throughput will be (cache behavior, allocation, and surrounding code differ in
  the real engine).
- **Effort 1 array kernels** (`roundToInt` / `sum` / `max`) run unconditionally
  in real Iris. **Effort 2 noise** (`simplexFractalFBM`) is not part of
  Iris; this tool force-measures the candidate kernel regardless of the 4+ double
  lane gate.
- Vector-API auto-vectorization and cost depend heavily on the JDK version and
  CPU. Run on the actual target hardware; do not extrapolate across machines.

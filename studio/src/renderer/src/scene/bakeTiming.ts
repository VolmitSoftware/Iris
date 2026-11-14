import type { BakeRun } from '../bridge/types'

const NANOS_PER_MILLI = 1_000_000
const SECOND_IN_MILLIS = 1000
const FINE_MILLIS_DIGITS_BELOW = 10

/** How many times a bake is timed; the run in the middle is the one shown. */
export const BAKE_RUN_COUNT = 5

/** How long a bake stalls the chunk that first places it, against a server tick of 50 ms (v3 BakeWeight). */
export interface BakeWeight {
  label: string
  /** Where the weight starts, in milliseconds. */
  fromMillis: number
  /** Where the meter is full within the weight, in milliseconds. */
  meterFullAtMillis: number
  /** The text colour class of the weight. */
  textClass: string
  /** The fill colour class of the weight. */
  fillClass: string
}

export const BAKE_WEIGHTS: readonly BakeWeight[] = [
  { label: 'Light', fromMillis: 1, meterFullAtMillis: 50, textClass: 'text-success', fillClass: 'bg-success' },
  { label: 'Moderate', fromMillis: 50, meterFullAtMillis: 500, textClass: 'text-warning', fillClass: 'bg-warning' },
  { label: 'Heavy', fromMillis: 500, meterFullAtMillis: 5000, textClass: 'text-destructive', fillClass: 'bg-destructive' }
]

export function bakeWeightIndex(millis: number): number {
  const index = BAKE_WEIGHTS.findIndex((weight) => millis < weight.meterFullAtMillis)
  return index < 0 ? BAKE_WEIGHTS.length - 1 : index
}

export function bakeWeightOf(millis: number): BakeWeight {
  return BAKE_WEIGHTS[bakeWeightIndex(millis)]
}

/** How full the meter is, from 0 to 1: each weight takes an equal share, filled on a logarithmic scale since bakes span thousands of times. */
export function meterFill(millis: number): number {
  const index = bakeWeightIndex(millis)
  const weight = BAKE_WEIGHTS[index]
  const clamped = Math.min(Math.max(millis, weight.fromMillis), weight.meterFullAtMillis)
  const withinWeight = Math.log(clamped / weight.fromMillis) / Math.log(weight.meterFullAtMillis / weight.fromMillis)
  return (index + withinWeight) / BAKE_WEIGHTS.length
}

export function runMillis(run: BakeRun): number {
  return run.stages.reduce((total, stage) => total + stage.nanos, 0) / NANOS_PER_MILLI
}

/** The run in the middle by total time. The first run is slow while the JIT compiles the baker, and the garbage collector or chunk generation can slow any other, so neither ends up in the middle. */
export function medianRun(runs: readonly BakeRun[]): BakeRun {
  const byTime = [...runs].sort((first, second) => runMillis(first) - runMillis(second))
  return byTime[Math.floor(byTime.length / 2)]
}

/**
 * Times a bake {@link BAKE_RUN_COUNT} times, one run after the other, and keeps the median; null once {@code stillWanted}
 * says another entry was opened.
 */
export async function measureBake(bakeOnce: () => Promise<BakeRun>, stillWanted: () => boolean): Promise<BakeRun | null> {
  const runs: BakeRun[] = []
  while (runs.length < BAKE_RUN_COUNT) {
    if (!stillWanted()) {
      return null
    }
    runs.push(await bakeOnce())
  }
  return stillWanted() ? medianRun(runs) : null
}

export function formatMillis(millis: number): string {
  if (millis >= SECOND_IN_MILLIS) {
    return `${(millis / SECOND_IN_MILLIS).toFixed(1)} s`
  }
  return `${millis.toFixed(millis < FINE_MILLIS_DIGITS_BELOW ? 1 : 0)} ms`
}

/** Where light turns to heavy, as the tooltip tells it. */
export function weightScale(): string {
  return `Light under ${formatMillis(BAKE_WEIGHTS[1].fromMillis)}, heavy from ${formatMillis(BAKE_WEIGHTS[2].fromMillis)}`
}

/** The index of the stage that took longest; the first when none took any time. */
export function slowestStageIndex(run: BakeRun): number {
  return run.stages.reduce((slowest, stage, index) => (stage.nanos > run.stages[slowest].nanos ? index : slowest), 0)
}

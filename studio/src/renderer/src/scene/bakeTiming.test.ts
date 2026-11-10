import { describe, expect, it } from 'vitest'
import type { BakeRun } from '../bridge/types'
import { BAKE_RUN_COUNT, bakeWeightOf, formatMillis, measureBake, medianRun, meterFill, runMillis, slowestStageIndex } from './bakeTiming'

const NANOS_PER_MILLI = 1_000_000

function runOf(...stageMillis: number[]): BakeRun {
  return { variantCount: 4, stages: stageMillis.map((millis, index) => ({ label: `Stage ${index}`, nanos: millis * NANOS_PER_MILLI })) }
}

describe('bake timing', () => {
  it('adds the stages of a run up to its time in milliseconds', () => {
    expect(runMillis(runOf(2, 3, 5))).toBe(10)
  })

  it('keeps the run in the middle by time, not the fastest, the slowest or the first', () => {
    const slowFirstRun = runOf(900)
    const median = runOf(40)
    const runs = [slowFirstRun, runOf(10), runOf(300), median, runOf(25)]
    expect(medianRun(runs)).toBe(median)
    expect(runs[0]).toBe(slowFirstRun)
  })

  it('times five runs one after the other and keeps the median', async () => {
    const times = [90, 10, 50, 30, 70]
    let asked = 0
    const median = await measureBake(async () => runOf(times[asked++]), () => true)
    expect(asked).toBe(BAKE_RUN_COUNT)
    expect(median === null ? null : runMillis(median)).toBe(50)
  })

  it('stops asking once another entry is opened', async () => {
    let asked = 0
    const median = await measureBake(
      async () => runOf(asked++),
      () => asked < 2
    )
    expect(median).toBeNull()
    expect(asked).toBe(2)
  })

  it('drops the result of runs that finished after the entry was left', async () => {
    let wanted = true
    const median = await measureBake(
      async () => {
        wanted = false
        return runOf(1)
      },
      () => wanted
    )
    expect(median).toBeNull()
  })

  it('rates a bake against a server tick of 50 ms', () => {
    expect(bakeWeightOf(0.4).label).toBe('Light')
    expect(bakeWeightOf(49.9).label).toBe('Light')
    expect(bakeWeightOf(50).label).toBe('Moderate')
    expect(bakeWeightOf(500).label).toBe('Heavy')
    expect(bakeWeightOf(60_000).label).toBe('Heavy')
  })

  it('fills the meter on a logarithmic scale, a third for each weight', () => {
    expect(meterFill(0)).toBe(0)
    expect(meterFill(50)).toBeCloseTo(1 / 3)
    expect(meterFill(500)).toBeCloseTo(2 / 3)
    expect(meterFill(5000)).toBeCloseTo(1)
    expect(meterFill(1_000_000)).toBeCloseTo(1)
    expect(meterFill(Math.sqrt(50))).toBeCloseTo(1 / 6)
  })

  it('formats times with the digits they need', () => {
    expect(formatMillis(2.34)).toBe('2.3 ms')
    expect(formatMillis(48.6)).toBe('49 ms')
    expect(formatMillis(1250)).toBe('1.3 s')
  })

  it('points at the slowest stage, the first when none took time', () => {
    expect(slowestStageIndex(runOf(1, 9, 4))).toBe(1)
    expect(slowestStageIndex(runOf(0, 0))).toBe(0)
  })
})

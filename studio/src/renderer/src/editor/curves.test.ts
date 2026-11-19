import { describe, expect, it } from 'vitest'
import { canBecomeCurve, curveAt, curveFrom, isCurve, keyframesFrom, samplesOf, shapeFrom, withAddedKeyframe, withKeyframe, withoutKeyframe, withSetting, withShape } from './curves'

describe('isCurve', () => {
  it('holds only for keyframes and named shapes', () => {
    expect(isCurve([[0, 1.3], [1, 0.2]])).toBe(true)
    expect(isCurve({ shape: 'power', exponent: 3 })).toBe(true)
    expect(isCurve([15, 20])).toBe(false)
    expect(isCurve(4)).toBe(false)
    expect(isCurve({ min: 3, max: 8 })).toBe(false)
    expect(isCurve({ shape: 'zigzag' })).toBe(false)
  })
})

describe('samplesOf', () => {
  it('runs from the start of the curve to its end', () => {
    expect(samplesOf([[0, 1.3], [1, 0.2]], 3)).toEqual([1.3, 0.75, 0.2].map((expected) => expect.closeTo(expected, 9)))
  })

  it('has none for a value that is no curve', () => {
    expect(samplesOf(4, 3)).toBeNull()
  })

  it('follows a named curve between its from and to', () => {
    expect(curveAt({ shape: 'power', exponent: 2, from: 10, to: 20 }, 0.5)).toBeCloseTo(12.5, 9)
    expect(curveAt({ shape: 'linear' }, 0.25)).toBeCloseTo(0.25, 9)
  })

  it('holds the first and last values beyond the keyframes', () => {
    expect(curveAt([[0.2, 5], [0.8, 9]], 0)).toBe(5)
    expect(curveAt([[0.2, 5], [0.8, 9]], 1)).toBe(9)
  })
})

describe('curveFrom', () => {
  it('makes a number a flat curve and a range a rising one', () => {
    expect(curveFrom(4)).toEqual([[0, 4], [1, 4]])
    expect(curveFrom([15, 20])).toEqual([[0, 15], [1, 20]])
    expect(curveFrom({ min: 3, max: 8 })).toEqual([[0, 3], [1, 8]])
  })

  it('leaves what is not a number or range alone', () => {
    expect(curveFrom('wood')).toBeNull()
    expect(canBecomeCurve([[0, 1], [1, 2]])).toBe(false)
    expect(canBecomeCurve(4)).toBe(true)
  })
})

describe('changing how a curve is written', () => {
  it('turns keyframes into a linear shape between their ends, and back into sampled keyframes', () => {
    const shape = shapeFrom([[1, 8], [0, 2]])

    expect(shape).toEqual({ shape: 'linear', from: 2, to: 8 })
    expect(keyframesFrom(shape).map(([t, value]) => [t, value])).toEqual([[0, 2], [0.25, 3.5], [0.5, 5], [0.75, 6.5], [1, 8]])
  })

  it('sets a shape setting, or leaves it out so the default applies', () => {
    expect(withSetting({ shape: 'power', exponent: 3 }, 'exponent', 4)).toEqual({ shape: 'power', exponent: 4 })
    expect(withSetting({ shape: 'power', exponent: 3 }, 'exponent', undefined)).toEqual({ shape: 'power' })
  })

  it('keeps from and to when the shape changes, but not the old shape settings', () => {
    expect(withShape({ shape: 'power', exponent: 3, from: 1, to: 4 }, 'sine')).toEqual({ shape: 'sine', from: 1, to: 4 })
  })
})

describe('editing keyframes', () => {
  const keys: [number, number][] = [[0, 1], [1, 3]]

  it('adds a keyframe halfway across the widest gap, on the curve', () => {
    expect(withAddedKeyframe(keys)).toEqual([[0, 1], [0.5, 2], [1, 3]])
    expect(withAddedKeyframe([[0, 1], [0.25, 1], [1, 3]])[2]).toEqual([0.625, expect.closeTo(1 + (2 * 0.375) / 0.75, 9)])
  })

  it('gives a lone keyframe a partner at the other end', () => {
    expect(withAddedKeyframe([[0, 4]])).toEqual([[0, 4], [1, 4]])
  })

  it('sets and removes a keyframe but never removes the last one', () => {
    expect(withKeyframe(keys, 1, [0.9, 5])).toEqual([[0, 1], [0.9, 5]])
    expect(withoutKeyframe(keys, 0)).toEqual([[1, 3]])
    expect(withoutKeyframe([[1, 3]], 0)).toEqual([[1, 3]])
  })
})

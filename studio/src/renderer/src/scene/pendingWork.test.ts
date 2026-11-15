import { describe, expect, it } from 'vitest'
import { PendingWork } from './pendingWork'

describe('PendingWork', () => {
  it('measures against the most pending since last done', () => {
    const work = new PendingWork()
    expect(work.completion(200)).toBeCloseTo(0)
    expect(work.completion(50)).toBeCloseTo(0.75)
    expect(work.completion(0)).toBe(1)
  })

  it('starts over after the work is done', () => {
    const work = new PendingWork()
    work.completion(200)
    work.completion(0)
    expect(work.completion(10)).toBeCloseTo(0)
    expect(work.completion(5)).toBeCloseTo(0.5)
  })
})

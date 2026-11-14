import { describe, expect, it } from 'vitest'
import type { ServerUsage } from '../bridge/types'
import { cpuIsHigh, formatCpu, formatHeap, heapIsHigh } from './serverUsage'

const GIGABYTE = 1024 ** 3

function usageOf(cpuPercent: number | null, heapGigabytes: number): ServerUsage {
  return { cpuPercent, threads: 8, heapUsedBytes: heapGigabytes * GIGABYTE, heapMaxBytes: 10 * GIGABYTE }
}

describe('server usage', () => {
  it('shows the CPU in percent per busy thread, and a dash when the server cannot tell', () => {
    expect(formatCpu(usageOf(312.4, 1))).toBe('312%')
    expect(formatCpu(usageOf(null, 1))).toBe('–')
  })

  it('shows the heap in use of its maximum in gigabytes', () => {
    expect(formatHeap(usageOf(0, 3.25))).toBe('3.3 / 10.0 GB')
  })

  it('warns once four fifths of the threads are busy, not before', () => {
    expect(cpuIsHigh(usageOf(639, 1))).toBe(false)
    expect(cpuIsHigh(usageOf(640, 1))).toBe(true)
    expect(cpuIsHigh(usageOf(null, 1))).toBe(false)
  })

  it('warns once four fifths of the heap is in use, not before', () => {
    expect(heapIsHigh(usageOf(0, 7.9))).toBe(false)
    expect(heapIsHigh(usageOf(0, 8))).toBe(true)
  })
})

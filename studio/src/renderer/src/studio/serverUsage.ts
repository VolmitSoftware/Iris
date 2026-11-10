import type { ServerUsage } from '../bridge/types'

const HIGH_USAGE = 0.8
const PERCENT_PER_THREAD = 100
const BYTES_PER_GIGABYTE = 1024 ** 3

export function formatCpu(usage: ServerUsage): string {
  return usage.cpuPercent === null ? '–' : `${Math.round(usage.cpuPercent)}%`
}

export function formatHeap(usage: ServerUsage): string {
  return `${gigabytes(usage.heapUsedBytes)} / ${gigabytes(usage.heapMaxBytes)} GB`
}

/** Whether four fifths of the CPU's threads are busy. */
export function cpuIsHigh(usage: ServerUsage): boolean {
  return usage.cpuPercent !== null && usage.cpuPercent >= usage.threads * PERCENT_PER_THREAD * HIGH_USAGE
}

/** Whether four fifths of the heap is in use. */
export function heapIsHigh(usage: ServerUsage): boolean {
  return usage.heapUsedBytes >= usage.heapMaxBytes * HIGH_USAGE
}

function gigabytes(bytes: number): string {
  return (bytes / BYTES_PER_GIGABYTE).toFixed(1)
}

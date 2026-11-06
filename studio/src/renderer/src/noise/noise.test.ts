import { describe, expect, it } from 'vitest'
import type { NoiseSource } from '../bridge/types'
import { heightColor } from '../map/heightColors'
import { INVALID_NOISE_COLOR, noiseScale } from './noiseScale'
import { NoiseTerrain } from './noiseTerrain'

const UNIT_NOISE: NoiseSource = { id: 'style:SIMPLEX', name: 'Simplex', category: 'Simplex', usesSeed: true, blockRange: null }
const BLOCK_NOISE: NoiseSource = { id: 'world:overlay', name: 'Overlay noise', category: 'Pack generators', usesSeed: false, blockRange: { lowest: -20, highest: 40 } }

describe('noise scale', () => {
  it('spans the palette for noise of 0 to 1 and takes its end colours past it', () => {
    const terrain = noiseScale('terrain', UNIT_NOISE)
    expect([terrain.minimum, terrain.maximum]).toEqual([0, 1])
    expect(terrain.colorOf(0)).toBe(0x071a2f)
    expect(terrain.colorOf(1)).toBe(0xf4f1de)
    expect(terrain.colorOf(7)).toBe(0xf4f1de)
    expect(noiseScale('signed', UNIT_NOISE).minimum).toBe(-1)
  })

  it('spans the block range for noise in blocks, coloured as heights above the fluid', () => {
    const height = noiseScale('height', BLOCK_NOISE)
    expect([height.minimum, height.maximum]).toEqual([-20, 40])
    expect(height.colorOf(-20)).toBe(heightColor(-20, 0))
    expect(height.colorOf(40)).toBe(heightColor(40, 0))
  })

  it('marks a value the noise could not give', () => {
    expect(noiseScale('grayscale', UNIT_NOISE).colorOf(Number.NaN)).toBe(INVALID_NOISE_COLOR)
  })
})

describe('noise terrain', () => {
  const area = (values: number[]) => ({ tiles: [[{ size: 2, values: Float32Array.from(values) }]], originX: 10, originZ: 20, blocksPerValue: 4 })

  it('raises noise of 0 to 1 to the relief, without water', () => {
    const terrain = new NoiseTerrain(area([0, 0.5, 1, Number.NaN]), UNIT_NOISE, noiseScale('terrain', UNIT_NOISE), 64)
    expect([terrain.groundAt(0, 0), terrain.groundAt(1, 0), terrain.groundAt(0, 1), terrain.groundAt(1, 1)]).toEqual([0, 32, 64, null])
    expect(terrain.waterAt(0, 0)).toBeNull()
    expect([terrain.worldX(1), terrain.worldZ(1)]).toEqual([14, 24])
  })

  it('stands noise in blocks at its own height, under water below the fluid', () => {
    const terrain = new NoiseTerrain(area([-5, 12, 0, 3]), BLOCK_NOISE, noiseScale('height', BLOCK_NOISE), 64)
    expect(terrain.groundAt(0, 0)).toBe(-5)
    expect(terrain.waterAt(0, 0)).toBe(0)
    expect(terrain.waterAt(1, 0)).toBeNull()
  })
})

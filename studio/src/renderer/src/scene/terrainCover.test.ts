import { PerspectiveCamera, Vector3 } from 'three'
import { describe, expect, it } from 'vitest'
import { CameraSight } from './cameraSight'
import { planCover, type DrawnTile, type TileState } from './terrainCover'
import { addressOf, blocksPerColumn, columnsUnder, isInside, originXOf, originZOf, tileBlocks, tileKey, type TerrainTile } from './terrainTiles'

const COARSEST_LEVEL = 12
const LOOKED_AT = new Vector3(0, 64, 0)
const SAMPLE_RADIUS_BLOCKS = 300
const SAMPLE_STEP_BLOCKS = 23

// Looks down at the ground around the origin from {@code height} blocks above it, from the south.
function sightFrom(height: number): CameraSight {
  const camera = new PerspectiveCamera(45, 1.6, height * 0.01, height * 20)
  camera.position.set(0, 64 + height, height * 0.6)
  camera.lookAt(LOOKED_AT)
  camera.updateMatrixWorld()
  return new CameraSight({ camera, width: 1280, height: 800, orbitDistance: camera.position.distanceTo(LOOKED_AT) }, 0, 128)
}

const nothingReady = (): TileState => 'none'
const everythingReady = (): TileState => 'fresh'

function holeContains(draw: DrawnTile, x: number, z: number): boolean {
  const step = blocksPerColumn(draw.tile.level)
  const column = Math.floor((x - originXOf(draw.tile)) / step)
  const row = Math.floor((z - originZOf(draw.tile)) / step)
  return draw.holes.some((hole) => column >= hole.fromColumn && column < hole.toColumn && row >= hole.fromRow && row < hole.toRow)
}

// How many drawn tiles show the ground at the block: one when finer tiles take over exactly where coarser ones have holes.
function drawnOver(draws: DrawnTile[], x: number, z: number): number {
  return draws.filter((draw) => {
    const size = tileBlocks(draw.tile.level)
    const inside = x >= originXOf(draw.tile) && x < originXOf(draw.tile) + size && z >= originZOf(draw.tile) && z < originZOf(draw.tile) + size
    return inside && !holeContains(draw, x, z)
  }).length
}

function expectEverySpotDrawnOnce(draws: DrawnTile[]): void {
  for (let x = -SAMPLE_RADIUS_BLOCKS; x <= SAMPLE_RADIUS_BLOCKS; x += SAMPLE_STEP_BLOCKS) {
    for (let z = -SAMPLE_RADIUS_BLOCKS; z <= SAMPLE_RADIUS_BLOCKS; z += SAMPLE_STEP_BLOCKS) {
      expect(drawnOver(draws, x + 0.5, z + 0.5), `${x}, ${z}`).toBe(1)
    }
  }
}

describe('terrain cover', () => {
  it('asks for the coarse backdrop first, once for each tile', () => {
    const { draws, wanted } = planCover(sightFrom(2000), nothingReady, COARSEST_LEVEL)
    expect(draws).toEqual([])
    expect(wanted[0].level).toBe(Math.max(...wanted.map((tile) => tile.level)))
    expect(new Set(wanted.map(tileKey)).size).toBe(wanted.length)
    expect(Math.min(...wanted.map((tile) => tile.level))).toBeLessThan(wanted[0].level)
  })

  it('draws a quick stand-in only where no truer tile of any level is known', () => {
    const sight = sightFrom(2000)
    const backdrop = planCover(sight, nothingReady, COARSEST_LEVEL).wanted[0]
    const quickEverywhere = (): TileState => 'quick'
    const trueBackdrop = (tile: TerrainTile): TileState => (tileKey(tile) === tileKey(backdrop) ? 'fresh' : 'quick')

    const quickOnly = planCover(sight, quickEverywhere, COARSEST_LEVEL).draws
    const withTrueBackdrop = planCover(sight, trueBackdrop, COARSEST_LEVEL).draws

    expect(quickOnly.length).toBeGreaterThan(0)
    expectEverySpotDrawnOnce(quickOnly)
    expect(withTrueBackdrop.some((draw) => tileKey(draw.tile) === tileKey(backdrop))).toBe(true)
    expect(withTrueBackdrop.some((draw) => isInside(draw.tile, backdrop))).toBe(false)
  })

  it('keeps the true ground drawn from closer up, however much finer, until its coarser tile is as true', () => {
    const sight = sightFrom(3000)
    const nearGround = { level: 0, tileX: 0, tileZ: 0 }
    const known = (tile: TerrainTile): TileState => (tileKey(tile) === tileKey(nearGround) ? 'fresh' : 'quick')
    const knownInside = (tile: TerrainTile): TerrainTile[] => (isInside(nearGround, tile) ? [nearGround] : [])

    const { draws } = planCover(sight, known, COARSEST_LEVEL, knownInside)

    expect(draws.some((draw) => tileKey(draw.tile) === tileKey(nearGround))).toBe(true)
    expect(drawnOver(draws, 5.5, 5.5)).toBe(1)
  })

  it('wants nothing once every tile it needs is sampled', () => {
    expect(planCover(sightFrom(2000), everythingReady, COARSEST_LEVEL).wanted).toEqual([])
  })

  it('draws ground close to the camera in single blocks and far above it in wider columns', () => {
    const near = planCover(sightFrom(150), everythingReady, COARSEST_LEVEL).draws
    const far = planCover(sightFrom(30_000), everythingReady, COARSEST_LEVEL).draws
    expect(Math.min(...near.map((draw) => draw.tile.level))).toBe(0)
    expect(Math.min(...far.map((draw) => draw.tile.level))).toBeGreaterThanOrEqual(3)
  })

  it('never asks for more than a backdrop, a preview and detail of 64 tiles each', () => {
    for (const height of [60, 600, 6000, 600_000]) {
      const { wanted } = planCover(sightFrom(height), nothingReady, COARSEST_LEVEL)
      expect(wanted.length).toBeLessThanOrEqual(3 * 64)
    }
  })

  it('leaves a hole in each coarser tile where a finer one is drawn, so every spot is drawn once', () => {
    const { draws } = planCover(sightFrom(2000), everythingReady, COARSEST_LEVEL)
    for (const coarse of draws) {
      for (const finer of draws.filter((other) => isInside(other.tile, coarse.tile))) {
        expect(coarse.holes).toContainEqual(columnsUnder(coarse.tile, finer.tile))
      }
    }
    expectEverySpotDrawnOnce(draws)
  })

  it('shows the backdrop alone until finer tiles are sampled', () => {
    const wanted = planCover(sightFrom(2000), nothingReady, COARSEST_LEVEL).wanted
    const backdropLevel = wanted[0].level
    const state = (tile: TerrainTile): TileState => (tile.level === backdropLevel ? 'fresh' : 'none')
    const { draws } = planCover(sightFrom(2000), state, COARSEST_LEVEL)
    expect(draws.length).toBeGreaterThan(0)
    expect(draws.every((draw) => draw.tile.level === backdropLevel && draw.holes.length === 0)).toBe(true)
    expectEverySpotDrawnOnce(draws)
  })

  it('lets any tiles that are sampled stand in for the rest without gaps or overlaps', () => {
    const wanted = planCover(sightFrom(2000), nothingReady, COARSEST_LEVEL).wanted
    const backdropLevel = wanted[0].level
    const sampled = new Set(wanted.filter((_, index) => index % 3 === 0).map(tileKey))
    const state = (tile: TerrainTile): TileState => (tile.level === backdropLevel || sampled.has(tileKey(tile)) ? 'fresh' : 'none')
    expectEverySpotDrawnOnce(planCover(sightFrom(2000), state, COARSEST_LEVEL).draws)
  })

  it('draws finer tiles left over from before in place of one not sampled yet', () => {
    const wanted = planCover(sightFrom(2000), nothingReady, COARSEST_LEVEL).wanted
    const backdropLevel = wanted[0].level
    const leaf = wanted.find((tile) => tile.level > 0 && tile.level < backdropLevel)!
    const leftOver = new Set([tileKey({ level: leaf.level - 1, tileX: leaf.tileX * 2, tileZ: leaf.tileZ * 2 })])
    const state = (tile: TerrainTile): TileState => (tile.level === backdropLevel || leftOver.has(tileKey(tile)) ? 'standIn' : 'none')
    const { draws } = planCover(sightFrom(2000), state, COARSEST_LEVEL)
    expect(draws.map((draw) => tileKey(draw.tile))).toContain([...leftOver][0])
    expectEverySpotDrawnOnce(draws)
  })

  it('asks again for ground that only stands in', () => {
    const standIn = (): TileState => 'standIn'
    const { wanted } = planCover(sightFrom(2000), standIn, COARSEST_LEVEL)
    expect(wanted.length).toBeGreaterThan(0)
  })

  it('addresses each wanted tile by the blocks it holds', () => {
    const { wanted } = planCover(sightFrom(2000), nothingReady, COARSEST_LEVEL)
    expect(wanted.map(addressOf).every((address) => Number.isInteger(Math.log2(address.blocksPerPixel)))).toBe(true)
  })
})

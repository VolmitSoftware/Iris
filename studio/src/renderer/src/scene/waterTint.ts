import type { Vec3 } from './quadBatch'

export const WATER_SURFACE_COLOR = 0x5a8ef0
export const WATER_SURFACE_OPACITY = 0.3
const DEEP_COLOR: Vec3 = [0x0f / 255, 0x25 / 255, 0x52 / 255]
const SHALLOW_TINT = 0.35
const DEEPEST_TINT = 0.85
/** How far down the tint covers about two thirds of the way from shallow to deepest. */
const TINT_DEPTH_BLOCKS = 24

/**
 * How much of the deep colour covers ground this many blocks under the water's surface; none for ground at or above
 * it, so ground level with the surface stays dry.
 */
export function tintAt(depthBlocks: number): number {
  return depthBlocks <= 0 ? 0 : submergedTint(depthBlocks)
}

/** The same for ground known to lie under the surface, even at its very edge. */
export function submergedTint(depthBlocks: number): number {
  return SHALLOW_TINT + (DEEPEST_TINT - SHALLOW_TINT) * (1 - Math.exp(-depthBlocks / TINT_DEPTH_BLOCKS))
}

/** The colour sunk toward the deep colour by {@code tint}, from 0 for none to 1 for all of it. */
export function sunk(color: Vec3, tint: number): Vec3 {
  if (tint === 0) {
    return color
  }
  return [mix(color[0], DEEP_COLOR[0], tint), mix(color[1], DEEP_COLOR[1], tint), mix(color[2], DEEP_COLOR[2], tint)]
}

function mix(from: number, to: number, share: number): number {
  return from + (to - from) * share
}

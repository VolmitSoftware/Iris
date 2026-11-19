/**
 * How bright the server's sky light and corner darkening make a face, as Minecraft's smooth lighting shows them. A
 * face's shade holds the sky's light in the cell it faces, 0 to {@link FULL_LIGHT}, in its lowest bits, then for each of
 * its corners how many of the three cells around it are opaque.
 */

export const FULL_LIGHT = 15
const LIGHT_BITS = 4
const CORNER_BITS = 2
const LIGHT_MASK = (1 << LIGHT_BITS) - 1
const CORNER_MASK = (1 << CORNER_BITS) - 1
// Between Minecraft's Moody and Bright, so shade under trees reads without the forest floor going black.
const LIFT = 0.5
// What a cell the sky never reaches still shows, so a cave mouth is dark but not a hole.
const DARKEST = 0.12
// Each opaque cell around a corner takes this off its brightness; linear, so a far area's mean darkens as its blocks do.
const DARKENING_PER_OPAQUE_CELL = 0.2

/** The brightness of a cell lit at {@code light}, which may lie between levels, from {@link DARKEST} to 1. */
export function brightnessOfLight(light: number): number {
  const level = Math.min(FULL_LIGHT, Math.max(0, light)) / FULL_LIGHT
  const moody = level / (4 - 3 * level)
  const bright = 1 - (1 - moody) ** 4
  return DARKEST + (1 - DARKEST) * (moody + (bright - moody) * LIFT)
}

/** How much a corner keeps of its brightness with {@code opaque} cells around it, which may be a mean. */
export function brightnessOfCorner(opaque: number): number {
  return 1 - DARKENING_PER_OPAQUE_CELL * opaque
}

export function lightOf(shade: number): number {
  return shade & LIGHT_MASK
}

/** How many cells around a face's corner are opaque, the corners in the order the face's corners are drawn. */
export function opaqueAround(shade: number, corner: number): number {
  return (shade >> (LIGHT_BITS + corner * CORNER_BITS)) & CORNER_MASK
}

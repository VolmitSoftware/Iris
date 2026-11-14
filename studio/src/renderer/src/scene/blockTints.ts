/**
 * The colour each tinted face of a block is multiplied with, as Minecraft's BlockColors give it (v3e
 * MinecraftBlockTints). Scenes stand in no biome, so the biome tints are the plains' colours.
 */

const NO_TINT = 0xffffff
const PLAINS_GRASS = 0x91bd59
const PLAINS_FOLIAGE = 0x77ab2f
const PLAINS_DRY_FOLIAGE = 0xa0804b
const PLAINS_WATER = 0x3f76e4

const GRASS_TINTED = new Set(['grass_block', 'short_grass', 'fern', 'potted_fern', 'bush', 'tall_grass', 'large_fern', 'sugar_cane'])
const GRASS_TINTED_ON_SECOND_LAYER = new Set(['pink_petals', 'wildflowers'])
const FOLIAGE_TINTED = new Set(['oak_leaves', 'jungle_leaves', 'acacia_leaves', 'dark_oak_leaves', 'vine', 'mangrove_leaves'])
const WATER_TINTED = new Set(['water', 'bubble_column', 'water_cauldron'])
const GROWING_STEMS = new Set(['melon_stem', 'pumpkin_stem'])
const FIXED_TINTS: Record<string, number> = {
  spruce_leaves: 0x619961,
  birch_leaves: 0x80a755,
  attached_melon_stem: 0xe0c71c,
  attached_pumpkin_stem: 0xe0c71c,
  lily_pad: 0x208030
}

/** The tint of one face of block {@code name}, as 0xRRGGBB; white for an untinted face. */
export function blockTint(name: string, properties: Record<string, string>, tintIndex: number): number {
  // The client tints water when it draws the fluid, which has no model, so its stand-in is tinted whole.
  if (WATER_TINTED.has(name)) {
    return PLAINS_WATER
  }
  if (GRASS_TINTED_ON_SECOND_LAYER.has(name)) {
    return tintIndex === 1 ? PLAINS_GRASS : NO_TINT
  }
  if (tintIndex !== 0) {
    return NO_TINT
  }
  if (GRASS_TINTED.has(name)) {
    return PLAINS_GRASS
  }
  if (FOLIAGE_TINTED.has(name)) {
    return PLAINS_FOLIAGE
  }
  if (name === 'leaf_litter') {
    return PLAINS_DRY_FOLIAGE
  }
  if (GROWING_STEMS.has(name)) {
    const age = Number(properties.age ?? '0')
    return ((age * 32) << 16) | ((255 - age * 8) << 8) | (age * 4)
  }
  return FIXED_TINTS[name] ?? NO_TINT
}

import type { BridgeClient } from '../bridge/BridgeClient'
import { NO_BLOCK, type TileBlocks } from '../bridge/types'
import { BlockColors } from './blockColors'
import { BlockLooks } from './blockLooks'
import { BlockModels } from './blockModels'
import { BridgeAssets } from './BridgeAssets'
import { TerrainAtlas } from './terrainAtlas'

/** What a tile's blocks are drawn with: each block's colour from above, and its textures where columns show them. */
export interface BlockArt {
  /** The atlas the looks' pictures lie in; null for art that only colours. */
  readonly atlas: TerrainAtlas | null
  /** Fills in the colours of the tile's blocks, and their looks when {@code textured}. */
  paint(blocks: TileBlocks, textured: boolean): Promise<void>
  dispose(): void
}

// Only these have their whole model's pictures placed, so the ground's blocks take no room in the atlas for faces a column never shows.
function namesStandingOnGround(blocks: TileBlocks): string[] {
  const standing = new Set<number>(blocks.cover)
  standing.delete(NO_BLOCK)
  return [...standing].map((index) => blocks.names[index])
}

// A tile drawn block by block places the whole models of the blocks it shows.
function namesSeenOneByOne(blocks: TileBlocks): string[] {
  if (blocks.voxels === null) {
    return []
  }
  return [...new Set(blocks.voxels.blocks)].map((index) => blocks.names[index])
}

/** Block art read from the Minecraft client's assets through the bridge; {@code textured} art can also show textures. */
export function blockArt(client: BridgeClient, textured: boolean): BlockArt {
  const images = new BridgeAssets(client, () => {})
  const models = new BlockModels(images)
  const colors = new BlockColors(models, images)
  const atlas = textured ? new TerrainAtlas(images) : null
  const looks = atlas === null ? null : new BlockLooks(models, atlas)
  return {
    atlas,
    paint: async (blocks, withLooks) => {
      const shownLooks = withLooks ? looks : null
      const [colorOf, lookOf, coverOf, modelOf] = await Promise.all([
        colors.colorsOf(blocks.names),
        shownLooks?.looksOf(blocks.names) ?? null,
        shownLooks?.coversOf(namesStandingOnGround(blocks)) ?? null,
        shownLooks?.coversOf(namesSeenOneByOne(blocks)) ?? null
      ])
      blocks.colors = blocks.names.map((name) => colorOf.get(name)!)
      blocks.looks = lookOf === null ? [] : blocks.names.map((name) => lookOf.get(name) ?? null)
      blocks.covers = coverOf === null ? [] : blocks.names.map((name) => coverOf.get(name) ?? null)
      blocks.models = modelOf === null ? [] : blocks.names.map((name) => modelOf.get(name) ?? null)
    },
    dispose: () => atlas?.dispose()
  }
}

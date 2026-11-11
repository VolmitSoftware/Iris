import type { BridgeClient } from '../bridge/BridgeClient'
import type { TileBlocks } from '../bridge/types'
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
      const [colorOf, lookOf] = await Promise.all([colors.colorsOf(blocks.names), withLooks && looks !== null ? looks.looksOf(blocks.names) : null])
      blocks.colors = blocks.names.map((name) => colorOf.get(name)!)
      blocks.looks = lookOf === null ? [] : blocks.names.map((name) => lookOf.get(name) ?? null)
    },
    dispose: () => atlas?.dispose()
  }
}

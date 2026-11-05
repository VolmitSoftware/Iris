/** The shapes the Iris bridge answers with (Iris core, art.arcane.iris.core.studio). */

export type LabelKind = 'BIOME' | 'REGION'

export interface MapLabel {
  kind: LabelKind
  key: string
  name: string
  /** 0xRRGGBB */
  color: number
}

export interface ViewInfo {
  id: string
  label: string
  description: string
  group: string
  groupLabel: string
}

export interface WorldInfo {
  world: string
  dimension: string
  dimensionName: string
  seed: string
  minY: number
  height: number
  fluidHeight: number
  tileSize: number
  hasPlayers: boolean
  views: ViewInfo[]
}

export interface MapHover {
  worldX: number
  worldZ: number
  biome: MapLabel | null
  caveBiome: MapLabel | null
  region: MapLabel | null
  vanillaBiome: string | null
  height: number
  fluidHeight: number
  slopeDegrees: number
  biomeFile: string | null
  regionFile: string | null
}

export interface Marker {
  label: string
  worldX: number
  worldY: number
  worldZ: number
  health: number
  maxHealth: number
}

/**
 * A decoded map tile. Grids hold (size + 2)² cells, the tile's pixels inside a ring of its neighbours' edge pixels.
 */
export interface MapTile {
  size: number
  blocksPerPixel: number
  subjectKind: LabelKind
  labels: MapLabel[]
  subjects: Int16Array
  regions: Int16Array
  heights: Float32Array | null
}

export const NO_LABEL = -1

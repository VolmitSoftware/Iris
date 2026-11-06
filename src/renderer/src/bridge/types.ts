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

/** A view of block scenes a module of the pack shows, such as its objects (IrisApi SceneView). */
export interface SceneViewInfo {
  id: string
  label: string
  description: string
}

export interface SceneEntry {
  key: string
  name: string
  /** The folder it is listed under; empty for none. */
  group: string
  variants: number
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
  /** How the spot got its region and the biome the view shows, region first. */
  pickPath: PickStep[]
}

/** REGION, BIOME and CHILD were picked by noise; IMAGE_MAP, FOCUS and PREVIEW were set without a pick. */
export type PickKind = 'REGION' | 'BIOME' | 'CHILD' | 'IMAGE_MAP' | 'FOCUS' | 'PREVIEW'

export interface PickStep {
  kind: PickKind
  picked: MapLabel
  /** The biome list a BIOME step picked from, such as "land". */
  list: string | null
  /** The parent name a CHILD step replaced; null when the parent kept the spot. */
  replaced: string | null
  /** 0 to 1; null when nothing was picked. */
  noise: number | null
  bands: PickBand[]
}

/** One option's share of a pick noise: picked from `from` up to `to`. */
export interface PickBand {
  option: MapLabel
  from: number
  to: number
}

/** How the noise picks what an area of the map shows (MapPickInspection). */
export interface PickInspection {
  detail: string
  sections: PickSection[]
}

/** The picks on one region's ground; `region` is null in the region view, where the regions themselves are picked. */
export interface PickSection {
  region: MapLabel | null
  /** How much of the area's picked ground the region holds. */
  share: number
  biomeCount: number
  spreads: PickSpread[]
}

/** One noise in the area: its values in 50 equal steps from 0 to 1, and each option's share of it. */
export interface PickSpread {
  title: string
  bands: PickBand[]
  histogram: number[]
  samples: number
  rows: PickShare[]
}

/** `weight`: how much of the noise picks the option; `inView`: how much of the area's values do. */
export interface PickShare {
  option: MapLabel
  weight: number
  inView: number
}

/** How the region and biome views draw their picks: the labels, the noise that picked them, or the labels by noise. */
export type PickDisplay = 'picks' | 'noise' | 'both'

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
  /** The noise value that settled each cell's pick, 0 to 1 and NaN where nothing picked it; null unless asked for. */
  pickNoise: Float32Array | null
}

export const NO_LABEL = -1

/** The shapes the Iris bridge answers with (Iris core, art.arcane.iris.core.studio). */

import type { BlockLook } from '../scene/blockLooks'
import type { CoverLook } from '../scene/coverLooks'

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

/** A studio view a module of the pack shows (IrisApi StudioView): block scenes such as its objects, or patterns such as its palettes. */
export interface ModuleViewInfo {
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

/** A scene view as the server lists it; `editable` when its entries can be edited as nodes, `creatable` when they can also be created and deleted, `baked` when their bake can be timed, `parts` when a block can be traced to the part that placed it. */
export interface SceneViewInfo extends ModuleViewInfo {
  editable: boolean
  creatable: boolean
  baked: boolean
  parts: boolean
}

/** One timed bake of a scene entry (IrisApi BakeRun): a time per step, added up over every variant. */
export interface BakeRun {
  variantCount: number
  stages: { label: string; nanos: number }[]
}

/** Where a value sits in a JSON file: member names and element indices from the root. */
export type JsonPath = (string | number)[]

/** An entry's file as the node editor shows it (StudioEditors.EditorState). */
export interface EditorState {
  /** Where it is below the pack folder. */
  file: string
  /** Tells this text from any other; an edit sent with an older one is refused. */
  revision: string
  text: string
  /** The file as nodes, or null when it can't be read as nodes and `problem` says why. */
  roots: GraphNode[] | null
  problem: string | null
  /** Why the file can't be edited here, such as outside a studio world; null when it can. */
  locked: string | null
  types: Record<string, SettingsType>
}

export interface GraphNode {
  kind: string
  title: string
  path: JsonPath
  /** The scene part it draws, or NO_PART. */
  partId: number
  roles: string[]
  settings: NodeSettings[]
  children: { wire: string; node: GraphNode }[]
  /** The array a node moved under this one joins; null when nothing can move here. */
  childList: JsonPath | null
  movable: boolean
  duplicable: boolean
  additions: { label: string; list: JsonPath; literal: string }[]
}

export const NO_PART = -1

/** The settings of the object at `path`, of schema type `type`, without the members shown as nodes. */
export interface NodeSettings {
  title: string
  path: JsonPath
  type: string
  shownAsNodes: string[]
}

/** A settings type of the pack's schema (SchemaTypes.TypeWire). */
export interface SettingsType {
  description: string
  options: SettingOption[]
}

export interface SettingOption {
  name: string
  description: string
  required: boolean
  minimum: number | null
  maximum: number | null
  minItems: number | null
  defaultValue: unknown
  kind: ValueKind
}

export type ValueKind =
  | { kind: 'boolean' }
  | { kind: 'integer' }
  | { kind: 'decimal' }
  | { kind: 'text'; format: string }
  | { kind: 'reference'; reference: string; format: string }
  | { kind: 'resource'; resource: string }
  | { kind: 'enum'; constants: { name: string; description: string }[] }
  | { kind: 'list'; element: ValueKind; unique: boolean }
  | { kind: 'map'; value: ValueKind }
  | { kind: 'object'; type: string }
  | { kind: 'host'; type: string }
  | { kind: 'any' }

/** One change the node editor makes (GraphEditRequest). */
export type GraphEdit =
  | { op: 'set'; object: JsonPath; key: string; literal: string }
  | { op: 'unset'; object: JsonPath; key: string }
  | { op: 'append'; list: JsonPath; literal: string }
  | { op: 'addItem'; list: JsonPath }
  | { op: 'delete'; path: JsonPath }
  | { op: 'duplicate'; path: JsonPath }
  | { op: 'move'; path: JsonPath; toList: JsonPath }

/** Something a pattern view lays out, such as one of a biome's palettes (IrisApi PatternEntry). */
export interface PatternEntry {
  key: string
  name: string
  /** The heading it is listed under, such as its biome. */
  group: string
  /** How many depths below the top it has a pick for; at least 1. */
  depths: number
  /** Short lines about it, such as its blocks and noise. */
  facts: string[]
}

/** What a pattern entry places across a square of columns, row by row from the north west. */
export interface PatternTile {
  columns: number
  blocksPerColumn: number
  /** The block states the picks index, as BlockState keys; a plugin's block by the name the pack gives it. */
  blocks: string[]
  /** Per block, the state players see and the studio draws: the block itself, or the vanilla state a plugin block borrows. */
  drawnAs: string[]
  /** Each column's block, or NO_PATTERN_BLOCK where the pattern places none. */
  picks: Int16Array
}

export const NO_PATTERN_BLOCK = -1

/** A world saved on the server's disk, a dimension folder of region files. */
export interface SavedWorldInfo {
  /** Its folder below the server's level folder. */
  id: string
  name: string
  /** Who added the dimension, such as minecraft or iris. */
  namespace: string
}

/** One noise the noise view can show. */
export interface NoiseSource {
  id: string
  name: string
  category: string
  /** Whether the view's seed changes it; the dimension's overlay keeps the world's. */
  usesSeed: boolean
  /** What the noise spans where it is measured in blocks above the fluid; null for noise of 0 to 1. */
  blockRange: { lowest: number; highest: number } | null
}

/** A square of noise values, a pixel's value taken at its north west corner, row by row from the north west. */
export interface NoiseTile {
  size: number
  values: Float32Array
}

/** Where a saved world's view opens: the middle of the region nearest the origin, and how its ground lies there. */
export interface SavedStart {
  centerX: number
  centerZ: number
  minY: number
  lowestGround: number
  highestGround: number
  /** The water surface most water lies at, or null where the region holds none. */
  seaLevel: number | null
}

/** A square of a saved world, one column per pixel, row by row from the north west. */
export interface SavedTile {
  size: number
  blocksPerPixel: number
  /** The top block of the ground under any water; {@link noGround} where nothing finished is saved. */
  groundY: Int16Array
  /** The top water block over the ground; {@link noWater} where it is dry. */
  waterSurfaceY: Int16Array
  /** Indices into {@link blockNames}, {@link blocksDrawnAs} and {@link biomeLabels}. */
  blocks: Int16Array
  biomes: Int16Array
  /** Each block without its properties; a plugin's block by the name its plugin gives it. */
  blockNames: string[]
  /** Per block, the state it was saved as, which players see and the studio draws; a plugin block's is the vanilla state it borrows. */
  blocksDrawnAs: string[]
  biomeLabels: MapLabel[]
  noGround: number
  noWater: number
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
  /** The way it faces, in Minecraft's degrees: 0 is south, 90 is west; 0 for entities. */
  yaw: number
  health: number
  maxHealth: number
}

/** What the server asks of its machine, as it sends it every second. */
export interface ServerUsage {
  /** The server's CPU use with 100 per busy thread, or null when the JVM cannot tell. */
  cpuPercent: number | null
  /** The threads the server's CPU has, so the CPU can reach 100 times this. */
  threads: number
  heapUsedBytes: number
  heapMaxBytes: number
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
  /** The block on top of each cell's column; null unless asked for. */
  blocks: TileBlocks | null
}

/** Where a tile's blocks come from: the chunks the server generates in memory. */
export type BlockSource = 'generated'

/** The top of each cell's column: its ground block, and the block standing on it, such as grass. */
export interface TileBlocks {
  /** The block states the grids index, as BlockState keys. */
  names: string[]
  /** {@link NO_BLOCK} where the column holds no ground. */
  ground: Int16Array
  /** {@link NO_BLOCK} where nothing stands on the ground. */
  cover: Int16Array
  /** The colour of each named block from above, as 0xRRGGBB; the tile source fills it in. */
  colors: number[]
  /** What each named block looks like on a column, where the tile is fine enough to show textures; else empty. */
  looks: (BlockLook | null)[]
  /** What each named block looks like standing on the ground, for the blocks {@link cover} names, on a tile that shows textures; else empty. */
  covers: (CoverLook | null)[]
}

export const NO_BLOCK = -1
export const NO_LABEL = -1

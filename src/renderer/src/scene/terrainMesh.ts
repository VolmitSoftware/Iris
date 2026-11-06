import { Box3, BufferAttribute, BufferGeometry, DoubleSide, Group, Mesh, MeshBasicMaterial, PlaneGeometry, Vector3 } from 'three'
import { NO_LABEL, type MapTile } from '../bridge/types'
import { heightColor } from '../map/heightColors'
import type { ViewportContent } from '../studio/SceneViewport'

export type TerrainColoring = 'biomes' | 'regions' | 'height'

/** A square of map tiles with heights, row by row from the north west. */
export interface TerrainArea {
  tiles: MapTile[][]
  /** The world spot of the first tile's first pixel. */
  originX: number
  originZ: number
}

export interface TerrainMesh {
  content: ViewportContent
  dispose(): void
}

const WATER_COLOR = 0x3f76e4
const WATER_OPACITY = 0.55
const UNLABELLED_COLOR = 0x5a5f6b
// Light from high in the south west, so slopes read as relief; the darkest side keeps some light.
const LIGHT = new Vector3(-0.6, 1, 0.4).normalize()
const AMBIENT_LIGHT = 0.5
const WALL_DEPTH_BLOCKS = 8
const WALL_TOP_LIGHT = 0.55
const WALL_BASE_LIGHT = 0.25
const SEA_INSET_BLOCKS = 0.05

/** The terrain of the area, one vertex per map pixel, with the dimension's sea as a sheet of water at fluid height. */
export function buildTerrainMesh(area: TerrainArea, coloring: TerrainColoring, fluidHeight: number): TerrainMesh {
  const grid = TerrainGrid.of(area)
  const terrain = terrainGeometry(grid, coloring, fluidHeight)
  const base = Math.min(grid.lowestHeight(), fluidHeight) - WALL_DEPTH_BLOCKS
  const landWalls = rimWall(grid, terrain, (ground) => ground, () => base)
  const seaWalls = rimWall(grid, terrain, () => fluidHeight, (ground) => Math.min(ground, fluidHeight))
  const terrainMaterial = new MeshBasicMaterial({ vertexColors: true, side: DoubleSide })
  const sea = seaSurface(grid, fluidHeight)
  const waterMaterial = new MeshBasicMaterial({ color: WATER_COLOR, transparent: true, opacity: WATER_OPACITY, depthWrite: false, side: DoubleSide })
  const group = new Group()
  group.add(new Mesh(terrain, terrainMaterial), new Mesh(landWalls, terrainMaterial), new Mesh(sea, waterMaterial), new Mesh(seaWalls, waterMaterial))
  const geometries = [terrain, landWalls, sea, seaWalls]
  return {
    content: { object: group, bounds: new Box3().setFromObject(group) },
    dispose: () => {
      geometries.forEach((geometry) => geometry.dispose())
      terrainMaterial.dispose()
      waterMaterial.dispose()
    }
  }
}

// Kept a little inside the walls, so its edge does not flicker against them.
function seaSurface(grid: TerrainGrid, fluidHeight: number): BufferGeometry {
  const sea = new PlaneGeometry(grid.spanX - 2 * SEA_INSET_BLOCKS, grid.spanZ - 2 * SEA_INSET_BLOCKS).rotateX(-Math.PI / 2)
  return sea.translate(grid.worldX(0) + grid.spanX / 2, fluidHeight, grid.worldZ(0) + grid.spanZ / 2)
}

/**
 * A wall round the terrain's rim, from {@code topAt} down to {@code bottomAt} of the ground height at each rim vertex;
 * in the terrain's colour, darkening downwards, for a material that uses vertex colours.
 */
function rimWall(grid: TerrainGrid, terrain: BufferGeometry, topAt: (ground: number) => number, bottomAt: (ground: number) => number): BufferGeometry {
  const rim = grid.rimVertices()
  const surface = terrain.getAttribute('position')
  const shade = terrain.getAttribute('color')
  const positions = new Float32Array(rim.length * 2 * 3)
  const colors = new Float32Array(rim.length * 2 * 3)
  rim.forEach((vertex, index) => {
    const [x, ground, z] = [surface.getX(vertex), surface.getY(vertex), surface.getZ(vertex)]
    positions.set([x, topAt(ground), z, x, bottomAt(ground), z], index * 6)
    const [red, green, blue] = [shade.getX(vertex), shade.getY(vertex), shade.getZ(vertex)]
    colors.set([red * WALL_TOP_LIGHT, green * WALL_TOP_LIGHT, blue * WALL_TOP_LIGHT], index * 6)
    colors.set([red * WALL_BASE_LIGHT, green * WALL_BASE_LIGHT, blue * WALL_BASE_LIGHT], index * 6 + 3)
  })
  const indices: number[] = []
  for (let index = 0; index < rim.length; index++) {
    const next = (index + 1) % rim.length
    const [upper, lower, nextUpper, nextLower] = [index * 2, index * 2 + 1, next * 2, next * 2 + 1]
    indices.push(upper, lower, nextUpper, nextUpper, lower, nextLower)
  }
  const geometry = new BufferGeometry()
  geometry.setAttribute('position', new BufferAttribute(positions, 3))
  geometry.setAttribute('color', new BufferAttribute(colors, 3))
  geometry.setIndex(indices)
  return geometry
}

function terrainGeometry(grid: TerrainGrid, coloring: TerrainColoring, fluidHeight: number): BufferGeometry {
  const { columns, rows } = grid
  const positions = new Float32Array(columns * rows * 3)
  const colors = new Float32Array(columns * rows * 3)
  for (let row = 0; row < rows; row++) {
    for (let column = 0; column < columns; column++) {
      const vertex = row * columns + column
      const height = grid.height(column, row)
      positions.set([grid.worldX(column), height, grid.worldZ(row)], vertex * 3)
      const color = coloring === 'height' ? heightColor(height, fluidHeight) : grid.labelColor(column, row, coloring)
      const light = AMBIENT_LIGHT + (1 - AMBIENT_LIGHT) * Math.max(0, grid.normal(column, row).dot(LIGHT))
      colors.set([(((color >> 16) & 0xff) / 255) * light, (((color >> 8) & 0xff) / 255) * light, ((color & 0xff) / 255) * light], vertex * 3)
    }
  }
  const indices = new Uint32Array((columns - 1) * (rows - 1) * 6)
  let next = 0
  for (let row = 0; row < rows - 1; row++) {
    for (let column = 0; column < columns - 1; column++) {
      const northWest = row * columns + column
      const southWest = northWest + columns
      indices.set([northWest, southWest, northWest + 1, northWest + 1, southWest, southWest + 1], next)
      next += 6
    }
  }
  const geometry = new BufferGeometry()
  geometry.setAttribute('position', new BufferAttribute(positions, 3))
  geometry.setAttribute('color', new BufferAttribute(colors, 3))
  geometry.setIndex(new BufferAttribute(indices, 1))
  return geometry
}

/** The area's tiles as one grid of pixels, without the ring each tile carries for its neighbours. */
class TerrainGrid {
  readonly columns: number
  readonly rows: number
  /** How far the vertices reach, west to east and north to south, in blocks. */
  readonly spanX: number
  readonly spanZ: number
  private readonly size: number
  private readonly blocksPerPixel: number

  private constructor(private readonly area: TerrainArea) {
    const first = area.tiles[0][0]
    this.size = first.size
    this.blocksPerPixel = first.blocksPerPixel
    this.columns = area.tiles[0].length * this.size
    this.rows = area.tiles.length * this.size
    this.spanX = (this.columns - 1) * this.blocksPerPixel
    this.spanZ = (this.rows - 1) * this.blocksPerPixel
  }

  static of(area: TerrainArea): TerrainGrid {
    return new TerrainGrid(area)
  }

  worldX(column: number): number {
    return this.area.originX + column * this.blocksPerPixel
  }

  worldZ(row: number): number {
    return this.area.originZ + row * this.blocksPerPixel
  }

  height(column: number, row: number): number {
    const { tile, cell } = this.cellAt(column, row)
    return tile.heights![cell]
  }

  labelColor(column: number, row: number, coloring: 'biomes' | 'regions'): number {
    const { tile, cell } = this.cellAt(column, row)
    const index = coloring === 'regions' ? tile.regions[cell] : tile.subjects[cell]
    return index === NO_LABEL ? UNLABELLED_COLOR : tile.labels[index].color
  }

  lowestHeight(): number {
    let lowest = Infinity
    for (let row = 0; row < this.rows; row++) {
      for (let column = 0; column < this.columns; column++) {
        lowest = Math.min(lowest, this.height(column, row))
      }
    }
    return lowest
  }

  /** The vertices on the edge of the grid, once each, going round clockwise from the north west corner. */
  rimVertices(): number[] {
    const { columns, rows } = this
    const rim: number[] = []
    for (let column = 0; column < columns - 1; column++) rim.push(column)
    for (let row = 0; row < rows - 1; row++) rim.push(row * columns + columns - 1)
    for (let column = columns - 1; column > 0; column--) rim.push((rows - 1) * columns + column)
    for (let row = rows - 1; row > 0; row--) rim.push(row * columns)
    return rim
  }

  normal(column: number, row: number): Vector3 {
    const west = this.height(Math.max(0, column - 1), row)
    const east = this.height(Math.min(this.columns - 1, column + 1), row)
    const north = this.height(column, Math.max(0, row - 1))
    const south = this.height(column, Math.min(this.rows - 1, row + 1))
    return new Vector3(west - east, 2 * this.blocksPerPixel, north - south).normalize()
  }

  private cellAt(column: number, row: number): { tile: MapTile; cell: number } {
    const tile = this.area.tiles[Math.floor(row / this.size)][Math.floor(column / this.size)]
    const gridSize = this.size + 2
    return { tile, cell: ((row % this.size) + 1) * gridSize + (column % this.size) + 1 }
  }
}

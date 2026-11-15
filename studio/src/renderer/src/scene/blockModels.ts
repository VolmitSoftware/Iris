/**
 * Minecraft's block models, read the way the client reads them: a block state picks variants or multipart pieces from
 * its blockstate file, each a model whose parents fill in textures and elements, turned in steps of 90 degrees.
 */

export type Direction = 'down' | 'up' | 'north' | 'south' | 'west' | 'east'
export type Vec3 = [number, number, number]

/** Where a block model's files come from: the bridge's copy of the client's assets. */
export interface AssetSource {
  /** The JSON file at {@code path} below the assets folder, or null when there is none. */
  json(path: string): Promise<unknown>
}

/** One textured face of a block, in block units from its lowest corner (0 to 1 on each axis). */
export interface Quad {
  /** Top left, bottom left, bottom right, top right as the texture lies on the face, seen from outside. */
  corners: [Vec3, Vec3, Vec3, Vec3]
  /** The texture's coordinates at each corner, 0 to 1 from its top left. */
  uvs: [[number, number], [number, number], [number, number], [number, number]]
  /** Such as minecraft:block/stone. */
  texture: string
  /** Which of the block's tints colours the face, or -1 for none. */
  tintIndex: number
  /** The side whose neighbour, when it is a solid block, hides the face; null when nothing hides it. */
  cullFace: Direction | null
  /** The side the face looks toward, for its light. */
  facing: Direction
  shade: boolean
}

export interface BlockModel {
  quads: Quad[]
  /** A single element filling the whole block on every side, which hides the faces it touches if its textures are solid. */
  fullCube: boolean
  /** The texture the client breaks the block with, which stands in for blocks the client draws without a model. */
  particle: string | null
}

export interface ParsedState {
  namespace: string
  name: string
  properties: Record<string, string>
}

type Json = Record<string, unknown>

interface Variant {
  model: string
  x: number
  y: number
}

interface ModelFile {
  parent?: string
  textures?: Record<string, string>
  elements?: ElementJson[]
}

interface ElementJson {
  from: Vec3
  to: Vec3
  rotation?: { origin: Vec3; axis: 'x' | 'y' | 'z'; angle: number; rescale?: boolean }
  shade?: boolean
  faces: Partial<Record<Direction, FaceJson>>
}

interface FaceJson {
  uv?: [number, number, number, number]
  texture: string
  cullface?: Direction
  rotation?: number
  tintindex?: number
}

const PIXELS = 16
const DEFAULT_NAMESPACE = 'minecraft'
const MAX_PARENT_DEPTH = 32
const DIRECTIONS: Direction[] = ['down', 'up', 'north', 'south', 'west', 'east']
const DIRECTION_VECTORS: Record<Direction, Vec3> = {
  down: [0, -1, 0],
  up: [0, 1, 0],
  north: [0, 0, -1],
  south: [0, 0, 1],
  west: [-1, 0, 0],
  east: [1, 0, 0]
}

/** Reads {@code minecraft:oak_log[axis=y]}. */
export function parseState(key: string): ParsedState {
  const open = key.indexOf('[')
  const id = open < 0 ? key : key.slice(0, open)
  const colon = id.indexOf(':')
  const properties: Record<string, string> = {}
  if (open >= 0) {
    for (const pair of key.slice(open + 1, -1).split(',')) {
      const equals = pair.indexOf('=')
      if (equals > 0) {
        properties[pair.slice(0, equals)] = pair.slice(equals + 1)
      }
    }
  }
  return colon < 0
    ? { namespace: DEFAULT_NAMESPACE, name: id, properties }
    : { namespace: id.slice(0, colon), name: id.slice(colon + 1), properties }
}

/** Resolves and keeps the models of block states; one per connection, as its assets are one Minecraft version's. */
export class BlockModels {
  private readonly models = new Map<string, Promise<BlockModel | null>>()
  private readonly files = new Map<string, Promise<ModelFile | null>>()

  constructor(private readonly assets: AssetSource) {}

  /** The model of a block state, or null when the client has no blockstate file for the block. */
  model(stateKey: string): Promise<BlockModel | null> {
    let model = this.models.get(stateKey)
    if (model === undefined) {
      model = this.resolve(parseState(stateKey))
      this.models.set(stateKey, model)
    }
    return model
  }

  private async resolve(state: ParsedState): Promise<BlockModel | null> {
    const blockstate = (await this.assets.json(`${state.namespace}/blockstates/${state.name}.json`)) as Json | null
    if (blockstate === null) {
      return null
    }
    const variants = pickVariants(blockstate, state.properties)
    const pieces = await Promise.all(variants.map((variant) => this.piece(variant)))
    const quads = pieces.flatMap((piece) => piece.quads)
    return { quads, fullCube: pieces.length === 1 && pieces[0].fullCube, particle: pieces[0]?.particle ?? null }
  }

  private async piece(variant: Variant): Promise<BlockModel> {
    const chain = await this.parentChain(variant.model)
    const textures: Record<string, string> = {}
    for (const file of [...chain].reverse()) {
      Object.assign(textures, file.textures ?? {})
    }
    const elements = chain.find((file) => file.elements !== undefined)?.elements ?? []
    const quads = elements.flatMap((element) => elementQuads(element, textures))
    const turned = quads.map((quad) => turn(quad, variant.x, variant.y))
    return { quads: turned, fullCube: elements.length === 1 && isFullCube(elements[0]), particle: textureOf('#particle', textures) }
  }

  private async parentChain(modelId: string): Promise<ModelFile[]> {
    const chain: ModelFile[] = []
    let next: string | undefined = modelId
    while (next !== undefined && chain.length < MAX_PARENT_DEPTH) {
      const file = await this.file(next)
      if (file === null) {
        break
      }
      chain.push(file)
      next = file.parent
    }
    return chain
  }

  private file(modelId: string): Promise<ModelFile | null> {
    let file = this.files.get(modelId)
    if (file === undefined) {
      const { namespace, path } = splitId(modelId)
      file = this.assets.json(`${namespace}/models/${path}.json`) as Promise<ModelFile | null>
      this.files.set(modelId, file)
    }
    return file
  }
}

/** A whole block with {@code texture} on every side, for blocks the client draws without a model. */
export function cubeModel(texture: string): BlockModel {
  const faces = Object.fromEntries(DIRECTIONS.map((direction) => [direction, { texture: '#all', cullface: direction }]))
  const quads = elementQuads({ from: [0, 0, 0], to: [PIXELS, PIXELS, PIXELS], faces }, { all: texture })
  return { quads, fullCube: true, particle: texture }
}

/** The variant a block state picks, or the multipart pieces whose conditions it meets; the first of weighted choices. */
function pickVariants(blockstate: Json, properties: Record<string, string>): Variant[] {
  const variants = blockstate.variants as Record<string, unknown> | undefined
  if (variants !== undefined) {
    for (const [condition, choice] of Object.entries(variants)) {
      if (matchesVariantKey(condition, properties)) {
        return [variantOf(choice)]
      }
    }
    return []
  }
  const multipart = blockstate.multipart as { when?: Json; apply: unknown }[] | undefined
  return (multipart ?? []).filter((part) => part.when === undefined || meets(part.when, properties)).map((part) => variantOf(part.apply))
}

function matchesVariantKey(condition: string, properties: Record<string, string>): boolean {
  if (condition === '') {
    return true
  }
  return condition.split(',').every((pair) => {
    const [name, value] = pair.split('=')
    return properties[name] === value
  })
}

function meets(condition: Json, properties: Record<string, string>): boolean {
  if (Array.isArray(condition.OR)) {
    return (condition.OR as Json[]).some((part) => meets(part, properties))
  }
  if (Array.isArray(condition.AND)) {
    return (condition.AND as Json[]).every((part) => meets(part, properties))
  }
  return Object.entries(condition).every(([name, allowed]) => String(allowed).split('|').includes(properties[name]))
}

function variantOf(choice: unknown): Variant {
  const picked = (Array.isArray(choice) ? choice[0] : choice) as Json
  return { model: String(picked.model), x: Number(picked.x ?? 0), y: Number(picked.y ?? 0) }
}

function splitId(id: string): { namespace: string; path: string } {
  const colon = id.indexOf(':')
  return colon < 0 ? { namespace: DEFAULT_NAMESPACE, path: id } : { namespace: id.slice(0, colon), path: id.slice(colon + 1) }
}

function isFullCube(element: ElementJson): boolean {
  const fills = element.from.every((value) => value === 0) && element.to.every((value) => value === PIXELS)
  return fills && element.rotation === undefined && DIRECTIONS.every((direction) => element.faces[direction] !== undefined)
}

function elementQuads(element: ElementJson, textures: Record<string, string>): Quad[] {
  const quads: Quad[] = []
  for (const direction of DIRECTIONS) {
    const face = element.faces[direction]
    if (face === undefined) {
      continue
    }
    const texture = textureOf(face.texture, textures)
    if (texture === null) {
      continue
    }
    const corners = faceCorners(direction, element.from, element.to).map((corner) => rotateInElement(corner, element)) as Quad['corners']
    quads.push({
      corners,
      uvs: faceUvs(face, direction, element.from, element.to),
      texture,
      tintIndex: face.tintindex ?? -1,
      cullFace: face.cullface ?? null,
      facing: direction,
      shade: element.shade ?? true
    })
  }
  return quads
}

/** Follows {@code #name} references through the model's textures to a texture id. */
function textureOf(reference: string, textures: Record<string, string>): string | null {
  let current = reference
  for (let step = 0; step < MAX_PARENT_DEPTH && current.startsWith('#'); step++) {
    const next = textures[current.slice(1)]
    if (next === undefined) {
      return null
    }
    current = next
  }
  if (current.startsWith('#')) {
    return null
  }
  const { namespace, path } = splitId(current)
  return `${namespace}:${path}`
}

/** A face's corners in pixels, top left first and counter-clockwise seen from outside, as {@link Quad#corners}. */
function faceCorners(direction: Direction, from: Vec3, to: Vec3): Vec3[] {
  const [x1, y1, z1] = from.map((value) => value / PIXELS)
  const [x2, y2, z2] = to.map((value) => value / PIXELS)
  switch (direction) {
    case 'north':
      return [[x2, y2, z1], [x2, y1, z1], [x1, y1, z1], [x1, y2, z1]]
    case 'south':
      return [[x1, y2, z2], [x1, y1, z2], [x2, y1, z2], [x2, y2, z2]]
    case 'west':
      return [[x1, y2, z1], [x1, y1, z1], [x1, y1, z2], [x1, y2, z2]]
    case 'east':
      return [[x2, y2, z2], [x2, y1, z2], [x2, y1, z1], [x2, y2, z1]]
    case 'up':
      return [[x1, y2, z1], [x1, y2, z2], [x2, y2, z2], [x2, y2, z1]]
    case 'down':
      return [[x1, y1, z2], [x1, y1, z1], [x2, y1, z1], [x2, y1, z2]]
  }
}

function faceUvs(face: FaceJson, direction: Direction, from: Vec3, to: Vec3): Quad['uvs'] {
  const [u1, v1, u2, v2] = (face.uv ?? defaultUv(direction, from, to)).map((value) => value / PIXELS)
  const uvs: Quad['uvs'] = [[u1, v1], [u1, v2], [u2, v2], [u2, v1]]
  const steps = (((face.rotation ?? 0) / 90) % 4 + 4) % 4
  return uvs.map((_, corner) => uvs[(corner + steps) % 4]) as Quad['uvs']
}

// Minecraft's own default: the face shows the part of the texture its element covers, seen from outside.
function defaultUv(direction: Direction, [x1, y1, z1]: Vec3, [x2, y2, z2]: Vec3): [number, number, number, number] {
  switch (direction) {
    case 'down':
      return [x1, PIXELS - z2, x2, PIXELS - z1]
    case 'up':
      return [x1, z1, x2, z2]
    case 'north':
      return [PIXELS - x2, PIXELS - y2, PIXELS - x1, PIXELS - y1]
    case 'south':
      return [x1, PIXELS - y2, x2, PIXELS - y1]
    case 'west':
      return [z1, PIXELS - y2, z2, PIXELS - y1]
    case 'east':
      return [PIXELS - z2, PIXELS - y2, PIXELS - z1, PIXELS - y1]
  }
}

function rotateInElement(corner: Vec3, element: ElementJson): Vec3 {
  const rotation = element.rotation
  if (rotation === undefined || rotation.angle === 0) {
    return corner
  }
  const origin = rotation.origin.map((value) => value / PIXELS) as Vec3
  const radians = (rotation.angle * Math.PI) / 180
  const relative: Vec3 = [corner[0] - origin[0], corner[1] - origin[1], corner[2] - origin[2]]
  const turned = rotateAboutAxis(relative, rotation.axis, radians)
  if (rotation.rescale === true) {
    const stretch = 1 / Math.cos(Math.abs(radians))
    for (const axis of [0, 1, 2]) {
      if (axis !== 'xyz'.indexOf(rotation.axis)) {
        turned[axis] *= stretch
      }
    }
  }
  return [turned[0] + origin[0], turned[1] + origin[1], turned[2] + origin[2]]
}

function rotateAboutAxis([x, y, z]: Vec3, axis: 'x' | 'y' | 'z', radians: number): Vec3 {
  const cos = Math.cos(radians)
  const sin = Math.sin(radians)
  switch (axis) {
    case 'x':
      return [x, y * cos - z * sin, y * sin + z * cos]
    case 'y':
      return [x * cos + z * sin, y, -x * sin + z * cos]
    case 'z':
      return [x * cos - y * sin, x * sin + y * cos, z]
  }
}

/** A variant's turn: {@code x} first, then {@code y}, each in steps of 90 degrees about the block's centre. */
function turn(quad: Quad, xDegrees: number, yDegrees: number): Quad {
  const xSteps = quarterTurns(xDegrees)
  const ySteps = quarterTurns(yDegrees)
  if (xSteps === 0 && ySteps === 0) {
    return quad
  }
  const turnPoint = (point: Vec3): Vec3 => offsetFromCentre(turnVector(offsetFromCentre(point, -0.5), xSteps, ySteps), 0.5)
  return {
    ...quad,
    corners: quad.corners.map(turnPoint) as Quad['corners'],
    cullFace: quad.cullFace === null ? null : directionOf(turnVector(DIRECTION_VECTORS[quad.cullFace], xSteps, ySteps)),
    facing: directionOf(turnVector(DIRECTION_VECTORS[quad.facing], xSteps, ySteps))
  }
}

function quarterTurns(degrees: number): number {
  return ((Math.round(degrees / 90) % 4) + 4) % 4
}

function offsetFromCentre([x, y, z]: Vec3, by: number): Vec3 {
  return [x + by, y + by, z + by]
}

// A quarter turn about x takes north to down; one about y takes north to east, as Minecraft turns its variants.
function turnVector(vector: Vec3, xSteps: number, ySteps: number): Vec3 {
  let [x, y, z] = vector
  for (let step = 0; step < xSteps; step++) {
    ;[y, z] = [z, -y]
  }
  for (let step = 0; step < ySteps; step++) {
    ;[x, z] = [-z, x]
  }
  return [x, y, z]
}

function directionOf([x, y, z]: Vec3): Direction {
  const rounded = [Math.round(x), Math.round(y), Math.round(z)].join(',')
  return DIRECTIONS.find((direction) => DIRECTION_VECTORS[direction].join(',') === rounded) ?? 'up'
}

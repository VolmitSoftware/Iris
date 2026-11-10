import { BufferAttribute, BufferGeometry } from 'three'
import type { UvCell } from './terrainAtlas'

const VERTICES_PER_QUAD = 4
const INDICES_PER_QUAD = 6
const COMPONENTS = 3
const UV_COMPONENTS = 2
const FIRST_CAPACITY_QUADS = 1024

/**
 * A picture on a quad. A standing picture is upright seen from outside, as on a side: it runs from the far end of
 * {@code along} on its left to the near end on its right, with its top edge at the far end of {@code up}. A lying one is
 * as seen from above, as on a top: left to right along {@code along}, its top edge at the near end of {@code up}.
 */
export interface Picture {
  cell: UvCell
  standing: boolean
}

/**
 * Flat quads gathered for one geometry; vertices are not shared, so every quad keeps its own colour and shade. A batch
 * given a {@code plain} cell is textured: quads without a picture show that cell, so their colour alone is seen.
 */
export class QuadBatch {
  private positions = new Float32Array(FIRST_CAPACITY_QUADS * VERTICES_PER_QUAD * COMPONENTS)
  private colors: Float32Array | null
  private uvs: Float32Array | null
  private quads = 0

  constructor(
    coloured: boolean,
    private readonly plain: UvCell | null = null
  ) {
    this.colors = coloured ? new Float32Array(this.positions.length) : null
    this.uvs = plain === null ? null : new Float32Array(FIRST_CAPACITY_QUADS * VERTICES_PER_QUAD * UV_COMPONENTS)
  }

  get count(): number {
    return this.quads
  }

  /**
   * A quad from {@code origin} along {@code along} and {@code up}; it faces the way {@code up} crosses {@code along}.
   * Colours are red, green and blue from 0 to 1; the corners at the far end of {@code up} take {@code topColor}.
   */
  add(origin: Vec3, along: Vec3, up: Vec3, color: Vec3 | null, topColor: Vec3 | null = color, picture: Picture | null = null): void {
    this.reserveOne()
    const first = this.quads * VERTICES_PER_QUAD * COMPONENTS
    for (let corner = 0; corner < VERTICES_PER_QUAD; corner++) {
      const [alongSteps, upSteps] = [corner >> 1, corner & 1]
      for (let axis = 0; axis < COMPONENTS; axis++) {
        const at = first + corner * COMPONENTS + axis
        this.positions[at] = origin[axis] + along[axis] * alongSteps + up[axis] * upSteps
        const cornerColor = upSteps === 1 ? topColor : color
        if (this.colors !== null && cornerColor !== null) {
          this.colors[at] = cornerColor[axis]
        }
      }
      this.setUv(this.quads * VERTICES_PER_QUAD + corner, alongSteps, upSteps, picture)
    }
    this.quads++
  }

  geometry(): BufferGeometry {
    const geometry = new BufferGeometry()
    const used = this.quads * VERTICES_PER_QUAD * COMPONENTS
    geometry.setAttribute('position', new BufferAttribute(this.positions.slice(0, used), COMPONENTS))
    if (this.colors !== null) {
      geometry.setAttribute('color', new BufferAttribute(this.colors.slice(0, used), COMPONENTS))
    }
    if (this.uvs !== null) {
      geometry.setAttribute('uv', new BufferAttribute(this.uvs.slice(0, this.quads * VERTICES_PER_QUAD * UV_COMPONENTS), UV_COMPONENTS))
    }
    const indices = new Uint32Array(this.quads * INDICES_PER_QUAD)
    for (let quad = 0; quad < this.quads; quad++) {
      const first = quad * VERTICES_PER_QUAD
      indices.set([first, first + 1, first + 2, first + 2, first + 1, first + 3], quad * INDICES_PER_QUAD)
    }
    geometry.setIndex(new BufferAttribute(indices, 1))
    return geometry
  }

  private setUv(vertex: number, alongSteps: number, upSteps: number, picture: Picture | null): void {
    if (this.uvs === null || this.plain === null) {
      return
    }
    const at = vertex * UV_COMPONENTS
    if (picture === null) {
      this.uvs[at] = this.plain.u + this.plain.width / 2
      this.uvs[at + 1] = this.plain.v + this.plain.height / 2
      return
    }
    const [rightSteps, downSteps] = picture.standing ? [1 - alongSteps, 1 - upSteps] : [alongSteps, upSteps]
    this.uvs[at] = picture.cell.u + rightSteps * picture.cell.width
    this.uvs[at + 1] = picture.cell.v + downSteps * picture.cell.height
  }

  private reserveOne(): void {
    if ((this.quads + 1) * VERTICES_PER_QUAD * COMPONENTS <= this.positions.length) {
      return
    }
    this.positions = grown(this.positions)
    if (this.colors !== null) {
      this.colors = grown(this.colors)
    }
    if (this.uvs !== null) {
      this.uvs = grown(this.uvs)
    }
  }
}

export type Vec3 = readonly [number, number, number]

function grown(values: Float32Array): Float32Array<ArrayBuffer> {
  const bigger = new Float32Array(values.length * 2)
  bigger.set(values)
  return bigger
}

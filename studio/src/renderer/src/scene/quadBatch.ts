import { BufferAttribute, BufferGeometry } from 'three'

const VERTICES_PER_QUAD = 4
const INDICES_PER_QUAD = 6
const COMPONENTS = 3
const FIRST_CAPACITY_QUADS = 1024

/** Flat quads gathered for one geometry; vertices are not shared, so every quad keeps its own colour and shade. */
export class QuadBatch {
  private positions = new Float32Array(FIRST_CAPACITY_QUADS * VERTICES_PER_QUAD * COMPONENTS)
  private colors: Float32Array | null
  private quads = 0

  constructor(coloured: boolean) {
    this.colors = coloured ? new Float32Array(this.positions.length) : null
  }

  get count(): number {
    return this.quads
  }

  /**
   * A quad from {@code origin} along {@code along} and {@code up}; it faces the way {@code up} crosses {@code along}.
   * Colours are red, green and blue from 0 to 1; the corners at the far end of {@code up} take {@code topColor}.
   */
  add(origin: Vec3, along: Vec3, up: Vec3, color: Vec3 | null, topColor: Vec3 | null = color): void {
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
    const indices = new Uint32Array(this.quads * INDICES_PER_QUAD)
    for (let quad = 0; quad < this.quads; quad++) {
      const first = quad * VERTICES_PER_QUAD
      indices.set([first, first + 1, first + 2, first + 2, first + 1, first + 3], quad * INDICES_PER_QUAD)
    }
    geometry.setIndex(new BufferAttribute(indices, 1))
    return geometry
  }

  private reserveOne(): void {
    if ((this.quads + 1) * VERTICES_PER_QUAD * COMPONENTS <= this.positions.length) {
      return
    }
    this.positions = grown(this.positions)
    if (this.colors !== null) {
      this.colors = grown(this.colors)
    }
  }
}

export type Vec3 = readonly [number, number, number]

function grown(values: Float32Array): Float32Array<ArrayBuffer> {
  const bigger = new Float32Array(values.length * 2)
  bigger.set(values)
  return bigger
}

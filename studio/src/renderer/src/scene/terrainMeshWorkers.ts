import { ColumnField } from './columnField'
import { geometryOfCompact } from './compactGeometry'
import type { TerrainGeometry } from './terrainMesh'
import type { MeshRequest, MeshResult } from './terrainMeshWorker'

const MOST_WORKERS = 6

/**
 * Map tiles' faces built on worker threads, so a tile arriving never holds up a frame: the main thread only wraps the
 * arrays a worker sends back, without copying them. Tiles are built in the order they are asked for.
 */
export class TerrainMeshWorkers {
  private readonly workers: Worker[] = []
  private readonly idle: Worker[] = []
  private readonly queue: { request: MeshRequest; resolve(geometry: TerrainGeometry): void; reject(error: Error): void }[] = []
  private readonly running = new Map<Worker, { resolve(geometry: TerrainGeometry): void; reject(error: Error): void }>()
  private nextId = 0

  constructor() {
    const count = Math.max(1, Math.min(MOST_WORKERS, (navigator.hardwareConcurrency || 2) - 1))
    for (let index = 0; index < count; index++) {
      const worker = new Worker(new URL('./terrainMeshWorker.ts', import.meta.url), { type: 'module' })
      worker.onmessage = (event: MessageEvent<MeshResult>) => this.finished(worker, event.data)
      worker.onerror = (event) => this.failed(worker, new Error(event.message))
      this.workers.push(worker)
      this.idle.push(worker)
    }
  }

  build(request: Omit<MeshRequest, 'id'>): Promise<TerrainGeometry> {
    return new Promise((resolve, reject) => {
      this.queue.push({ request: { ...request, id: this.nextId++ }, resolve, reject })
      this.startNext()
    })
  }

  dispose(): void {
    this.workers.forEach((worker) => worker.terminate())
    this.queue.forEach((job) => job.reject(new Error('The terrain is no longer drawn')))
    this.queue.length = 0
  }

  private startNext(): void {
    while (this.idle.length > 0 && this.queue.length > 0) {
      const worker = this.idle.pop()!
      const job = this.queue.shift()!
      this.running.set(worker, job)
      worker.postMessage(job.request)
    }
  }

  private finished(worker: Worker, result: MeshResult): void {
    const job = this.running.get(worker)
    this.running.delete(worker)
    this.idle.push(worker)
    job?.resolve(geometryOf(result))
    this.startNext()
  }

  private failed(worker: Worker, error: Error): void {
    const job = this.running.get(worker)
    this.running.delete(worker)
    this.idle.push(worker)
    job?.reject(error)
    this.startNext()
  }
}

function geometryOf(result: MeshResult): TerrainGeometry {
  return {
    field: ColumnField.ofShape(result.field),
    parts: result.parts.map(({ kind, arrays }) => ({ kind, geometry: geometryOfCompact(arrays), placement: arrays.placement }))
  }
}

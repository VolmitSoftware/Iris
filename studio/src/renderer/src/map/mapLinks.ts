import type { MapPlacement } from './MapController'

/**
 * The maps whose link is on, which move together: one moved by the mouse or the keys moves the others to the same
 * spot and zoom, and one linked later joins them where they look.
 */
export class MapLinks {
  private readonly members = new Map<string, (placement: MapPlacement) => void>()
  private last: MapPlacement | null = null

  /** Links a map; returns where the linked maps look, or null when none moved yet. */
  join(id: string, follow: (placement: MapPlacement) => void): MapPlacement | null {
    this.members.set(id, follow)
    return this.last
  }

  leave(id: string): void {
    this.members.delete(id)
  }

  moved(id: string, placement: MapPlacement): void {
    if (!this.members.has(id)) {
      return
    }
    this.last = placement
    this.members.forEach((follow, member) => member !== id && follow(placement))
  }
}

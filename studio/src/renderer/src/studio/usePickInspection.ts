import { useEffect, useState } from 'react'
import type { BridgeClient, MapLayerQuery, WorldRect } from '../bridge/BridgeClient'
import type { PickInspection } from '../bridge/types'

// Measuring the picks in view samples thousands of spots, so it waits for the view to rest.
const INSPECTION_REST_MILLIS = 350

/**
 * Measures how the noise picks what the view shows, once the view has rested on an area for a moment; the last
 * measurement stays up until the next one arrives, so panning does not blank the panel.
 */
export function usePickInspection(
  client: BridgeClient,
  layer: MapLayerQuery,
  area: WorldRect | null,
  enabled: boolean,
  packRevision: number
): PickInspection | null {
  const [inspection, setInspection] = useState<PickInspection | null>(null)
  useEffect(() => setInspection(null), [layer, packRevision])
  useEffect(() => {
    if (!enabled || area === null) {
      return
    }
    const stop = new AbortController()
    const timer = window.setTimeout(() => {
      client
        .picks(layer, area, stop.signal)
        .then(setInspection)
        .catch(() => undefined)
    }, INSPECTION_REST_MILLIS)
    return () => {
      window.clearTimeout(timer)
      stop.abort()
    }
  }, [client, layer, area, enabled, packRevision])
  return inspection
}

import { useEffect, useRef, useState } from 'react'
import type { Box3 } from 'three'
import type { TileSource } from '../map/TileStore'
import type { GroundInView } from '../scene/groundInView'
import { TerrainStream, type TerrainStyle } from '../scene/terrainStream'
import type { ViewportContent } from './SceneViewport'

/**
 * Endless terrain around {@code focus} for a viewport: the tiles of {@code layer} drawn in {@code style}, where the
 * camera looks. A new layer keeps the ground drawn until its tiles arrive; a new source or focus starts again.
 *
 * @return what to show, or null before there is any, and how many tiles are still on their way
 */
export function useTerrainStream<Tile, Layer>(
  source: TileSource<Tile, Layer, never>,
  coarsestLevel: number,
  focus: Box3,
  layer: Layer | null,
  style: TerrainStyle<Tile> | null,
  tell: (message: string) => void
): { content: ViewportContent | null; pending: number; stream: TerrainStream<Tile, Layer> | null } {
  const [stream, setStream] = useState<TerrainStream<Tile, Layer> | null>(null)
  const [pending, setPending] = useState(0)
  const latestTell = useRef(tell)
  latestTell.current = tell

  useEffect(() => {
    const created = new TerrainStream({ source, coarsestLevel, focus, onPending: setPending, onError: (message) => latestTell.current(message) })
    setStream(created)
    return () => {
      created.dispose()
      setStream(null)
    }
  }, [source, coarsestLevel, focus])
  useEffect(() => {
    if (layer !== null) {
      stream?.showLayer(layer)
    }
  }, [stream, layer])
  useEffect(() => {
    if (style !== null) {
      stream?.restyle(style)
    }
  }, [stream, style])

  return { content: stream?.content ?? null, pending, stream }
}

// Moving the camera a little samples a tile or two, which is over before it is worth a word.
const WORTH_TELLING_MILLIS = 700

/** Whether tiles have been on their way for long enough to say so. */
export function useLongPending(pending: number): boolean {
  const waiting = pending > 0
  const [long, setLong] = useState(false)
  useEffect(() => {
    if (!waiting) {
      setLong(false)
      return
    }
    const timer = window.setTimeout(() => setLong(true), WORTH_TELLING_MILLIS)
    return () => window.clearTimeout(timer)
  }, [waiting])
  return long
}

// The camera drifts and tiles arrive in bursts; the ground it sees is read once they have rested this long.
const GROUND_REST_MILLIS = 250

/** The ground the camera of a stream looks at, read once the view has rested; the last reading stays until the next one. */
export function useGroundInView<Tile, Layer>(stream: TerrainStream<Tile, Layer> | null): GroundInView | null {
  const [ground, setGround] = useState<GroundInView | null>(null)
  useEffect(() => {
    if (stream === null) {
      return
    }
    let timer: number | undefined
    const stopWatching = stream.onSightChanged(() => {
      window.clearTimeout(timer)
      timer = window.setTimeout(() => setGround(stream.groundInView()), GROUND_REST_MILLIS)
    })
    return () => {
      stopWatching()
      window.clearTimeout(timer)
    }
  }, [stream])
  return ground
}

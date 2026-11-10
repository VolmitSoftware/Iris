import { useEffect, useRef, useState } from 'react'
import type { Box3 } from 'three'
import type { TileSource } from '../map/TileStore'
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
): { content: ViewportContent | null; pending: number } {
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

  return { content: stream?.content ?? null, pending }
}

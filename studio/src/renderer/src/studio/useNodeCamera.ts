import { useCallback, useEffect, useState } from 'react'
import { GRID, type Bounds, type Point } from '../editor/nodeLayout'

const MARGIN = GRID
const SMALLEST_SCALE = 0.25
const LARGEST_SCALE = 2.5
const WHEEL_ZOOM_PER_PIXEL = 0.0015

/** Where the canvas looks: the screen offset of the graph's origin, and how much it is zoomed. */
export interface Camera {
  x: number
  y: number
  scale: number
}

export interface NodeCamera {
  camera: Camera
  panBy(dx: number, dy: number): void
  /** Zooms out, never in past 100%, until every node fits, and centres them. */
  showAll(bounds: Bounds | null): void
  resetZoom(): void
  /** Moves the view so the rectangle is in sight, when it is not already. */
  reveal(area: Bounds): void
  /** The graph point under a point of the window. */
  graphPoint(clientX: number, clientY: number): Point
}

/** The view of the node canvas in {@code frame}: dragged to pan, zoomed by the wheel around the pointer; it opens at 100% on the top left node. */
export function useNodeCamera(frame: React.RefObject<HTMLDivElement | null>, opening: Bounds | null): NodeCamera {
  const [camera, setCamera] = useState<Camera>(() => ({ x: MARGIN - (opening?.left ?? MARGIN), y: MARGIN - (opening?.top ?? MARGIN), scale: 1 }))

  useEffect(() => {
    const element = frame.current
    if (element === null) {
      return
    }
    const wheel = (event: WheelEvent): void => {
      event.preventDefault()
      const bounds = element.getBoundingClientRect()
      setCamera((current) => zoomedAround(current, { x: event.clientX - bounds.left, y: event.clientY - bounds.top }, Math.exp(-event.deltaY * WHEEL_ZOOM_PER_PIXEL)))
    }
    element.addEventListener('wheel', wheel, { passive: false })
    return () => element.removeEventListener('wheel', wheel)
  }, [frame])

  const panBy = useCallback((dx: number, dy: number) => setCamera((current) => ({ ...current, x: current.x + dx, y: current.y + dy })), [])

  const showAll = useCallback(
    (bounds: Bounds | null) => {
      const element = frame.current
      if (element === null || bounds === null) {
        return
      }
      const [width, height] = [bounds.right - bounds.left + 2 * MARGIN, bounds.bottom - bounds.top + 2 * MARGIN]
      const scale = clamp(Math.min(element.clientWidth / width, element.clientHeight / height), SMALLEST_SCALE, 1)
      setCamera({ scale, x: element.clientWidth / 2 - ((bounds.left + bounds.right) / 2) * scale, y: element.clientHeight / 2 - ((bounds.top + bounds.bottom) / 2) * scale })
    },
    [frame]
  )

  const resetZoom = useCallback(() => {
    const element = frame.current
    if (element !== null) {
      setCamera((current) => zoomedAround(current, { x: element.clientWidth / 2, y: element.clientHeight / 2 }, 1 / current.scale))
    }
  }, [frame])

  const reveal = useCallback(
    (area: Bounds) => {
      const element = frame.current
      if (element === null) {
        return
      }
      setCamera((current) => {
        const [left, top] = [current.x + area.left * current.scale, current.y + area.top * current.scale]
        const [right, bottom] = [current.x + area.right * current.scale, current.y + area.bottom * current.scale]
        const height = Math.min(bottom - top, element.clientHeight)
        const dx = left < 0 || right > element.clientWidth ? MARGIN - left : 0
        const dy = top < 0 || top + height > element.clientHeight ? MARGIN - top : 0
        return dx === 0 && dy === 0 ? current : { ...current, x: current.x + dx, y: current.y + dy }
      })
    },
    [frame]
  )

  const graphPoint = useCallback(
    (clientX: number, clientY: number): Point => {
      const bounds = frame.current!.getBoundingClientRect()
      return { x: (clientX - bounds.left - camera.x) / camera.scale, y: (clientY - bounds.top - camera.y) / camera.scale }
    },
    [frame, camera]
  )

  return { camera, panBy, showAll, resetZoom, reveal, graphPoint }
}

function zoomedAround(current: Camera, anchor: Point, factor: number): Camera {
  const scale = clamp(current.scale * factor, SMALLEST_SCALE, LARGEST_SCALE)
  const ratio = scale / current.scale
  return { scale, x: anchor.x - (anchor.x - current.x) * ratio, y: anchor.y - (anchor.y - current.y) * ratio }
}

function clamp(value: number, lowest: number, highest: number): number {
  return Math.min(highest, Math.max(lowest, value))
}

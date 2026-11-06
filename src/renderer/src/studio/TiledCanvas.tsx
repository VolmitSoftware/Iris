import { useEffect, useRef } from 'react'
import type { TiledView } from '../map/TiledView'

type AnyTiledView = TiledView<unknown, unknown, unknown>

interface TiledCanvasProps<View extends AnyTiledView> {
  /** Makes the view on the canvas; called again only when it changes, so keep it stable. */
  create(canvas: HTMLCanvasElement): View
  viewRef: React.RefObject<View | null>
  className?: string
}

// One wheel notch is about 100; this zooms about 15% per notch, and smoothly on touchpads.
const WHEEL_ZOOM_PER_DELTA = 0.002

/** A tiled view from above: drag to move, wheel to zoom. */
export function TiledCanvas<View extends AnyTiledView>({ create, viewRef, className }: TiledCanvasProps<View>): React.JSX.Element {
  const canvasRef = useRef<HTMLCanvasElement>(null)

  useEffect(() => {
    const canvas = canvasRef.current!
    const view = create(canvas)
    viewRef.current = view
    const resize = new ResizeObserver(([entry]) => view.resize(entry.contentRect.width, entry.contentRect.height))
    resize.observe(canvas)
    const stopMouse = followMouse(canvas, view)
    return () => {
      stopMouse()
      resize.disconnect()
      view.close()
      viewRef.current = null
    }
  }, [create, viewRef])

  return <canvas ref={canvasRef} className={className} />
}

function followMouse(canvas: HTMLCanvasElement, view: AnyTiledView): () => void {
  let last: { x: number; y: number } | null = null
  const pointOf = (event: MouseEvent): { x: number; y: number } => {
    const bounds = canvas.getBoundingClientRect()
    return { x: event.clientX - bounds.left, y: event.clientY - bounds.top }
  }
  const down = (event: PointerEvent): void => {
    if (event.button === 0) {
      canvas.setPointerCapture(event.pointerId)
      last = { x: event.clientX, y: event.clientY }
    }
  }
  const move = (event: PointerEvent): void => {
    view.setCursor(pointOf(event))
    if (last !== null) {
      view.panBy(event.clientX - last.x, event.clientY - last.y)
      last = { x: event.clientX, y: event.clientY }
    }
  }
  const up = (): void => {
    last = null
  }
  const leave = (): void => view.setCursor(null)
  const wheel = (event: WheelEvent): void => {
    event.preventDefault()
    const point = pointOf(event)
    view.zoomAt(2 ** (-event.deltaY * WHEEL_ZOOM_PER_DELTA), point.x, point.y)
  }
  canvas.addEventListener('pointerdown', down)
  canvas.addEventListener('pointermove', move)
  canvas.addEventListener('pointerup', up)
  canvas.addEventListener('pointerleave', leave)
  canvas.addEventListener('wheel', wheel, { passive: false })
  return () => {
    canvas.removeEventListener('pointerdown', down)
    canvas.removeEventListener('pointermove', move)
    canvas.removeEventListener('pointerup', up)
    canvas.removeEventListener('pointerleave', leave)
    canvas.removeEventListener('wheel', wheel)
  }
}

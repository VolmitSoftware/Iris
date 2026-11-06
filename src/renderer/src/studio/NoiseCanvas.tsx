import { useEffect, useRef } from 'react'
import type { BridgeClient } from '../bridge/BridgeClient'
import { NoiseController, type NoiseListener } from '../noise/NoiseController'

interface NoiseCanvasProps {
  client: BridgeClient
  controllerRef: React.RefObject<NoiseController | null>
  listener: NoiseListener
  className?: string
}

// One wheel notch is about 100; this zooms about 15% per notch, and smoothly on touchpads.
const WHEEL_ZOOM_PER_DELTA = 0.002

/** The noise from above: drag to move, wheel to zoom. */
export function NoiseCanvas({ client, controllerRef, listener, className }: NoiseCanvasProps): React.JSX.Element {
  const canvasRef = useRef<HTMLCanvasElement>(null)
  const latestListener = useRef(listener)
  latestListener.current = listener

  useEffect(() => {
    const canvas = canvasRef.current!
    const noise = new NoiseController(canvas, client, {
      onStatus: (status) => latestListener.current.onStatus(status),
      onError: (message) => latestListener.current.onError(message)
    })
    controllerRef.current = noise
    const resize = new ResizeObserver(([entry]) => noise.resize(entry.contentRect.width, entry.contentRect.height))
    resize.observe(canvas)
    const stopMouse = followMouse(canvas, noise)
    return () => {
      stopMouse()
      resize.disconnect()
      noise.close()
      controllerRef.current = null
    }
  }, [client, controllerRef])

  return <canvas ref={canvasRef} className={className} />
}

function followMouse(canvas: HTMLCanvasElement, noise: NoiseController): () => void {
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
    noise.setCursor(pointOf(event))
    if (last !== null) {
      noise.panBy(event.clientX - last.x, event.clientY - last.y)
      last = { x: event.clientX, y: event.clientY }
    }
  }
  const up = (): void => {
    last = null
  }
  const leave = (): void => noise.setCursor(null)
  const wheel = (event: WheelEvent): void => {
    event.preventDefault()
    const point = pointOf(event)
    noise.zoomAt(2 ** (-event.deltaY * WHEEL_ZOOM_PER_DELTA), point.x, point.y)
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

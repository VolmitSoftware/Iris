import { useEffect, useRef, useState } from 'react'
import {
  ContextMenu,
  ContextMenuContent,
  ContextMenuItem,
  ContextMenuSeparator,
  ContextMenuTrigger
} from '@/components/ui/context-menu'
import type { BridgeClient } from '../bridge/BridgeClient'
import type { WorldInfo } from '../bridge/types'
import { MapController, type MapListener, type SpotSubjects } from '../map/MapController'
import type { Highlight } from '../map/tileStyle'

export interface CanvasPoint {
  x: number
  y: number
}

interface MapCanvasProps {
  client: BridgeClient
  world: WorldInfo
  viewId: string
  controllerRef: React.RefObject<MapController | null>
  listener: MapListener
  highlight: Highlight | null
  onPointer(point: CanvasPoint | null): void
  onDragged(): void
  onTeleport(point: CanvasPoint): void
  onOpenFile(point: CanvasPoint): void
  onHighlight(subject: Highlight): void
  onCopy(text: string): void
}

// A press that moves less than this is a click, not a drag.
const CLICK_SLOP_PIXELS = 4
// One wheel notch is about 100; this zooms about 15% per notch, and smoothly on touchpads.
const WHEEL_ZOOM_PER_DELTA = 0.002

interface ContextSpot {
  point: CanvasPoint
  subjects: SpotSubjects
  blockX: number
  blockZ: number
}

/** The map canvas and its mouse: drag to move, wheel to zoom, Ctrl+click to teleport, Alt+click to open a file. */
export function MapCanvas(props: MapCanvasProps): React.JSX.Element {
  const { client, world, viewId, controllerRef } = props
  const canvasRef = useRef<HTMLCanvasElement>(null)
  const latest = useRef(props)
  latest.current = props
  const [contextSpot, setContextSpot] = useState<ContextSpot | null>(null)

  useEffect(() => {
    const canvas = canvasRef.current!
    const map = new MapController(canvas, client, world, viewId, {
      onStatus: (status) => latest.current.listener.onStatus(status),
      onShares: (shares) => latest.current.listener.onShares(shares),
      onArea: (area) => latest.current.listener.onArea(area),
      onError: (message) => latest.current.listener.onError(message)
    })
    controllerRef.current = map
    const resize = new ResizeObserver(([entry]) => map.resize(entry.contentRect.width, entry.contentRect.height))
    resize.observe(canvas)
    return () => {
      resize.disconnect()
      map.close()
      controllerRef.current = null
    }
    // The controller lives as long as the connection; later view changes go through setView, not a new controller.
  }, [client, world, controllerRef])

  useMouse(canvasRef, controllerRef, latest)

  return (
    <ContextMenu
      onOpenChange={(open) => {
        if (!open) {
          setContextSpot(null)
        }
      }}
    >
      <ContextMenuTrigger asChild>
        <canvas
          ref={canvasRef}
          className="absolute inset-0 size-full cursor-grab active:cursor-grabbing"
          onContextMenu={(event) => {
            const map = controllerRef.current
            if (map === null) {
              return
            }
            const point = canvasPoint(event)
            const spot = map.worldAt(point.x, point.y)
            setContextSpot({ point, subjects: map.subjectsAt(point.x, point.y), blockX: Math.floor(spot.x), blockZ: Math.floor(spot.z) })
          }}
        />
      </ContextMenuTrigger>
      <ContextMenuContent className="min-w-56">
        {contextSpot !== null && <SpotMenu spot={contextSpot} viewId={viewId} props={props} />}
      </ContextMenuContent>
    </ContextMenu>
  )
}

function SpotMenu({ spot, viewId, props }: { spot: ContextSpot; viewId: string; props: MapCanvasProps }): React.JSX.Element {
  const { subject, region } = spot.subjects
  const coordinates = `${spot.blockX}, ${spot.blockZ}`
  return (
    <>
      {subject !== null && <ContextMenuItem onSelect={() => props.onHighlight(subject)}>{highlightText(subject, props.highlight)}</ContextMenuItem>}
      {region !== null && subject?.kind !== 'REGION' && (
        <ContextMenuItem onSelect={() => props.onHighlight(region)}>{highlightText(region, props.highlight)}</ContextMenuItem>
      )}
      <ContextMenuItem onSelect={() => props.onTeleport(spot.point)}>Teleport here</ContextMenuItem>
      <ContextMenuItem onSelect={() => props.onOpenFile(spot.point)}>{viewId === 'regions' ? 'Open region file' : 'Open biome file'}</ContextMenuItem>
      <ContextMenuSeparator />
      <ContextMenuItem onSelect={() => props.onCopy(coordinates)}>Copy coordinates ({coordinates})</ContextMenuItem>
    </>
  )
}

function highlightText(subject: Highlight, current: Highlight | null): string {
  const highlighted = current !== null && current.kind === subject.kind && current.key === subject.key
  return `${highlighted ? 'Clear highlight of' : 'Highlight'} ${subject.name}`
}

function canvasPoint(event: { clientX: number; clientY: number; currentTarget: Element }): CanvasPoint {
  const bounds = event.currentTarget.getBoundingClientRect()
  return { x: event.clientX - bounds.left, y: event.clientY - bounds.top }
}

function useMouse(
  canvasRef: React.RefObject<HTMLCanvasElement | null>,
  controllerRef: React.RefObject<MapController | null>,
  latest: React.RefObject<MapCanvasProps>
): void {
  useEffect(() => {
    const canvas = canvasRef.current!
    let press: { x: number; y: number; lastX: number; lastY: number; dragged: boolean } | null = null

    const down = (event: PointerEvent): void => {
      if (event.button !== 0) {
        return
      }
      canvas.setPointerCapture(event.pointerId)
      press = { x: event.clientX, y: event.clientY, lastX: event.clientX, lastY: event.clientY, dragged: false }
    }
    const move = (event: PointerEvent): void => {
      const point = canvasPoint({ clientX: event.clientX, clientY: event.clientY, currentTarget: canvas })
      controllerRef.current?.setCursor(point)
      latest.current.onPointer(point)
      if (press === null) {
        return
      }
      if (!press.dragged && Math.hypot(event.clientX - press.x, event.clientY - press.y) >= CLICK_SLOP_PIXELS) {
        press.dragged = true
        latest.current.onDragged()
      }
      if (press.dragged) {
        controllerRef.current?.panBy(event.clientX - press.lastX, event.clientY - press.lastY)
      }
      press.lastX = event.clientX
      press.lastY = event.clientY
    }
    const up = (event: PointerEvent): void => {
      if (press === null || event.button !== 0) {
        return
      }
      const wasClick = !press.dragged
      press = null
      if (!wasClick) {
        return
      }
      const point = canvasPoint({ clientX: event.clientX, clientY: event.clientY, currentTarget: canvas })
      if (event.ctrlKey || event.metaKey) {
        latest.current.onTeleport(point)
      } else if (event.altKey) {
        latest.current.onOpenFile(point)
      }
    }
    const leave = (): void => {
      controllerRef.current?.setCursor(null)
      latest.current.onPointer(null)
    }
    const wheel = (event: WheelEvent): void => {
      event.preventDefault()
      const point = canvasPoint({ clientX: event.clientX, clientY: event.clientY, currentTarget: canvas })
      controllerRef.current?.zoomAt(2 ** (-event.deltaY * WHEEL_ZOOM_PER_DELTA), point.x, point.y)
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
  }, [canvasRef, controllerRef, latest])
}

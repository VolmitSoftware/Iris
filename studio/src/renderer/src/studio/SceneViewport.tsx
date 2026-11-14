import { useEffect, useRef, useState } from 'react'
import { ContextMenu, ContextMenuContent, ContextMenuTrigger } from '@/components/ui/context-menu'
import { Box3, ColorManagement, LinearSRGBColorSpace, MOUSE, PerspectiveCamera, Ray, Raycaster, Scene, Sphere, Vector2, Vector3, WebGLRenderer, type Object3D } from 'three'
import { OrbitControls } from 'three/examples/jsm/controls/OrbitControls.js'
import type { SightView } from '../scene/cameraSight'
import { surfaceAlong } from '../scene/surfacePick'

// Colours are worked out as Minecraft and v3e do, on the values a screen shows: a texture times its tint times the light
// on its face. Left to manage colours, three.js would take those values for linear light and encode them again, which
// pales every colour and flattens the shading.
ColorManagement.enabled = false

const FIELD_OF_VIEW_DEGREES = 45
// Looking down at the scene from the south east, as v3e's object view opens.
const VIEW_DIRECTION = new Vector3(1, 0.8, 1.2).normalize()
const FRAMING_MARGIN = 1.15
const NEAREST_CLIP = 0.05
// The near plane follows the orbit distance: a fixed tiny one leaves too little depth precision far away, so surfaces a block apart flicker.
const NEAR_CLIP_PER_ORBIT_DISTANCE = 0.01
// The far plane keeps up with zooming out, so ground reaching to the horizon stays in view.
const FAR_CLIP_PER_ORBIT_DISTANCE = 20

// A press that moves further than this between pressing and releasing is a drag, not a click.
const CLICK_SLOP_PIXELS = 4

// Ground is looked at from above: the camera stops a little over the horizon instead of going under it.
const LOWEST_OVER_GROUND_RADIANS = Math.PI / 2 - 0.05
const TURNS_ON_DRAG = { LEFT: MOUSE.ROTATE, MIDDLE: MOUSE.PAN, RIGHT: MOUSE.PAN }
const MOVES_ON_DRAG = { LEFT: MOUSE.PAN, MIDDLE: MOUSE.ROTATE, RIGHT: MOUSE.ROTATE }

const LEFT_BUTTON = 0
const RIGHT_BUTTON = 2

function picks(event: PointerEvent): boolean {
  return event.button === LEFT_BUTTON && !event.shiftKey && !event.ctrlKey && !event.metaKey
}

// As on the map: a right click, or a click while holding Ctrl.
function asksAboutSpot(event: PointerEvent): boolean {
  return event.button === RIGHT_BUTTON || (event.button === LEFT_BUTTON && (event.ctrlKey || event.metaKey))
}

/** The camera of a viewport, for content that draws only what the camera looks at. */
export interface CameraLink {
  /** Where the camera is and how it looks now. */
  view(): SightView
  /** Tells the listener whenever the camera moves or the viewport changes size; returns how to stop. */
  onMoved(listener: () => void): () => void
  /** Draws the viewport again, after the content changed by itself. */
  redraw(): void
}

/** What a viewport shows: one object, and the box the camera frames when it is first shown. */
export interface ViewportContent {
  object: Object3D
  bounds: Box3
  /** Ground to travel over: a drag moves it as the map does and a right drag turns the view; anything else turns on a drag. */
  isGround?: boolean
  /** Where a ray first meets what is drawn within {@code reach}, or null; without it the viewport casts the ray at the object's meshes. */
  surfaceAlong?(ray: Ray, reach: number): Vector3 | null
  /** Called when the content is shown, so it can follow the camera; returns how to stop following when it goes. */
  follow?(camera: CameraLink): () => void
}

/** Where a click met the content, and the way it was looking; null when the click met nothing. */
export type PickedSurface = { point: Vector3; direction: Vector3 } | null

interface SceneViewportProps {
  content: ViewportContent | null
  /** Changes when the camera should frame the content again; the same content keeps the camera where it is. */
  framing?: unknown
  /** Called when the content is clicked, not dragged. */
  onPick?(picked: PickedSurface): void
  /** What a spot on the content offers, as the map does for a spot on it. */
  spotActions?: SpotActions
}

export interface SpotActions {
  /** Called when a spot is clicked while holding Ctrl. */
  onCtrlClick(spot: Vector3): void
  /** The items of the menu a right click on a spot opens. */
  menuItems(spot: Vector3): React.ReactNode
}

/** A spot the user asked about, and where on the screen they did. */
interface AskedSpot {
  spot: Vector3
  gesture: 'ctrlClick' | 'rightClick'
  clientX: number
  clientY: number
}

/**
 * Something in 3D. An object turns on a drag and moves on a right drag; ground moves on a drag, as the map does, and
 * turns on a right drag. Either turns around what is in the middle, and scrolling comes closer to the cursor. The
 * camera stops with the mouse, and the view is drawn only when it changes.
 */
export function SceneViewport({ content, framing, onPick, spotActions }: SceneViewportProps): React.JSX.Element {
  const host = useRef<HTMLDivElement>(null)
  const stage = useRef<Stage | null>(null)
  const [menuSpot, setMenuSpot] = useState<Vector3 | null>(null)
  const latestSpotActions = useRef(spotActions)
  latestSpotActions.current = spotActions

  useEffect(() => {
    const created = new Stage(host.current!)
    stage.current = created
    return () => {
      created.dispose()
      stage.current = null
    }
  }, [])

  useEffect(() => stage.current?.show(content), [content])
  useEffect(() => stage.current?.frameShown(), [framing])
  useEffect(() => stage.current?.onPick(onPick ?? null), [onPick])

  const offersSpotActions = spotActions !== undefined
  useEffect(() => {
    if (!offersSpotActions) {
      return
    }
    stage.current?.onSpotAsked((asked) => {
      if (asked.gesture === 'ctrlClick') {
        latestSpotActions.current?.onCtrlClick(asked.spot)
        return
      }
      setMenuSpot(asked.spot)
      openMenuAt(host.current!, asked.clientX, asked.clientY)
    })
    return () => stage.current?.onSpotAsked(null)
  }, [offersSpotActions])
  useEffect(() => keepPressFromOpeningMenu(host.current!), [])

  return (
    <ContextMenu onOpenChange={(open) => !open && setMenuSpot(null)}>
      <ContextMenuTrigger asChild>
        <div ref={host} className="absolute inset-0 bg-map-background" />
      </ContextMenuTrigger>
      <ContextMenuContent className="min-w-56">{menuSpot !== null && spotActions?.menuItems(menuSpot)}</ContextMenuContent>
    </ContextMenu>
  )
}

// A right press turns the view, so the menu waits for a right click that did not: the browser's own request for a menu,
// which comes with the press, is kept from the menu, and the viewport asks for it itself.
function keepPressFromOpeningMenu(host: HTMLElement): () => void {
  const swallow = (event: MouseEvent): void => {
    if (event.isTrusted) {
      event.preventDefault()
      event.stopPropagation()
    }
  }
  host.addEventListener('contextmenu', swallow, { capture: true })
  return () => host.removeEventListener('contextmenu', swallow, { capture: true })
}

function openMenuAt(host: HTMLElement, clientX: number, clientY: number): void {
  host.dispatchEvent(new MouseEvent('contextmenu', { bubbles: true, cancelable: true, clientX, clientY }))
}

/** The renderer, camera and controls of one viewport. What it shows belongs to whoever built it, who frees it. */
class Stage {
  private readonly renderer = new WebGLRenderer({ antialias: true, alpha: true })
  private readonly camera = new PerspectiveCamera(FIELD_OF_VIEW_DEGREES, 1, NEAREST_CLIP, 10_000)
  private readonly scene = new Scene()
  private readonly controls: OrbitControls
  private readonly resizes: ResizeObserver
  private readonly moveListeners = new Set<() => void>()
  private shown: ViewportContent | null = null
  private stopFollowing: (() => void) | null = null
  private framed = false
  private framedFar = this.camera.far
  private frameRequested = false
  private pickListener: ((picked: PickedSurface) => void) | null = null
  private pressedAt: { x: number; y: number } | null = null
  private spotListener: ((asked: AskedSpot) => void) | null = null
  private spotPressedAt: { x: number; y: number } | null = null

  constructor(private readonly host: HTMLDivElement) {
    this.renderer.outputColorSpace = LinearSRGBColorSpace
    this.renderer.setPixelRatio(window.devicePixelRatio)
    this.renderer.setClearAlpha(0)
    host.appendChild(this.renderer.domElement)
    this.controls = new OrbitControls(this.camera, this.renderer.domElement)
    this.controls.zoomToCursor = true
    this.navigateAs(false)
    this.renderer.domElement.addEventListener('pointerdown', (event) => {
      this.pressedAt = picks(event) ? { x: event.clientX, y: event.clientY } : null
      this.spotPressedAt = asksAboutSpot(event) ? { x: event.clientX, y: event.clientY } : null
      this.pivotOnSurfaceInMiddle()
    })
    this.renderer.domElement.addEventListener('pointerup', (event) => {
      this.pickIfClicked(event)
      this.askAboutSpotIfClicked(event)
    })
    this.controls.addEventListener('change', () => {
      this.fitClipToOrbit()
      this.notifyMoved()
      this.requestFrame()
    })
    this.resizes = new ResizeObserver(() => this.fitToHost())
    this.resizes.observe(host)
    this.fitToHost()
  }

  show(content: ViewportContent | null): void {
    this.removeShown()
    this.shown = content
    if (content !== null) {
      this.scene.add(content.object)
      this.navigateAs(content.isGround === true)
      if (!this.framed) {
        this.frameShown()
      }
      this.stopFollowing = content.follow?.(this.cameraLink()) ?? null
    }
    this.requestFrame()
  }

  private navigateAs(ground: boolean): void {
    this.controls.mouseButtons = ground ? MOVES_ON_DRAG : TURNS_ON_DRAG
    // Ground moves along itself, so dragging up brings what is further away closer instead of lifting the camera.
    this.controls.screenSpacePanning = !ground
    this.controls.maxPolarAngle = ground ? LOWEST_OVER_GROUND_RADIANS : Math.PI
  }

  private removeShown(): void {
    this.stopFollowing?.()
    this.stopFollowing = null
    if (this.shown !== null) {
      this.scene.remove(this.shown.object)
    }
  }

  private cameraLink(): CameraLink {
    return {
      view: () => ({
        camera: this.camera,
        width: this.host.clientWidth,
        height: this.host.clientHeight,
        orbitDistance: this.camera.position.distanceTo(this.controls.target)
      }),
      onMoved: (listener) => {
        this.moveListeners.add(listener)
        return () => this.moveListeners.delete(listener)
      },
      redraw: () => this.requestFrame()
    }
  }

  private notifyMoved(): void {
    this.moveListeners.forEach((listener) => listener())
  }

  frameShown(): void {
    if (this.shown === null) {
      this.framed = false
      return
    }
    this.framed = true
    const bounds = this.shown.bounds
    const sphere = bounds.isEmpty() ? new Sphere(new Vector3(), 1) : bounds.getBoundingSphere(new Sphere())
    const halfVertical = (FIELD_OF_VIEW_DEGREES * Math.PI) / 360
    const halfHorizontal = Math.atan(Math.tan(halfVertical) * this.camera.aspect)
    const distance = (sphere.radius * FRAMING_MARGIN) / Math.sin(Math.min(halfVertical, halfHorizontal))
    this.controls.target.copy(sphere.center)
    this.camera.position.copy(sphere.center).addScaledVector(VIEW_DIRECTION, distance)
    this.framedFar = distance * 10 + sphere.radius * 4
    this.controls.update()
    this.fitClipToOrbit()
    this.notifyMoved()
    this.requestFrame()
  }

  onPick(listener: ((picked: PickedSurface) => void) | null): void {
    this.pickListener = listener
  }

  // A press that stays where it was is a click; one that moved turned the view.
  private pickIfClicked(event: PointerEvent): void {
    const pressed = this.pressedAt
    this.pressedAt = null
    if (pressed === null || this.shown === null || this.pickListener === null) {
      return
    }
    if (Math.hypot(event.clientX - pressed.x, event.clientY - pressed.y) > CLICK_SLOP_PIXELS) {
      return
    }
    this.pickListener(this.surfaceUnder(event))
  }

  onSpotAsked(listener: ((asked: AskedSpot) => void) | null): void {
    this.spotListener = listener
  }

  private askAboutSpotIfClicked(event: PointerEvent): void {
    const pressed = this.spotPressedAt
    this.spotPressedAt = null
    if (pressed === null || this.spotListener === null) {
      return
    }
    if (Math.hypot(event.clientX - pressed.x, event.clientY - pressed.y) > CLICK_SLOP_PIXELS) {
      return
    }
    const surface = this.surfaceUnder(event)
    if (surface === null) {
      return
    }
    const gesture = event.button === RIGHT_BUTTON ? 'rightClick' : 'ctrlClick'
    this.spotListener({ spot: surface.point, gesture, clientX: event.clientX, clientY: event.clientY })
  }

  private surfaceUnder(event: PointerEvent): PickedSurface {
    if (this.shown === null) {
      return null
    }
    const bounds = this.renderer.domElement.getBoundingClientRect()
    const pointer = new Vector2(((event.clientX - bounds.left) / bounds.width) * 2 - 1, -((event.clientY - bounds.top) / bounds.height) * 2 + 1)
    const caster = new Raycaster()
    this.camera.updateMatrixWorld()
    caster.setFromCamera(pointer, this.camera)
    const point = surfaceAlong(this.shown, caster.ray, this.camera.far)
    return point === null ? null : { point, direction: caster.ray.direction.clone() }
  }

  // The pivot slides along the line of sight, so the picture stays put while turning now goes around what is there,
  // and moving keeps pace with it.
  private pivotOnSurfaceInMiddle(): void {
    if (this.shown === null) {
      return
    }
    const ray = new Ray(this.camera.getWorldPosition(new Vector3()), this.camera.getWorldDirection(new Vector3()))
    const hit = surfaceAlong(this.shown, ray, this.camera.far)
    if (hit === null) {
      return
    }
    this.controls.target.copy(hit)
    this.fitClipToOrbit()
    this.notifyMoved()
    this.requestFrame()
  }

  private fitClipToOrbit(): void {
    const orbitDistance = this.camera.position.distanceTo(this.controls.target)
    this.camera.near = Math.max(NEAREST_CLIP, orbitDistance * NEAR_CLIP_PER_ORBIT_DISTANCE)
    this.camera.far = Math.max(this.framedFar, orbitDistance * FAR_CLIP_PER_ORBIT_DISTANCE)
    this.camera.updateProjectionMatrix()
  }

  dispose(): void {
    this.removeShown()
    this.resizes.disconnect()
    this.controls.dispose()
    this.renderer.dispose()
    this.renderer.domElement.remove()
  }

  private fitToHost(): void {
    const width = Math.max(1, this.host.clientWidth)
    const height = Math.max(1, this.host.clientHeight)
    this.renderer.setSize(width, height)
    this.camera.aspect = width / height
    this.camera.updateProjectionMatrix()
    this.notifyMoved()
    this.requestFrame()
  }

  private requestFrame(): void {
    if (this.frameRequested) {
      return
    }
    this.frameRequested = true
    requestAnimationFrame(() => {
      this.frameRequested = false
      this.renderer.render(this.scene, this.camera)
    })
  }
}

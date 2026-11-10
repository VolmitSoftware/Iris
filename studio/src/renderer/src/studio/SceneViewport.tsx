import { useEffect, useRef } from 'react'
import { Box3, MOUSE, PerspectiveCamera, Ray, Scene, Sphere, Vector3, WebGLRenderer, type Object3D } from 'three'
import { OrbitControls } from 'three/examples/jsm/controls/OrbitControls.js'
import type { SightView } from '../scene/cameraSight'
import { surfaceAlong } from '../scene/surfacePick'

const FIELD_OF_VIEW_DEGREES = 45
// Looking down at the scene from the south east, as v3e's object view opens.
const VIEW_DIRECTION = new Vector3(1, 0.8, 1.2).normalize()
const FRAMING_MARGIN = 1.15
const NEAREST_CLIP = 0.05
// The near plane follows the orbit distance: a fixed tiny one leaves too little depth precision far away, so surfaces a block apart flicker.
const NEAR_CLIP_PER_ORBIT_DISTANCE = 0.01
// The far plane keeps up with zooming out, so ground reaching to the horizon stays in view.
const FAR_CLIP_PER_ORBIT_DISTANCE = 20

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
  /** Where a ray first meets what is drawn within {@code reach}, or null; without it the viewport casts the ray at the object's meshes. */
  surfaceAlong?(ray: Ray, reach: number): Vector3 | null
  /** Called when the content is shown, so it can follow the camera; returns how to stop following when it goes. */
  follow?(camera: CameraLink): () => void
}

interface SceneViewportProps {
  content: ViewportContent | null
  /** Changes when the camera should frame the content again; the same content keeps the camera where it is. */
  framing?: unknown
}

/** Something in 3D: drag to turn around what is in the middle, right drag to move, scroll to come closer to the cursor. Drawn only when it changes. */
export function SceneViewport({ content, framing }: SceneViewportProps): React.JSX.Element {
  const host = useRef<HTMLDivElement>(null)
  const stage = useRef<Stage | null>(null)

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

  return <div ref={host} className="absolute inset-0 bg-map-background" />
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

  constructor(private readonly host: HTMLDivElement) {
    this.renderer.setPixelRatio(window.devicePixelRatio)
    this.renderer.setClearAlpha(0)
    host.appendChild(this.renderer.domElement)
    this.controls = new OrbitControls(this.camera, this.renderer.domElement)
    this.controls.enableDamping = true
    this.controls.zoomToCursor = true
    this.controls.mouseButtons = { LEFT: MOUSE.ROTATE, MIDDLE: MOUSE.PAN, RIGHT: MOUSE.PAN }
    this.renderer.domElement.addEventListener('pointerdown', (event) => this.pivotOnSurfaceInMiddle(event))
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
      if (!this.framed) {
        this.frameShown()
      }
      this.stopFollowing = content.follow?.(this.cameraLink()) ?? null
    }
    this.requestFrame()
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

  // The pivot slides along the line of sight, so the picture stays put while turning now goes around what is there.
  private pivotOnSurfaceInMiddle(event: PointerEvent): void {
    const turns = event.button === 0 && !event.shiftKey && !event.ctrlKey && !event.metaKey
    if (!turns || this.shown === null) {
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
      // Damping keeps turning the camera for a moment after a drag, and asks for frames until it stops.
      this.controls.update()
      this.renderer.render(this.scene, this.camera)
    })
  }
}

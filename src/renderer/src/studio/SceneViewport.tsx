import { useEffect, useRef } from 'react'
import { Box3, Mesh, MeshBasicMaterial, PerspectiveCamera, Scene, Sphere, Vector3, WebGLRenderer } from 'three'
import { OrbitControls } from 'three/examples/jsm/controls/OrbitControls.js'
import type { SceneMesh } from '../scene/sceneMesh'

const FIELD_OF_VIEW_DEGREES = 45
// Looking down at the scene from the south east, as v3e's object view opens.
const VIEW_DIRECTION = new Vector3(1, 0.8, 1.2).normalize()
const FRAMING_MARGIN = 1.15
const NEAREST_CLIP = 0.05
const CUTOUT_ALPHA = 0.1

interface SceneViewportProps {
  mesh: SceneMesh | null
}

/** A block scene in 3D: drag to turn around it, right drag to move, scroll to come closer. Drawn only when it changes. */
export function SceneViewport({ mesh }: SceneViewportProps): React.JSX.Element {
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

  useEffect(() => stage.current?.show(mesh), [mesh])

  return <div ref={host} className="absolute inset-0 bg-map-background" />
}

/** The renderer, camera and controls of one viewport. */
class Stage {
  private readonly renderer = new WebGLRenderer({ antialias: true, alpha: true })
  private readonly camera = new PerspectiveCamera(FIELD_OF_VIEW_DEGREES, 1, NEAREST_CLIP, 10_000)
  private readonly scene = new Scene()
  private readonly controls: OrbitControls
  private readonly resizes: ResizeObserver
  private shown: Mesh | null = null
  private frameRequested = false

  constructor(private readonly host: HTMLDivElement) {
    this.renderer.setPixelRatio(window.devicePixelRatio)
    this.renderer.setClearAlpha(0)
    host.appendChild(this.renderer.domElement)
    this.controls = new OrbitControls(this.camera, this.renderer.domElement)
    this.controls.enableDamping = true
    this.controls.addEventListener('change', () => this.requestFrame())
    this.resizes = new ResizeObserver(() => this.fitToHost())
    this.resizes.observe(host)
    this.fitToHost()
  }

  show(mesh: SceneMesh | null): void {
    this.removeShown()
    if (mesh === null) {
      this.requestFrame()
      return
    }
    const material = new MeshBasicMaterial({ map: mesh.atlas.texture, vertexColors: true, alphaTest: CUTOUT_ALPHA })
    this.shown = new Mesh(mesh.geometry, material)
    this.scene.add(this.shown)
    this.frame(new Box3().setFromBufferAttribute(mesh.geometry.getAttribute('position') as never))
    this.requestFrame()
  }

  dispose(): void {
    this.removeShown()
    this.resizes.disconnect()
    this.controls.dispose()
    this.renderer.dispose()
    this.renderer.domElement.remove()
  }

  private frame(bounds: Box3): void {
    const sphere = bounds.isEmpty() ? new Sphere(new Vector3(), 1) : bounds.getBoundingSphere(new Sphere())
    const halfVertical = (FIELD_OF_VIEW_DEGREES * Math.PI) / 360
    const halfHorizontal = Math.atan(Math.tan(halfVertical) * this.camera.aspect)
    const distance = (sphere.radius * FRAMING_MARGIN) / Math.sin(Math.min(halfVertical, halfHorizontal))
    this.controls.target.copy(sphere.center)
    this.camera.position.copy(sphere.center).addScaledVector(VIEW_DIRECTION, distance)
    this.camera.far = distance * 10 + sphere.radius * 4
    this.camera.updateProjectionMatrix()
    this.controls.update()
  }

  // The mesh and its atlas belong to whoever built them; only the material is the viewport's.
  private removeShown(): void {
    if (this.shown === null) {
      return
    }
    this.scene.remove(this.shown)
    ;(this.shown.material as MeshBasicMaterial).dispose()
    this.shown = null
  }

  private fitToHost(): void {
    const width = Math.max(1, this.host.clientWidth)
    const height = Math.max(1, this.host.clientHeight)
    this.renderer.setSize(width, height)
    this.camera.aspect = width / height
    this.camera.updateProjectionMatrix()
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

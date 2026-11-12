import { existsSync, readdirSync, readFileSync } from 'node:fs'
import { join } from 'node:path'

const NVIDIA_VENDOR_ID = '0x10de'
const DRM_FOLDER = '/sys/class/drm'
const DEVICE_FOLDER = '/dev/dri'
const RENDER_NODE_PREFIX = 'renderD'
// Where glvnd looks for EGL drivers, NixOS's first.
const EGL_VENDOR_FOLDERS = ['/run/opengl-driver/share/glvnd/egl_vendor.d', '/usr/share/glvnd/egl_vendor.d', '/etc/glvnd/egl_vendor.d']
const NVIDIA_EGL_VENDOR_FILE = '10_nvidia.json'

/**
 * On Linux, Chromium draws with the default EGL driver, which on a laptop with an NVIDIA GPU beside the integrated one
 * is the integrated GPU's: there the 3D world, tens of millions of triangles, runs at a few frames a second where the
 * NVIDIA GPU keeps 60. Where there is an NVIDIA GPU and its EGL driver, this sets the environment that points the GPU
 * process at that driver with PRIME render offload. An environment that already names an EGL driver is left as it is,
 * which is also how to keep the integrated GPU.
 *
 * @return the NVIDIA GPU's render node, on which the window's buffers must be made too, as the NVIDIA driver cannot draw
 *         into buffers of the integrated GPU; null where nothing changed. The GPU process has read the environment by the
 *         time the app's code runs, so the app has to start again, with the render node, for the change to count.
 */
export function preferDiscreteGpu(env: NodeJS.ProcessEnv): string | null {
  if (process.platform !== 'linux' || env.__EGL_VENDOR_LIBRARY_FILENAMES !== undefined) {
    return null
  }
  const renderNode = nvidiaRenderNode()
  const driver = EGL_VENDOR_FOLDERS.map((folder) => join(folder, NVIDIA_EGL_VENDOR_FILE)).find((file) => existsSync(file))
  if (renderNode === null || driver === undefined) {
    return null
  }
  env.__EGL_VENDOR_LIBRARY_FILENAMES = driver
  env.__NV_PRIME_RENDER_OFFLOAD = '1'
  env.__GLX_VENDOR_LIBRARY_NAME = 'nvidia'
  return renderNode
}

function nvidiaRenderNode(): string | null {
  try {
    const node = readdirSync(DRM_FOLDER)
      .filter((name) => name.startsWith(RENDER_NODE_PREFIX))
      .find((name) => readFileSync(join(DRM_FOLDER, name, 'device', 'vendor'), 'utf8').trim() === NVIDIA_VENDOR_ID)
    return node === undefined ? null : join(DEVICE_FOLDER, node)
  } catch {
    return null
  }
}

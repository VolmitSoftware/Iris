import { existsSync, readdirSync, readFileSync } from 'node:fs'
import { join } from 'node:path'

const NVIDIA_VENDOR_ID = '0x10de'
const DRM_FOLDER = '/sys/class/drm'
const RENDER_NODE_PREFIX = 'renderD'
// Where the Vulkan loader looks for drivers, NixOS's first.
const VULKAN_DRIVER_FOLDERS = ['/run/opengl-driver/share/vulkan/icd.d', '/usr/share/vulkan/icd.d', '/etc/vulkan/icd.d']
const NVIDIA_VULKAN_DRIVER_PREFIX = 'nvidia_icd'
const VULKAN_DRIVER_VARIABLES = ['VK_DRIVER_FILES', 'VK_ICD_FILENAMES']

/**
 * Chromium on Wayland hands the desktop buffers made on the GPU it draws with, and a desktop that composites on the
 * integrated GPU cannot show buffers of an NVIDIA GPU: the window stays empty. Through XWayland with Chromium drawing
 * on Vulkan, NVIDIA's Vulkan driver copies each frame to the integrated GPU itself, as it does for games.
 */
export const DEDICATED_GPU_SWITCHES = ['--ozone-platform=x11', '--use-angle=vulkan', '--enable-features=Vulkan,VulkanFromANGLE,DefaultANGLEVulkan']

/** NVIDIA's Vulkan driver file on a Linux machine with an NVIDIA GPU; null where there is no such GPU or driver. */
export function dedicatedGpuDriver(): string | null {
  if (process.platform !== 'linux' || !hasNvidiaGpu()) {
    return null
  }
  for (const folder of VULKAN_DRIVER_FOLDERS) {
    const driver = driverIn(folder)
    if (driver !== null) {
      return driver
    }
  }
  return null
}

/** Shows Chromium only the given Vulkan driver, so it draws on that driver's GPU. */
export function showOnlyVulkanDriver(env: NodeJS.ProcessEnv, driver: string): void {
  for (const variable of VULKAN_DRIVER_VARIABLES) {
    env[variable] = driver
  }
}

export function showEveryVulkanDriver(env: NodeJS.ProcessEnv): void {
  for (const variable of VULKAN_DRIVER_VARIABLES) {
    delete env[variable]
  }
}

export function runsOnDedicatedGpu(argv: string[]): boolean {
  return DEDICATED_GPU_SWITCHES.every((option) => argv.includes(option))
}

/** The arguments with the dedicated GPU's switches in place of any the studio was started with under the same names. */
export function withDedicatedGpuSwitches(argv: string[]): string[] {
  const names = DEDICATED_GPU_SWITCHES.map(switchName)
  return [...argv.filter((option) => !names.includes(switchName(option))), ...DEDICATED_GPU_SWITCHES]
}

export function withoutDedicatedGpuSwitches(argv: string[]): string[] {
  return argv.filter((option) => !DEDICATED_GPU_SWITCHES.includes(option))
}

function switchName(option: string): string {
  return option.split('=')[0]
}

function hasNvidiaGpu(): boolean {
  try {
    return readdirSync(DRM_FOLDER)
      .filter((name) => name.startsWith(RENDER_NODE_PREFIX))
      .some((name) => readFileSync(join(DRM_FOLDER, name, 'device', 'vendor'), 'utf8').trim() === NVIDIA_VENDOR_ID)
  } catch {
    return false
  }
}

function driverIn(folder: string): string | null {
  if (!existsSync(folder)) {
    return null
  }
  const file = readdirSync(folder).find((name) => name.startsWith(NVIDIA_VULKAN_DRIVER_PREFIX))
  return file === undefined ? null : join(folder, file)
}

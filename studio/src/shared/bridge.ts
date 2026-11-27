/** A bridge a running Iris server opened, as its connection file or a studio link describes it. */
export interface BridgeConnection {
  world: string
  port: number
  token: string
  startedMillis?: number
}

/** The keys the user rebound, by shortcut id, each a list of key chords. */
export type StoredShortcuts = Record<string, string[]>

export type ThemeChoice = 'system' | 'light' | 'dark'

/** How large the studio draws its text and controls, as a zoom factor of the window. */
export const UI_SCALES = [0.9, 1, 1.1, 1.25] as const
export type UiScale = (typeof UI_SCALES)[number]
export const DEFAULT_UI_SCALE: UiScale = 1

export function isUiScale(value: unknown): value is UiScale {
  return UI_SCALES.includes(value as UiScale)
}

/** The studio's version and the runtime it is built on. */
export interface AboutInfo {
  version: string
  electron: string
  chromium: string
}

export interface GraphicsInfo {
  /** Whether this machine has an NVIDIA GPU the studio can be asked to draw on. */
  canUseDedicatedGpu: boolean
  usesDedicatedGpu: boolean
}

/** What the preload script hands the renderer. */
export interface StudioHost {
  listBridges(): Promise<BridgeConnection[]>
  /** The studio link the app was started with, if any. */
  initialLink(): Promise<string | null>
  onLink(listener: (link: string) => void): () => void
  setTheme(theme: ThemeChoice): void
  copyText(text: string): void
  /** The rebound shortcuts from the studio settings, as the file holds them, for the caller to check. */
  loadShortcuts(): Promise<unknown>
  saveShortcuts(shortcuts: StoredShortcuts): void
  graphics(): Promise<GraphicsInfo>
  /** Saves the choice and restarts the studio, which only picks its GPU when it starts. */
  useDedicatedGpu(dedicated: boolean): void
  uiScale(): Promise<UiScale>
  /** Saves the scale and applies it to the window at once. */
  setUiScale(scale: UiScale): void
  about(): Promise<AboutInfo>
}

const LINK_PROTOCOL = 'iris-studio:'

/** The bridge a link such as {@code iris-studio://127.0.0.1:44503/?token=...} names; null for anything else. */
export function parseStudioLink(text: string): BridgeConnection | null {
  let url: URL
  try {
    url = new URL(text.trim())
  } catch {
    return null
  }
  const port = Number(url.port)
  const token = url.searchParams.get('token')
  if (url.protocol !== LINK_PROTOCOL || !Number.isInteger(port) || port <= 0 || !token) {
    return null
  }
  return { world: url.searchParams.get('world') ?? '', port, token }
}

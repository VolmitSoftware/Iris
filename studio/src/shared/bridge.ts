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

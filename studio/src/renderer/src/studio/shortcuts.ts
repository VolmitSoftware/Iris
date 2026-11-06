/** The studio's keys, as v3e's studio had them (StudioShortcut). */
export type ShortcutAction =
  | 'nextView'
  | 'refresh'
  | 'zoomIn'
  | 'zoomOut'
  | 'resetZoom'
  | 'dismiss'
  | 'follow'
  | 'grid'
  | 'entities'
  | 'highlight'
  | 'shading'
  | 'borders'
  | 'help'

export interface Shortcut {
  action: ShortcutAction
  label: string
  group: 'Views' | 'Everywhere' | 'Map'
  /** The key as shown, then every KeyboardEvent.key that triggers it. */
  keyText: string
  keys: string[]
}

export const SHORTCUTS: Shortcut[] = [
  { action: 'nextView', label: 'Next view', group: 'Views', keyText: 'M', keys: ['m'] },
  { action: 'refresh', label: 'Redraw the map with the latest pack files', group: 'Everywhere', keyText: 'R', keys: ['r'] },
  { action: 'zoomIn', label: 'Zoom in', group: 'Everywhere', keyText: '=', keys: ['=', '+'] },
  { action: 'zoomOut', label: 'Zoom out', group: 'Everywhere', keyText: '-', keys: ['-'] },
  { action: 'resetZoom', label: 'Reset zoom', group: 'Everywhere', keyText: '\\', keys: ['\\'] },
  { action: 'dismiss', label: 'Clear the highlight', group: 'Everywhere', keyText: 'Esc', keys: ['Escape'] },
  { action: 'follow', label: 'Follow your player', group: 'Map', keyText: 'F', keys: ['f'] },
  { action: 'grid', label: 'Chunk and region grid', group: 'Map', keyText: 'G', keys: ['g'] },
  { action: 'entities', label: 'Show entities', group: 'Map', keyText: 'E', keys: ['e'] },
  { action: 'highlight', label: 'Highlight what is under the cursor', group: 'Map', keyText: 'H', keys: ['h'] },
  { action: 'shading', label: 'Terrain shading', group: 'Map', keyText: 'S', keys: ['s'] },
  { action: 'borders', label: 'Region borders', group: 'Map', keyText: 'B', keys: ['b'] },
  { action: 'help', label: 'Show the shortcuts', group: 'Map', keyText: '/', keys: ['/', '?'] }
]

/** The key of each view; 4 stays free for the 3D world, as in v3e. */
export const VIEW_KEYS: Record<string, string> = {
  biomes: '1',
  regions: '2',
  height: '3',
  landBiomes: '5',
  seaBiomes: '6',
  caveBiomes: '7'
}

export const MOUSE_HINTS: { gesture: string; label: string }[] = [
  { gesture: 'Drag', label: 'Move the map' },
  { gesture: 'Wheel', label: 'Zoom around the cursor' },
  { gesture: 'Shift', label: 'More about the spot under the cursor' },
  { gesture: 'Ctrl+Click', label: 'Teleport there' },
  { gesture: 'Alt+Click', label: 'Open the biome or region file' },
  { gesture: 'Right click', label: 'Highlight, teleport, open or copy' }
]

export function shortcutFor(action: ShortcutAction): Shortcut {
  return SHORTCUTS.find((shortcut) => shortcut.action === action)!
}

export function actionForKey(key: string): ShortcutAction | null {
  const pressed = key.length === 1 ? key.toLowerCase() : key
  return SHORTCUTS.find((shortcut) => shortcut.keys.includes(pressed))?.action ?? null
}

export function viewForKey(key: string): string | null {
  return Object.entries(VIEW_KEYS).find(([, viewKey]) => viewKey === key)?.[0] ?? null
}

/** Whether a key press goes to a field being typed into, so the studio's keys leave it alone. */
export function isTypingInto(target: EventTarget | null): boolean {
  return target instanceof HTMLInputElement || target instanceof HTMLSelectElement || target instanceof HTMLTextAreaElement
}

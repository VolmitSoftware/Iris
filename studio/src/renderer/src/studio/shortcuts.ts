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
  | 'undo'
  | 'redo'
  | 'deleteNode'
  | 'duplicateNode'
  | 'showAllNodes'

export type ShortcutGroup = 'Views' | 'Everywhere' | 'Map' | 'Node editor'

/**
 * A key press as text: the modifiers in the order ctrl, alt, shift, then the key as {@code KeyboardEvent.key} names it,
 * lowercase when it is a letter. Ctrl stands for Cmd too, and shift is only kept for letters and named keys, as a
 * symbol such as ? already says it.
 */
export type Chord = string

/** The keys a user set, by shortcut id; a shortcut missing here keeps its default keys. */
export type Bindings = Record<string, Chord[]>

/** What the settings and help dialogs list: a studio shortcut, or the key that opens one view. */
export interface ShortcutRow {
  id: string
  label: string
  group: ShortcutGroup
}

interface ShortcutDefinition extends ShortcutRow {
  id: ShortcutAction
  chords: Chord[]
}

export const SHORTCUTS: ShortcutDefinition[] = [
  { id: 'nextView', label: 'Next view', group: 'Views', chords: ['m'] },
  { id: 'refresh', label: 'Redraw the map with the latest pack files', group: 'Everywhere', chords: ['r'] },
  { id: 'zoomIn', label: 'Zoom in', group: 'Everywhere', chords: ['=', '+'] },
  { id: 'zoomOut', label: 'Zoom out', group: 'Everywhere', chords: ['-'] },
  { id: 'resetZoom', label: 'Reset zoom', group: 'Everywhere', chords: ['\\'] },
  { id: 'dismiss', label: 'Clear the highlight or the chosen node', group: 'Everywhere', chords: ['Escape'] },
  { id: 'follow', label: 'Follow your player', group: 'Map', chords: ['f'] },
  { id: 'grid', label: 'Chunk and region grid', group: 'Map', chords: ['g'] },
  { id: 'entities', label: 'Show entities', group: 'Map', chords: ['e'] },
  { id: 'highlight', label: 'Highlight what is under the cursor', group: 'Map', chords: ['h'] },
  { id: 'shading', label: 'Terrain shading', group: 'Map', chords: ['s'] },
  { id: 'borders', label: 'Region borders', group: 'Map', chords: ['b'] },
  { id: 'help', label: 'Show the shortcuts', group: 'Map', chords: ['/', '?'] },
  { id: 'undo', label: 'Undo', group: 'Node editor', chords: ['ctrl+z'] },
  { id: 'redo', label: 'Redo', group: 'Node editor', chords: ['ctrl+y', 'ctrl+shift+z'] },
  { id: 'deleteNode', label: 'Delete the chosen node', group: 'Node editor', chords: ['Delete', 'Backspace'] },
  { id: 'duplicateNode', label: 'Duplicate the chosen node', group: 'Node editor', chords: ['ctrl+d'] },
  { id: 'showAllNodes', label: 'Show every node', group: 'Node editor', chords: ['f', 'Home'] }
]

/** The key of each view, as in v3e. */
const VIEW_KEYS: Record<string, Chord> = {
  biomes: '1',
  regions: '2',
  height: '3',
  world3d: '4',
  landBiomes: '5',
  seaBiomes: '6',
  caveBiomes: '7',
  'iris:terrain/palettes': 'p',
  'iris:objects': 'o',
  'iris:objects/procedural': 't',
  noise: 'n',
  savedWorld: 'w'
}

export const MOUSE_HINTS: { gesture: string; label: string }[] = [
  { gesture: 'Drag', label: 'Move the map' },
  { gesture: 'Wheel', label: 'Zoom around the cursor' },
  { gesture: 'Shift', label: 'More about the spot under the cursor' },
  { gesture: 'Ctrl+Click', label: 'Teleport there' },
  { gesture: 'Alt+Click', label: 'Open the biome or region file' },
  { gesture: 'Right click', label: 'Highlight, teleport, open or copy' }
]

/**
 * The window's own keys work anywhere in it, while the node editor takes its keys first while it is shown, so two
 * shortcuts only clash when both are window keys or both are node editor keys.
 */
const GROUP_SCOPES: Record<ShortcutGroup, { windowWide: boolean; nodeEditor: boolean }> = {
  Views: { windowWide: true, nodeEditor: false },
  Everywhere: { windowWide: true, nodeEditor: true },
  Map: { windowWide: true, nodeEditor: false },
  'Node editor': { windowWide: false, nodeEditor: true }
}

const NODE_EDITOR_ACTIONS = ['undo', 'redo', 'deleteNode', 'duplicateNode', 'showAllNodes', 'dismiss'] as const
export type NodeEditorAction = (typeof NODE_EDITOR_ACTIONS)[number]

const VIEW_ID_PREFIX ='view:'
const MODIFIER_PREFIXES = ['ctrl+', 'alt+', 'shift+'] as const
const MODIFIER_KEYS = new Set(['Control', 'Shift', 'Alt', 'Meta', 'AltGraph', 'CapsLock', 'Dead', 'Unidentified'])
const KEY_NAMES: Record<string, string> = { Escape: 'Esc', ' ': 'Space' }
const ARROW_PREFIX = 'Arrow'

export interface KeyPress {
  key: string
  ctrlKey: boolean
  metaKey: boolean
  altKey: boolean
  shiftKey: boolean
}

export function viewShortcutId(viewId: string): string {
  return VIEW_ID_PREFIX + viewId
}

export function viewOfShortcutId(id: string): string | null {
  return id.startsWith(VIEW_ID_PREFIX) ? id.slice(VIEW_ID_PREFIX.length) : null
}

/** Every shortcut to list, the views first as they head the Views group. */
export function shortcutRows(views: { id: string; label: string }[]): ShortcutRow[] {
  return [...views.map((view) => ({ id: viewShortcutId(view.id), label: view.label, group: 'Views' as const })), ...SHORTCUTS]
}

export function defaultChords(id: string): Chord[] {
  const viewId = viewOfShortcutId(id)
  if (viewId !== null) {
    return VIEW_KEYS[viewId] === undefined ? [] : [VIEW_KEYS[viewId]]
  }
  return SHORTCUTS.find((shortcut) => shortcut.id === id)?.chords ?? []
}

export function chordsOf(id: string, bindings: Bindings): Chord[] {
  return bindings[id] ?? defaultChords(id)
}

/** The chord of a key press; null for a modifier alone, which names no key yet. */
export function chordOf(press: KeyPress): Chord | null {
  if (MODIFIER_KEYS.has(press.key) || press.key === '') {
    return null
  }
  const key = press.key.length === 1 ? press.key.toLowerCase() : press.key
  const keepsShift = press.shiftKey && (key.length > 1 || key !== key.toUpperCase())
  const modifiers = [press.ctrlKey || press.metaKey ? 'ctrl+' : '', press.altKey ? 'alt+' : '', keepsShift ? 'shift+' : '']
  return modifiers.join('') + key
}

/** Whether a text is a chord in the form {@link chordOf} makes. */
export function isChord(text: unknown): text is Chord {
  if (typeof text !== 'string') {
    return false
  }
  const parsed = splitChord(text)
  if (parsed === null) {
    return false
  }
  return chordOf({ key: parsed.key, ctrlKey: parsed.modifiers.includes('ctrl+'), metaKey: false, altKey: parsed.modifiers.includes('alt+'), shiftKey: parsed.modifiers.includes('shift+') }) === text
}

/** A chord as people write it, like Ctrl+Shift+Z or Esc. */
export function chordText(chord: Chord): string {
  const parsed = splitChord(chord)
  if (parsed === null) {
    return chord
  }
  const modifiers = parsed.modifiers.map((prefix) => prefix[0].toUpperCase() + prefix.slice(1))
  return modifiers.join('') + keyName(parsed.key)
}

export function chordsText(chords: Chord[]): string {
  return chords.map(chordText).join(', ')
}

/** The first key of a shortcut as shown beside it, empty when it has none. */
export function keyTextOf(id: string, bindings: Bindings): string {
  const chords = chordsOf(id, bindings)
  return chords.length === 0 ? '' : chordText(chords[0])
}

/** Whether the key press is one of the keys of the shortcut. */
export function matches(press: KeyPress, id: string, bindings: Bindings): boolean {
  const chord = chordOf(press)
  return chord !== null && chordsOf(id, bindings).includes(chord)
}

/** The window shortcut a key press triggers, if any. */
export function windowShortcutFor(press: KeyPress, rows: ShortcutRow[], bindings: Bindings): string | null {
  const chord = chordOf(press)
  if (chord === null) {
    return null
  }
  return rows.filter((row) => GROUP_SCOPES[row.group].windowWide).find((row) => chordsOf(row.id, bindings).includes(chord))?.id ?? null
}

/** The node editor shortcut a key press triggers, if any. */
export function nodeEditorActionFor(press: KeyPress, bindings: Bindings): NodeEditorAction | null {
  return NODE_EDITOR_ACTIONS.find((action) => matches(press, action, bindings)) ?? null
}

/** The other shortcuts the chord already triggers where {@code row} works too. */
export function clashesWith(row: ShortcutRow, chord: Chord, rows: ShortcutRow[], bindings: Bindings): ShortcutRow[] {
  return rows.filter((other) => other.id !== row.id && sharesKeys(row.group, other.group) && chordsOf(other.id, bindings).includes(chord))
}

/** The bindings a settings file holds, without what is not a list of chords. */
export function sanitizeBindings(stored: unknown): Bindings {
  if (typeof stored !== 'object' || stored === null || Array.isArray(stored)) {
    return {}
  }
  const bindings: Bindings = {}
  for (const [id, chords] of Object.entries(stored)) {
    if (Array.isArray(chords) && chords.every(isChord)) {
      bindings[id] = [...new Set<Chord>(chords)]
    }
  }
  return bindings
}

/** Whether a key press goes to a field being typed into, so the studio's keys leave it alone. */
export function isTypingInto(target: EventTarget | null): boolean {
  return target instanceof HTMLInputElement || target instanceof HTMLSelectElement || target instanceof HTMLTextAreaElement
}

/** Whether a key press goes to the list of a view's entries, where Delete is for the entry and not the chosen node. */
export function isInEntryBrowser(target: EventTarget | null): boolean {
  return target instanceof Element && target.closest('[data-entry-browser]') !== null
}

function sharesKeys(first: ShortcutGroup, second: ShortcutGroup): boolean {
  const a = GROUP_SCOPES[first]
  const b = GROUP_SCOPES[second]
  return (a.windowWide && b.windowWide) || (a.nodeEditor && b.nodeEditor)
}

function splitChord(chord: Chord): { modifiers: string[]; key: string } | null {
  let key = chord
  const modifiers: string[] = []
  for (const prefix of MODIFIER_PREFIXES) {
    if (key.startsWith(prefix) && key.length > prefix.length) {
      modifiers.push(prefix)
      key = key.slice(prefix.length)
    }
  }
  return key === '' ? null : { modifiers, key }
}

function keyName(key: string): string {
  if (KEY_NAMES[key] !== undefined) {
    return KEY_NAMES[key]
  }
  if (key.length === 1) {
    return key.toUpperCase()
  }
  return key.startsWith(ARROW_PREFIX) ? key.slice(ARROW_PREFIX.length) : key
}

import { describe, expect, it } from 'vitest'
import {
  SHORTCUTS,
  chordOf,
  chordText,
  clashesWith,
  defaultChords,
  isChord,
  keyTextOf,
  matches,
  nodeEditorActionFor,
  sanitizeBindings,
  shortcutRows,
  viewShortcutId,
  windowShortcutFor,
  type KeyPress
} from './shortcuts'

const DEFAULT_VIEWS = ['biomes', 'regions', 'height', 'landBiomes', 'seaBiomes', 'caveBiomes'].map((id) => ({ id, label: id }))
const ROWS = shortcutRows(DEFAULT_VIEWS)

function press(key: string, modifiers: Partial<Omit<KeyPress, 'key'>> = {}): KeyPress {
  return { key, ctrlKey: false, metaKey: false, altKey: false, shiftKey: false, ...modifiers }
}

describe('shortcuts', () => {
  it('never gives two shortcuts that work together the same default key', () => {
    for (const row of ROWS) {
      for (const chord of defaultChords(row.id)) {
        expect(clashesWith(row, chord, ROWS, {}).map((clash) => clash.id), `${row.id} ${chord}`).toEqual([])
      }
    }
  })

  it('writes every default key as a chord that reads back as itself', () => {
    for (const row of ROWS) {
      for (const chord of defaultChords(row.id)) {
        expect(isChord(chord), `${row.id} ${chord}`).toBe(true)
      }
    }
  })

  it('makes a chord of a key press, folding Cmd into Ctrl and keeping shift for letters only', () => {
    expect(chordOf(press('Z', { ctrlKey: true, shiftKey: true }))).toBe('ctrl+shift+z')
    expect(chordOf(press('z', { metaKey: true }))).toBe('ctrl+z')
    expect(chordOf(press('M'))).toBe('m')
    expect(chordOf(press('?', { shiftKey: true }))).toBe('?')
    expect(chordOf(press('ArrowLeft', { shiftKey: true, altKey: true }))).toBe('alt+shift+ArrowLeft')
    expect(chordOf(press('Shift', { shiftKey: true }))).toBeNull()
  })

  it('shows chords as people write them', () => {
    expect(chordText('ctrl+shift+z')).toBe('Ctrl+Shift+Z')
    expect(chordText('Escape')).toBe('Esc')
    expect(chordText('=')).toBe('=')
    expect(chordText('ctrl++')).toBe('Ctrl++')
    expect(chordText('ArrowUp')).toBe('Up')
    expect(chordText(' ')).toBe('Space')
  })

  it('finds the window shortcut a key press triggers, by default and once rebound', () => {
    expect(windowShortcutFor(press('g'), ROWS, {})).toBe('grid')
    expect(windowShortcutFor(press('1'), ROWS, {})).toBe(viewShortcutId('biomes'))
    expect(windowShortcutFor(press('z', { ctrlKey: true }), ROWS, {})).toBeNull()
    expect(windowShortcutFor(press('g'), ROWS, { grid: ['x'] })).toBeNull()
    expect(windowShortcutFor(press('x'), ROWS, { grid: ['x'] })).toBe('grid')
    expect(windowShortcutFor(press('4'), ROWS, { [viewShortcutId('biomes')]: ['4'] })).toBe(viewShortcutId('biomes'))
  })

  it('leaves a shortcut with no keys unreachable', () => {
    expect(windowShortcutFor(press('g'), ROWS, { grid: [] })).toBeNull()
    expect(keyTextOf('grid', { grid: [] })).toBe('')
  })

  it('shows the first key of a shortcut', () => {
    expect(keyTextOf('redo', {})).toBe('Ctrl+Y')
    expect(keyTextOf('redo', { redo: ['ctrl+shift+z'] })).toBe('Ctrl+Shift+Z')
    expect(keyTextOf(viewShortcutId('noise'), {})).toBe('N')
    expect(keyTextOf(viewShortcutId('some:module/view'), {})).toBe('')
  })

  it('matches the node editor keys by the live bindings', () => {
    expect(matches(press('z', { ctrlKey: true }), 'undo', {})).toBe(true)
    expect(matches(press('z', { ctrlKey: true, shiftKey: true }), 'redo', {})).toBe(true)
    expect(matches(press('z', { ctrlKey: true }), 'undo', { undo: ['ctrl+u'] })).toBe(false)
    expect(matches(press('Control', { ctrlKey: true }), 'undo', {})).toBe(false)
  })

  it('finds the node editor action of a key press, by default and once rebound', () => {
    expect(nodeEditorActionFor(press('d', { ctrlKey: true }), {})).toBe('duplicateNode')
    expect(nodeEditorActionFor(press('F'), {})).toBe('showAllNodes')
    expect(nodeEditorActionFor(press('Home'), {})).toBe('showAllNodes')
    expect(nodeEditorActionFor(press('Escape'), {})).toBe('dismiss')
    expect(nodeEditorActionFor(press('Delete'), {})).toBe('deleteNode')
    expect(nodeEditorActionFor(press('Backspace'), {})).toBe('deleteNode')
    expect(nodeEditorActionFor(press('d'), {})).toBeNull()
    expect(nodeEditorActionFor(press('f'), { showAllNodes: ['a'] })).toBeNull()
    expect(nodeEditorActionFor(press('a'), { showAllNodes: ['a'] })).toBe('showAllNodes')
  })

  it('keeps the node editor keys out of the map shortcuts, which share F', () => {
    expect(windowShortcutFor(press('f'), ROWS, {})).toBe('follow')
    expect(ROWS.find((row) => row.id === 'showAllNodes')!.group).toBe('Node editor')
    expect(clashesWith(ROWS.find((row) => row.id === 'showAllNodes')!, 'f', ROWS, {})).toEqual([])
  })

  it('reports the shortcuts a new key would clash with, only where both work together', () => {
    const grid = ROWS.find((row) => row.id === 'grid')!
    const undo = ROWS.find((row) => row.id === 'undo')!
    expect(clashesWith(grid, 'h', ROWS, {}).map((clash) => clash.id)).toEqual(['highlight'])
    expect(clashesWith(grid, 'ctrl+z', ROWS, {})).toEqual([])
    expect(clashesWith(undo, 'Escape', ROWS, {}).map((clash) => clash.id)).toEqual(['dismiss'])
    expect(clashesWith(undo, 'g', ROWS, {})).toEqual([])
  })

  it('keeps only lists of chords from a settings file', () => {
    expect(sanitizeBindings({ grid: ['x', 'ctrl+x'], zoomIn: 'x', help: ['ctrl+Z'], undo: [], redo: ['a', 'a'] })).toEqual({
      grid: ['x', 'ctrl+x'],
      undo: [],
      redo: ['a']
    })
    expect(sanitizeBindings(null)).toEqual({})
    expect(sanitizeBindings(['grid'])).toEqual({})
  })

  it('gives every shortcut a label and a default key', () => {
    expect(SHORTCUTS.every((shortcut) => shortcut.label !== '' && shortcut.chords.length > 0)).toBe(true)
  })
})

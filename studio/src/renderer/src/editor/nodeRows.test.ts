import { describe, expect, it } from 'vitest'
import type { GraphNode, SettingOption, SettingsType, ValueKind } from '../bridge/types'
import { NODE_HEADER, NODE_HEADING, NODE_PADDING, NODE_ROW, NODE_STRIPE, nodeRows } from './nodeRows'

function option(name: string, kind: ValueKind): SettingOption {
  return { name, description: '', required: false, minimum: null, maximum: null, minItems: null, defaultValue: null, kind }
}

const any: ValueKind = { kind: 'any' }
const types: Record<string, SettingsType> = {
  Part: { description: '', options: [option('role', { kind: 'text', format: 'PLAIN' }), option('offset', { kind: 'object', type: 'Offset' })] },
  Stem: { description: '', options: [option('length', any), option('taper', any)] },
  Offset: { description: '', options: [option('x', any), option('y', any)] }
}

const stem: GraphNode = {
  kind: 'Stem',
  title: 'Stem',
  path: ['parts', 0],
  partId: 0,
  roles: ['wood'],
  settings: [
    { title: 'Part', path: ['parts', 0], type: 'Part', shownAsNodes: [] },
    { title: 'Stem', path: ['parts', 0, 'stem'], type: 'Stem', shownAsNodes: [] }
  ],
  children: [],
  childList: null,
  movable: true,
  duplicable: true,
  additions: []
}

const file = { parts: [{ role: 'wood', offset: { x: 2 }, stem: { length: 6 } }] }

describe('nodeRows', () => {
  it('lists the first section plainly, a later one under its heading, and a folded section as one row', () => {
    const { rows } = nodeRows(stem, types, file, new Set(), true)

    expect(rows.map((row) => row.kind)).toEqual(['setting', 'fold', 'heading', 'setting', 'addSetting'])
    expect(rows.flatMap((row) => (row.kind === 'setting' ? [row.option.name, row.value] : []))).toEqual(['role', 'wood', 'length', 6])
  })

  it('lists what an unfolded section holds right under it', () => {
    const { rows } = nodeRows(stem, types, file, new Set([JSON.stringify(['parts', 0, 'offset'])]), true)

    expect(rows.map((row) => (row.kind === 'setting' ? row.option.name : row.kind))).toEqual(['role', 'fold', 'x', 'heading', 'length', 'addSetting'])
  })

  it('adds up its height from its rows', () => {
    const { height } = nodeRows(stem, types, file, new Set(), true)

    expect(height).toBe(NODE_STRIPE + NODE_HEADER + 4 * NODE_ROW + NODE_HEADING + NODE_PADDING)
  })

  it('offers the sections with settings left to add, but no row to add them while the file is read only', () => {
    const editable = nodeRows(stem, types, file, new Set(), true)
    const readOnly = nodeRows(stem, types, file, new Set(), false)

    expect(editable.addable.map((fold) => fold.title)).toEqual(['offset', 'Stem'])
    expect(readOnly.rows.some((row) => row.kind === 'addSetting')).toBe(false)
  })

  it('draws a node without settings, such as a shared part, as its header alone', () => {
    const { rows, height } = nodeRows({ ...stem, settings: [] }, types, file, new Set(), true)

    expect(rows).toEqual([])
    expect(height).toBe(NODE_STRIPE + NODE_HEADER + NODE_PADDING)
  })
})

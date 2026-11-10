import { describe, expect, it } from 'vitest'
import type { GraphNode, SettingOption } from '../bridge/types'
import { NO_PART } from '../bridge/types'
import { completed, matching, rolesOf, suggestionSourceOf, wordBeingTyped } from './suggestions'

function option(name: string, kind: SettingOption['kind']): SettingOption {
  return { name, description: '', required: false, minimum: null, maximum: null, minItems: null, defaultValue: null, kind }
}

function node(roles: string[], children: GraphNode[] = []): GraphNode {
  return {
    kind: 'Stem',
    title: 'Stem',
    path: [],
    partId: NO_PART,
    roles,
    settings: [],
    children: children.map((child) => ({ wire: '', node: child })),
    childList: null,
    movable: false,
    duplicable: false,
    additions: []
  }
}

describe('suggestionSourceOf', () => {
  it('names the registry of a reference, the kind of a resource and the roles of the object', () => {
    expect(suggestionSourceOf('Material', option('block', { kind: 'reference', reference: 'BLOCK', format: 'BLOCK_ID' }))).toEqual({ from: 'reference', reference: 'BLOCK' })
    expect(suggestionSourceOf('Part', option('skin', { kind: 'resource', resource: 'iris:object' }))).toEqual({ from: 'resource', resource: 'iris:object' })
  })

  it('takes the roles for the settings that name one', () => {
    const on = option('on', { kind: 'list', element: { kind: 'text', format: 'PLAIN' }, unique: false })

    expect(suggestionSourceOf('art.arcane.iris.engine.procedural.part.ProceduralDecoration', on)).toEqual({ from: 'roles' })
    expect(suggestionSourceOf('art.arcane.iris.engine.procedural.part.ProceduralDecoration', option('at', { kind: 'text', format: 'PLAIN' }))).toBeNull()
  })

  it('takes the elements of a list of references, and nothing for free text', () => {
    expect(suggestionSourceOf('Rule', option('blocks', { kind: 'list', element: { kind: 'reference', reference: 'BLOCK', format: 'BLOCK_ID' }, unique: false }))).toEqual({ from: 'reference', reference: 'BLOCK' })
    expect(suggestionSourceOf('Rule', option('note', { kind: 'text', format: 'PLAIN' }))).toBeNull()
  })
})

describe('rolesOf', () => {
  it('lists each role of the whole tree once, sorted', () => {
    expect(rolesOf([node(['wood', 'leaves'], [node(['wood', 'bark'])]), node(['moss'])])).toEqual(['bark', 'leaves', 'moss', 'wood'])
  })
})

describe('matching', () => {
  const blocks = ['minecraft:oak_log', 'minecraft:birch_log', 'minecraft:oak_leaves', 'minecraft:stone']

  it('puts what starts with the typed text before what holds it', () => {
    expect(matching(blocks, 'minecraft:oak')).toEqual(['minecraft:oak_log', 'minecraft:oak_leaves'])
    expect(matching(blocks, 'oak')).toEqual(['minecraft:oak_log', 'minecraft:oak_leaves'])
    expect(matching(['a_log', 'log'], 'log')).toEqual([])
    expect(matching(['a_log', 'logs'], 'log')).toEqual(['logs', 'a_log'])
  })

  it('suggests nothing for nothing typed or a choice that is already typed out', () => {
    expect(matching(blocks, '')).toEqual([])
    expect(matching(blocks, 'MINECRAFT:STONE')).toEqual([])
  })

  it('shows only the first few', () => {
    expect(matching(Array.from({ length: 30 }, (_, index) => `block_${index}`), 'block')).toHaveLength(8)
  })
})

describe('the word being typed', () => {
  it('is the whole text, or for a list the item after the last comma', () => {
    expect(wordBeingTyped('oak', false)).toEqual({ start: 0, typed: 'oak' })
    expect(wordBeingTyped('wood, lea', true)).toEqual({ start: 6, typed: 'lea' })
    expect(wordBeingTyped('wood, ', true)).toEqual({ start: 6, typed: '' })
  })

  it('is replaced by the choice, a list ready for its next item', () => {
    expect(completed('minecraft:oa', 'minecraft:oak_log', false)).toBe('minecraft:oak_log')
    expect(completed('wood, lea', 'leaves', true)).toBe('wood, leaves, ')
  })
})

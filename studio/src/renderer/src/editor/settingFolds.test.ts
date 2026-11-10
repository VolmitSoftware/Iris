import { describe, expect, it } from 'vitest'
import type { NodeSettings, SettingOption, SettingsType, ValueKind } from '../bridge/types'
import { foldedSections, idOfFold, visibleFolds } from './settingFolds'

function option(name: string, kind: ValueKind): SettingOption {
  return { name, description: '', required: false, minimum: null, maximum: null, minItems: null, defaultValue: null, kind }
}

const text: ValueKind = { kind: 'text', format: 'PLAIN' }
const any: ValueKind = { kind: 'any' }

const types: Record<string, SettingsType> = {
  Material: {
    description: '',
    options: [
      option('role', text),
      option('block', { kind: 'reference', reference: 'BLOCK', format: 'BLOCK_ID' }),
      option('offset', { kind: 'object', type: 'Offset' }),
      option('rules', { kind: 'list', element: { kind: 'object', type: 'Rule' }, unique: false }),
      option('parts', { kind: 'list', element: { kind: 'object', type: 'Rule' }, unique: false })
    ]
  },
  Offset: { description: '', options: [option('x', any), option('y', any)] },
  Rule: { description: '', options: [option('chance', any), option('face', text)] }
}

const material: NodeSettings = { title: 'Material', path: ['materials', 0], type: 'Material', shownAsNodes: ['parts'] }
const file = {
  materials: [
    {
      role: 'wood',
      block: 'minecraft:birch_wood',
      offset: { x: 2, y: [[0, 1], [1, 0]] },
      rules: [{ chance: 0.5 }, { face: 'top' }]
    }
  ]
}

describe('foldedSections', () => {
  it('folds an object setting into a section, and a list of objects into a section of items', () => {
    const folds = foldedSections(material, types, file)

    expect(folds.map((fold) => fold.title)).toEqual(['Material', 'offset', 'rules', '1', '2'])
    expect(folds.map((fold) => fold.kind)).toEqual(['own', 'group', 'list', 'item', 'item'])
    expect(folds.map((fold) => fold.depth)).toEqual([0, 1, 1, 2, 2])
    expect(folds[4].path).toEqual(['materials', 0, 'rules', 1])
    expect(folds[2].holder).toEqual({ object: ['materials', 0], key: 'rules' })
  })

  it('leaves the folded settings and the ones shown as nodes out of the fields of the section that holds them', () => {
    const [own] = foldedSections(material, types, file)

    expect(own.options.map((setting) => setting.name)).toEqual(['role', 'block'])
  })

  it('sums a section up by what it holds, a curve as curve and the default namespace left off', () => {
    const folds = foldedSections(material, types, file)

    expect(folds[0].summary).toBe('role wood · block birch_wood · rules 2')
    expect(folds[1].summary).toBe('x 2 · y curve')
    expect(folds[2].summary).toBe('2 items')
  })

  it('keeps a setting that is not written as an object a field', () => {
    const [own, ...inside] = foldedSections(material, types, { materials: [{ role: 'wood' }] })

    expect(inside).toEqual([])
    expect(own.options.map((setting) => setting.name)).toEqual(['role', 'block', 'offset', 'rules'])
  })
})

describe('visibleFolds', () => {
  it('hides what is inside a fold that is folded', () => {
    const folds = foldedSections(material, types, file)

    expect(visibleFolds(folds, new Set()).map((fold) => fold.title)).toEqual(['Material', 'offset', 'rules'])
    expect(visibleFolds(folds, new Set([idOfFold(folds[2])])).map((fold) => fold.title)).toEqual(['Material', 'offset', 'rules', '1', '2'])
  })
})

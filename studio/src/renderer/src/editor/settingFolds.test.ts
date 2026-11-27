import { describe, expect, it } from 'vitest'
import type { NodeSettings, SettingOption, SettingsType, ValueKind } from '../bridge/types'
import { foldedSections, idOfFold, visibleFolds } from './settingFolds'
import { SPAN_TYPE } from './settingText'

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

  it('leaves the folded settings and the ones shown as nodes out of the rows of the section that holds them', () => {
    const [own] = foldedSections(material, types, file)

    expect(own.settings.map((setting) => setting.name)).toEqual(['role', 'block'])
  })

  it('lists only what is written, and keeps the rest to add', () => {
    const [own] = foldedSections(material, types, { materials: [{ block: 'minecraft:stone' }] })

    expect(own.settings.map((setting) => setting.name)).toEqual(['block'])
    expect(own.unset.map((setting) => setting.name)).toEqual(['role', 'offset', 'rules'])
  })

  it('lists a plain setting the type requires even while it is unset, so its default shows', () => {
    const required: Record<string, SettingsType> = { ...types, Material: { ...types.Material, options: [{ ...option('role', text), required: true }] } }
    const [own] = foldedSections(material, required, { materials: [{}] })

    expect(own.settings.map((setting) => setting.name)).toEqual(['role'])
    expect(own.unset).toEqual([])
  })

  it('keeps a span a row rather than a section, since it is typed as a range', () => {
    const spanned: Record<string, SettingsType> = { ...types, Material: { description: '', options: [option('along', { kind: 'object', type: SPAN_TYPE })] }, [SPAN_TYPE]: { description: '', options: [option('from', any), option('to', any)] } }
    const folds = foldedSections(material, spanned, { materials: [{ along: { from: 0.2, to: 0.8 } }] })

    expect(folds).toHaveLength(1)
    expect(folds[0].settings.map((setting) => setting.name)).toEqual(['along'])
    expect(folds[0].summary).toBe('along 0.2 – 0.8')
  })

  it('sums a section up by what it holds, a curve as curve and the default namespace left off', () => {
    const folds = foldedSections(material, types, file)

    expect(folds[0].summary).toBe('role wood · block birch_wood · rules 2')
    expect(folds[1].summary).toBe('x 2 · y curve')
    expect(folds[2].summary).toBe('2 items')
  })

  it('keeps an object setting that is not written as an object a row', () => {
    const [own, ...inside] = foldedSections(material, types, { materials: [{ offset: 3 }] })

    expect(inside).toEqual([])
    expect(own.settings.map((setting) => setting.name)).toEqual(['offset'])
  })
})

describe('visibleFolds', () => {
  it('hides what is inside a fold that is folded', () => {
    const folds = foldedSections(material, types, file)

    expect(visibleFolds(folds, new Set()).map((fold) => fold.title)).toEqual(['Material', 'offset', 'rules'])
    expect(visibleFolds(folds, new Set([idOfFold(folds[2])])).map((fold) => fold.title)).toEqual(['Material', 'offset', 'rules', '1', '2'])
  })
})

import { describe, expect, it } from 'vitest'
import type { ValueKind } from '../bridge/types'
import { literalOf, textOf } from './settingText'

const roles: ValueKind = { kind: 'list', element: { kind: 'text', format: 'PLAIN' }, unique: false }

describe('string lists', () => {
  it('read as commas and are written back quoted', () => {
    expect(textOf(roles, ['wood', 'leaves'])).toBe('wood, leaves')
    expect(literalOf(roles, 'wood,  leaves, ')).toBe('["wood","leaves"]')
    expect(literalOf(roles, ',')).toBe('[]')
  })

  it('keep a list typed as JSON, and show a list of anything else as JSON', () => {
    expect(literalOf(roles, '["a", "b"]')).toBe('["a", "b"]')
    expect(textOf(roles, [1, 2])).toBe('[1,2]')
  })
})

describe('text', () => {
  it('shows a string as it is and writes it quoted', () => {
    const text: ValueKind = { kind: 'text', format: 'PLAIN' }

    expect(textOf(text, 'wood')).toBe('wood')
    expect(literalOf(text, 'wood "a"')).toBe('"wood \\"a\\""')
  })
})

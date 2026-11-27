import { describe, expect, it } from 'vitest'
import type { SettingOption, ValueKind } from '../bridge/types'
import { defaultLiteral, defaultText, literalOf, problemWith, SPAN_TYPE, textOf } from './settingText'

const roles: ValueKind = { kind: 'list', element: { kind: 'text', format: 'PLAIN' }, unique: false }
const any: ValueKind = { kind: 'any' }
const span: ValueKind = { kind: 'object', type: SPAN_TYPE }

function option(kind: ValueKind, changes: Partial<SettingOption> = {}): SettingOption {
  return { name: 'radius', description: '', required: false, minimum: null, maximum: null, minItems: null, defaultValue: null, kind, ...changes }
}

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

describe('ranges', () => {
  it('show a [min, max] value and a span with a dash', () => {
    expect(textOf(any, [5, 7])).toBe('5 – 7')
    expect(textOf(span, { from: 0.45, to: 0.95 })).toBe('0.45 – 0.95')
    expect(textOf(span, { to: 0.5 })).toBe('0 – 0.5')
  })

  it('are read back however the dash is typed', () => {
    expect(literalOf(any, '2 – 4')).toBe('[2, 4]')
    expect(literalOf(any, '2-4')).toBe('[2, 4]')
    expect(literalOf(any, '2 to 4')).toBe('[2, 4]')
    expect(literalOf(any, '-1, 3.5')).toBe('[-1, 3.5]')
    expect(literalOf(span, '0.2 – 0.8')).toBe('{"from": 0.2, "to": 0.8}')
  })

  it('leave a plain number and a curve as typed', () => {
    expect(literalOf(any, ' 3 ')).toBe('3')
    expect(literalOf(any, '[[0, 1], [1, 0]]')).toBe('[[0, 1], [1, 0]]')
  })
})

describe('defaults', () => {
  it('start an added setting at its default, or at an empty value of its kind', () => {
    expect(defaultLiteral(option(any, { defaultValue: 2 }))).toBe('2')
    expect(defaultLiteral(option({ kind: 'boolean' }))).toBe('false')
    expect(defaultLiteral(option({ kind: 'enum', constants: [{ name: 'ROUND', description: '' }] }))).toBe('"ROUND"')
    expect(defaultLiteral(option(roles))).toBe('[]')
    expect(defaultLiteral(option({ kind: 'object', type: 'Offset' }))).toBe('{}')
  })

  it('show what an unset setting falls back to, or nothing', () => {
    expect(defaultText(option({ kind: 'text', format: 'PLAIN' }, { defaultValue: 'main' }))).toBe('main')
    expect(defaultText(option(any))).toBe('')
  })
})

describe('problemWith', () => {
  it('holds a number to its kind and limits', () => {
    const count = option({ kind: 'integer' }, { minimum: 1, maximum: 64 })

    expect(problemWith(count, 'a')).toBe('Not a number')
    expect(problemWith(count, '2.5')).toBe('Not a whole number')
    expect(problemWith(count, '0')).toBe('At least 1')
    expect(problemWith(count, '65')).toBe('At most 64')
    expect(problemWith(count, '6')).toBeNull()
  })

  it('takes a number, a range or JSON for a value that may be a curve', () => {
    expect(problemWith(option(any), '4 – 6')).toBeNull()
    expect(problemWith(option(any), '[[0, 1]]')).toBeNull()
    expect(problemWith(option(any), 'tall')).toBe('Takes a number, or a range like 4 – 6')
  })

  it('lets an empty draft through, since it unsets the setting', () => {
    expect(problemWith(option({ kind: 'integer' }), ' ')).toBeNull()
  })
})

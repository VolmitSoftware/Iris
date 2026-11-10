import { describe, expect, it } from 'vitest'
import { folderPrefixOf, refusalOfNewKey } from './entryKeys'

describe('refusalOfNewKey', () => {
  it('accepts a free, plain key', () => {
    expect(refusalOfNewKey('trees/oak-2', ['trees/oak'])).toBeNull()
  })

  it('refuses a key that is taken', () => {
    expect(refusalOfNewKey('trees/oak', ['trees/oak'])).toBe('trees/oak already exists')
  })

  it('refuses a blank name', () => {
    expect(refusalOfNewKey(' ', [])).toBe('Give it a name')
  })

  it('refuses keys that could leave the folder or are not plain', () => {
    for (const key of ['Trees/Oak', 'trees//oak', '/oak', 'oak/', '../oak', 'trees/../oak', 'oak.json', 'trees\\oak']) {
      expect(refusalOfNewKey(key, []), key).toMatch(/^Use lower case/)
    }
  })
})

describe('folderPrefixOf', () => {
  it('keeps the folders of a key with their closing slash', () => {
    expect(folderPrefixOf('trees/oak/tall')).toBe('trees/oak/')
  })

  it('is empty for a key in no folder', () => {
    expect(folderPrefixOf('oak')).toBe('')
  })
})

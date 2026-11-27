import { describe, expect, it } from 'vitest'
import { badgeColour, badgeInitials } from './packBadges'

describe('badgeInitials', () => {
  it('takes the first letter of a one word name', () => {
    expect(badgeInitials('Etopia')).toBe('E')
  })

  it('takes the first letters of the first two words, split at separators, capitals and digits', () => {
    expect(badgeInitials('voxy-modular')).toBe('VM')
    expect(badgeInitials('IrisV5')).toBe('IV')
    expect(badgeInitials('CodexProvider')).toBe('CP')
    expect(badgeInitials('the overworld pack')).toBe('TO')
  })

  it('marks a name without letters or digits', () => {
    expect(badgeInitials('--')).toBe('?')
  })
})

describe('badgeColour', () => {
  it('gives a pack the same colour every time', () => {
    expect(badgeColour('natureplanet')).toBe(badgeColour('natureplanet'))
  })

  it('spreads packs over the colours', () => {
    const colours = new Set(['natureplanet', 'overworld', 'cavetest', 'riverworld', 'underworld', 'etopia'].map(badgeColour))
    expect(colours.size).toBeGreaterThan(2)
  })
})

/** Badge colours as IntelliJ gives its projects: each pack keeps one, picked by its folder. */
const BADGE_COLOURS = ['#3f9b5e', '#e07a50', '#1fa0b4', '#c0479c', '#9a55d8', '#5f6ce0', '#4a8fdc', '#7ea64a', '#c89a22'] as const
const MOST_INITIALS = 2
const WORD_BREAKS = /[^\p{L}\p{N}]+|(?<=\p{Ll})(?=\p{Lu})|(?<=\p{L})(?=\p{N})/u

/** The first letters of a pack name's words, at most two: "IrisV5" is "IV", "voxy-modular" "VM", "Etopia" "E". */
export function badgeInitials(name: string): string {
  const words = name.split(WORD_BREAKS).filter((word) => word !== '')
  const initials = words.slice(0, MOST_INITIALS).map((word) => word.charAt(0).toUpperCase()).join('')
  return initials === '' ? '?' : initials
}

/** The colour of a pack's badge, the same every time for the same folder. */
export function badgeColour(packKey: string): string {
  let hash = 0
  for (const character of packKey) {
    hash = (hash * 31 + character.charCodeAt(0)) | 0
  }
  return BADGE_COLOURS[Math.abs(hash) % BADGE_COLOURS.length]
}

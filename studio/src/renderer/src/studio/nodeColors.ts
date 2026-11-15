/** The colours of the node editor: one per kind of node, and one per role so its nodes are found at a glance. */
const PRIMARY = 'var(--primary)'
const BY_KIND: Record<string, string> = {
  Object: PRIMARY,
  Stem: '#c08552',
  Blob: '#5fb865',
  Branches: '#d9a23f',
  Cluster: '#9c7bd6',
  Group: '#8c9099',
  'Shared part': '#548af7',
  Material: '#d8709a',
  Decoration: '#4fb6c6'
}
const ROLE_COLORS = ['#c08552', '#5fb865', '#548af7', '#d9a23f', '#9c7bd6', '#4fb6c6', '#d8709a', '#a3b84f']
const KNOWN_ROLES: Record<string, string> = { wood: ROLE_COLORS[0], leaves: ROLE_COLORS[1] }
const FALLBACK_KIND_COLOR = BY_KIND.Group

export function colorOfKind(kind: string): string {
  return BY_KIND[kind] ?? FALLBACK_KIND_COLOR
}

/** The same role always gets the same colour, wherever it is used. */
export function colorOfRole(role: string): string {
  return KNOWN_ROLES[role] ?? ROLE_COLORS[hashOf(role) % ROLE_COLORS.length]
}

function hashOf(text: string): number {
  let hash = 0
  for (let index = 0; index < text.length; index++) {
    hash = (Math.imul(hash, 31) + text.charCodeAt(index)) >>> 0
  }
  return hash
}

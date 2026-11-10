import type { GraphNode, SettingOption, ValueKind } from '../bridge/types'
import { LIST_SEPARATOR } from './settingText'

/** What an option may be typed as: the entries of a platform registry, the keys of the pack's resources of a kind, or the roles of the object. */
export type SuggestionSource = { from: 'reference'; reference: string } | { from: 'resource'; resource: string } | { from: 'roles' }

/** Settings that name a role of the object being edited, by the schema type and option (ProceduralPart, ProceduralMaterial and ProceduralDecoration). */
const ROLE_OPTIONS: ReadonlySet<string> = new Set([
  'art.arcane.iris.engine.procedural.part.ProceduralPart.role',
  'art.arcane.iris.engine.procedural.part.ProceduralMaterial.role',
  'art.arcane.iris.engine.procedural.part.ProceduralDecoration.on'
])

/** A source the server lists, as opposed to the roles, which the studio reads from the nodes. */
export type ListedSource = Exclude<SuggestionSource, { from: 'roles' }>

const MOST_SHOWN = 8

/** Where the suggestions for {@code option} of the schema type {@code type} come from; null when it is typed freely. */
export function suggestionSourceOf(type: string, option: SettingOption): SuggestionSource | null {
  if (ROLE_OPTIONS.has(`${type}.${option.name}`)) {
    return { from: 'roles' }
  }
  const kind: ValueKind = option.kind.kind === 'list' ? option.kind.element : option.kind
  if (kind.kind === 'reference') {
    return { from: 'reference', reference: kind.reference }
  }
  return kind.kind === 'resource' ? { from: 'resource', resource: kind.resource } : null
}

export function sourceKey(source: ListedSource): string {
  return source.from === 'reference' ? `reference:${source.reference}` : `resource:${source.resource}`
}

/** Every role the nodes place or decorate, once each and sorted. */
export function rolesOf(roots: GraphNode[]): string[] {
  const roles = new Set<string>()
  const visit = (node: GraphNode): void => {
    node.roles.forEach((role) => roles.add(role))
    node.children.forEach((link) => visit(link.node))
  }
  roots.forEach(visit)
  return [...roles].sort()
}

/** The word being typed: from where it starts in {@code draft}, with what is typed of it. For a list that is the item after the last comma. */
export function wordBeingTyped(draft: string, isList: boolean): { start: number; typed: string } {
  const start = isList ? draft.lastIndexOf(',') + 1 : 0
  const typed = draft.slice(start)
  return { start: start + (typed.length - typed.trimStart().length), typed: typed.trim() }
}

/** The choices that hold what is typed, those that start with it first; none while nothing is typed or it is already a choice. */
export function matching(choices: readonly string[], typed: string): string[] {
  const wanted = typed.toLowerCase()
  if (wanted === '') {
    return []
  }
  const starting: string[] = []
  const containing: string[] = []
  for (const choice of choices) {
    const lower = choice.toLowerCase()
    if (lower === wanted) {
      return []
    }
    if (lower.startsWith(wanted)) {
      starting.push(choice)
    } else if (lower.includes(wanted)) {
      containing.push(choice)
    }
  }
  return [...starting, ...containing].slice(0, MOST_SHOWN)
}

/** The draft with the word being typed replaced by {@code choice}; a list gets its separator ready for the next item. */
export function completed(draft: string, choice: string, isList: boolean): string {
  const { start } = wordBeingTyped(draft, isList)
  return draft.slice(0, start) + choice + (isList ? LIST_SEPARATOR : '')
}

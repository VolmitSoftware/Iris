import type { JsonPath, NodeSettings, SettingOption, SettingsType } from '../bridge/types'
import { isCurve } from './curves'
import { isWithin } from './nodeLayout'
import { textOf } from './settingText'
import { valueAt } from './jsonValues'

const SUMMARY_SETTINGS = 3
const SUMMARY_SEPARATOR = ' · '
const DEFAULT_NAMESPACE = 'minecraft:'

/**
 * What a section is: a node's own settings, a setting written as an object, a setting written as a list of objects, or
 * one element of such a list. All but the first fold.
 */
export type FoldKind = 'own' | 'group' | 'list' | 'item'

/** One object of the file whose settings the inspector shows, below the ones that hold it. */
export interface SettingsFold {
  title: string
  path: JsonPath
  type: string
  kind: FoldKind
  /** 0 for a node's own sections, one more for each object it is inside. */
  depth: number
  /** A short reading of what it holds, shown while it is folded. */
  summary: string
  /** The object and member that hold a group or list, to unset it; null for the rest. */
  holder: { object: JsonPath; key: string } | null
  /** The settings written plainly in it, as fields; those that are folds of their own are not among them. */
  options: SettingOption[]
}

function isObject(value: unknown): value is Record<string, unknown> {
  return typeof value === 'object' && value !== null && !Array.isArray(value)
}

function isObjects(value: unknown): value is Record<string, unknown>[] {
  return Array.isArray(value) && value.every(isObject)
}

export function idOfFold(fold: SettingsFold): string {
  return JSON.stringify(fold.path)
}

/**
 * The sections of one object of a node and the objects inside it, in file order, each followed by those inside it. A
 * setting that is itself an object whose type the schema describes is a section of its own one level deeper, and so is
 * each element of a list of objects; one that is not written stays a field.
 */
export function foldedSections(section: NodeSettings, types: Record<string, SettingsType>, values: unknown): SettingsFold[] {
  const folds: SettingsFold[] = []
  addFold(folds, types, values, { title: section.title, path: section.path, type: section.type, kind: 'own', depth: 0, holder: null }, new Set(section.shownAsNodes))
  return folds
}

type FoldHead = Pick<SettingsFold, 'title' | 'path' | 'type' | 'kind' | 'depth' | 'holder'>

function addFold(into: SettingsFold[], types: Record<string, SettingsType>, values: unknown, head: FoldHead, shownAsNodes: Set<string>): void {
  const options: SettingOption[] = []
  const inside: SettingsFold[] = []
  const summary: string[] = []
  for (const option of types[head.type]?.options ?? []) {
    if (shownAsNodes.has(option.name)) {
      continue
    }
    const path = [...head.path, option.name]
    const value = valueAt(values, path)
    const holder = { object: head.path, key: option.name }
    const objectType = option.kind.kind === 'object' ? option.kind.type : null
    const itemType = option.kind.kind === 'list' && option.kind.element.kind === 'object' ? option.kind.element.type : null
    if (objectType !== null && isObject(value) && types[objectType] !== undefined) {
      addFold(inside, types, values, { title: option.name, path, type: objectType, kind: 'group', depth: head.depth + 1, holder }, new Set())
      continue
    }
    if (itemType !== null && isObjects(value) && types[itemType] !== undefined) {
      addList(inside, types, values, { option, path, itemType, count: value.length, depth: head.depth + 1, holder })
      summary.push(`${option.name} ${value.length}`)
      continue
    }
    options.push(option)
    if (value !== undefined) {
      summary.push(`${option.name} ${shortText(option, value)}`)
    }
  }
  into.push({ ...head, summary: summary.slice(0, SUMMARY_SETTINGS).join(SUMMARY_SEPARATOR), options })
  into.push(...inside)
}

interface ListFold {
  option: SettingOption
  path: JsonPath
  itemType: string
  count: number
  depth: number
  holder: { object: JsonPath; key: string }
}

function addList(into: SettingsFold[], types: Record<string, SettingsType>, values: unknown, list: ListFold): void {
  into.push({
    title: list.option.name,
    path: list.path,
    type: list.itemType,
    kind: 'list',
    depth: list.depth,
    summary: `${list.count} ${list.count === 1 ? 'item' : 'items'}`,
    holder: list.holder,
    options: []
  })
  for (let index = 0; index < list.count; index++) {
    addFold(
      into,
      types,
      values,
      { title: String(index + 1), path: [...list.path, index], type: list.itemType, kind: 'item', depth: list.depth + 1, holder: null },
      new Set()
    )
  }
}

function shortText(option: SettingOption, value: unknown): string {
  return isCurve(value) ? 'curve' : textOf(option.kind, value).replaceAll(DEFAULT_NAMESPACE, '')
}

/** The folds shown while the ones in {@code open} are unfolded: a fold inside another that is folded is hidden. */
export function visibleFolds(folds: SettingsFold[], open: ReadonlySet<string>): SettingsFold[] {
  const foldable = folds.filter((fold) => fold.kind !== 'own')
  return folds.filter((fold) => !foldable.some((outer) => outer !== fold && !open.has(idOfFold(outer)) && outer.path.length < fold.path.length && isWithin(fold.path, outer.path)))
}

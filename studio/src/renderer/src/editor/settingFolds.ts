import type { JsonPath, NodeSettings, SettingOption, SettingsType, ValueKind } from '../bridge/types'
import { isCurve } from './curves'
import { isWithin } from './nodeLayout'
import { isSpan, textOf } from './settingText'
import { valueAt } from './jsonValues'

const SUMMARY_SETTINGS = 3
const SUMMARY_SEPARATOR = ' · '
const DEFAULT_NAMESPACE = 'minecraft:'

/**
 * What a section is: a node's own settings, a setting written as an object, a setting written as a list of objects, or
 * one element of such a list. All but the first fold.
 */
export type FoldKind = 'own' | 'group' | 'list' | 'item'

/** One object of the file whose settings a node lists, below the ones that hold it. */
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
  /** The settings listed as rows: those written plainly in it, and the plain ones it requires but leaves unset. */
  settings: SettingOption[]
  /** What it could set but leaves at the default, to add. */
  unset: SettingOption[]
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

/** The schema type of a setting that folds open as a section of its own when written; null for one that is a row. */
function groupTypeOf(kind: ValueKind): string | null {
  return kind.kind === 'object' && !isSpan(kind) ? kind.type : null
}

function itemTypeOf(kind: ValueKind): string | null {
  return kind.kind === 'list' && kind.element.kind === 'object' ? kind.element.type : null
}

/**
 * The sections of one object of a node and the objects inside it, in file order, each followed by those inside it. A
 * setting written as an object whose type the schema describes is a section of its own one level deeper, and so is
 * each element of a list of objects. Only what is written is listed; the rest can be added.
 */
export function foldedSections(section: NodeSettings, types: Record<string, SettingsType>, values: unknown): SettingsFold[] {
  const folds: SettingsFold[] = []
  addFold(folds, types, values, { title: section.title, path: section.path, type: section.type, kind: 'own', depth: 0, holder: null }, new Set(section.shownAsNodes))
  return folds
}

type FoldHead = Pick<SettingsFold, 'title' | 'path' | 'type' | 'kind' | 'depth' | 'holder'>

function addFold(into: SettingsFold[], types: Record<string, SettingsType>, values: unknown, head: FoldHead, shownAsNodes: Set<string>): void {
  const settings: SettingOption[] = []
  const unset: SettingOption[] = []
  const inside: SettingsFold[] = []
  const summary: string[] = []
  for (const option of types[head.type]?.options ?? []) {
    if (shownAsNodes.has(option.name)) {
      continue
    }
    const path = [...head.path, option.name]
    const value = valueAt(values, path)
    const holder = { object: head.path, key: option.name }
    const groupType = groupTypeOf(option.kind)
    const itemType = itemTypeOf(option.kind)
    if (value === undefined) {
      if (option.required && groupType === null && itemType === null) {
        settings.push(option)
      } else {
        unset.push(option)
      }
      continue
    }
    if (groupType !== null && isObject(value) && types[groupType] !== undefined) {
      addFold(inside, types, values, { title: option.name, path, type: groupType, kind: 'group', depth: head.depth + 1, holder }, new Set())
      continue
    }
    if (itemType !== null && isObjects(value) && types[itemType] !== undefined) {
      addList(inside, types, values, { option, path, itemType, count: value.length, depth: head.depth + 1, holder })
      summary.push(`${option.name} ${value.length}`)
      continue
    }
    settings.push(option)
    summary.push(`${option.name} ${shortText(option, value)}`)
  }
  into.push({ ...head, summary: summary.slice(0, SUMMARY_SETTINGS).join(SUMMARY_SEPARATOR), settings, unset })
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
    settings: [],
    unset: []
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

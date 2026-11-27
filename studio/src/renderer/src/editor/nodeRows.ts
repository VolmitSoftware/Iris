import type { GraphNode, SettingOption, SettingsType } from '../bridge/types'
import { valueAt } from './jsonValues'
import { idOf } from './nodeLayout'
import { foldedSections, idOfFold, visibleFolds, type SettingsFold } from './settingFolds'

export const NODE_STRIPE = 3
export const NODE_HEADER = 28
export const NODE_ROW = 24
export const NODE_HEADING = 20
export const NODE_PADDING = 8

export type NodeRow =
  | { kind: 'heading'; id: string; fold: SettingsFold }
  | { kind: 'fold'; id: string; fold: SettingsFold; open: boolean }
  | { kind: 'setting'; id: string; fold: SettingsFold; option: SettingOption; value: unknown }
  | { kind: 'addSetting'; id: string }

/** What a node lists below its header, how tall that makes it, and the sections that have settings left to add. */
export interface NodeRows {
  rows: NodeRow[]
  height: number
  addable: SettingsFold[]
}

const ADD_SETTING_ID = 'add-setting'

export function heightOfRow(row: NodeRow): number {
  return row.kind === 'heading' ? NODE_HEADING : NODE_ROW
}

/**
 * The rows of {@code node}: each of its sections' settings, the first section's plainly, a later one's under its
 * heading, and a folding section's only while it is in {@code open}. An editable node with settings left to add ends
 * in a row to add one.
 */
export function nodeRows(node: GraphNode, types: Record<string, SettingsType>, values: unknown, open: ReadonlySet<string>, editable: boolean): NodeRows {
  const rows: NodeRow[] = []
  const addable: SettingsFold[] = []
  let ownSections = 0
  for (const section of node.settings) {
    const folds = foldedSections(section, types, values)
    addable.push(...folds.filter((fold) => fold.unset.length > 0))
    for (const fold of visibleFolds(folds, open)) {
      const id = idOfFold(fold)
      if (fold.kind !== 'own') {
        rows.push({ kind: 'fold', id: `fold:${id}`, fold, open: open.has(id) })
      } else if (ownSections++ > 0) {
        rows.push({ kind: 'heading', id: `heading:${id}`, fold })
      }
      if (fold.kind === 'own' || open.has(id)) {
        rows.push(...settingRows(fold, values))
      }
    }
  }
  if (editable && addable.length > 0) {
    rows.push({ kind: 'addSetting', id: ADD_SETTING_ID })
  }
  const height = NODE_STRIPE + NODE_HEADER + rows.reduce((sum, row) => sum + heightOfRow(row), 0) + NODE_PADDING
  return { rows, height, addable }
}

function settingRows(fold: SettingsFold, values: unknown): NodeRow[] {
  return fold.settings.map((option) => {
    const path = [...fold.path, option.name]
    return { kind: 'setting', id: idOf(path), fold, option, value: valueAt(values, path) }
  })
}

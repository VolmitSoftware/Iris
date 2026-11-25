import { createContext, useContext } from 'react'
import type { GraphEdit } from '../bridge/types'
import type { NodeRow } from '../editor/nodeRows'
import type { SuggestionSource } from '../editor/suggestions'

export type SettingRow = Extract<NodeRow, { kind: 'setting' }>

/** What the nodes' rows share: the edits they make to the file, and which curve editor is open. */
export interface NodeEditing {
  locked: boolean
  /** The id of the setting row whose curve editor is open, or null. */
  openCurve: string | null
  onOpenCurve(rowId: string | null): void
  onToggleFold(foldId: string): void
  onEdit(edit: GraphEdit): void
  loadSuggestions(source: SuggestionSource): Promise<string[]>
  /** Notes the setting a right-click is on, so the node's menu can offer to make it a curve or one number. */
  onSettingMenu(row: SettingRow): void
}

export const NodeEditingContext = createContext<NodeEditing | null>(null)

export function useNodeEditing(): NodeEditing {
  const editing = useContext(NodeEditingContext)
  if (editing === null) {
    throw new Error('A node row was drawn outside a node canvas')
  }
  return editing
}

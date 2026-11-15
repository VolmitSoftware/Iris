import { useCallback, useEffect, useRef, useState } from 'react'
import { BridgeError, STATUS_CONFLICT, type BridgeClient } from '../bridge/BridgeClient'
import type { EditorState, GraphEdit } from '../bridge/types'
import { EditHistory } from './EditHistory'

export interface NodeEditor {
  /** The entry's file as it is now; null while it is read. */
  state: EditorState | null
  saving: boolean
  canUndo: boolean
  canRedo: boolean
  edit(edit: GraphEdit): void
  undo(): void
  redo(): void
}

/**
 * Reads an entry's file as nodes and edits it. Edits are sent one after another, each on the text the one before left,
 * so quick edits never refuse each other; one refused because the file changed elsewhere reads the file again.
 */
export function useNodeEditor(client: BridgeClient, viewId: string, key: string | null, packRevision: number, tell: (message: string) => void): NodeEditor {
  const [state, setState] = useState<EditorState | null>(null)
  const [saving, setSaving] = useState(false)
  const [, setHistoryChanges] = useState(0)
  const history = useRef(new EditHistory())
  const shown = useRef<EditorState | null>(null)
  const queue = useRef<Promise<void>>(Promise.resolve())
  const entry = useRef({ viewId, key })

  const run = useCallback(
    (task: (current: EditorState | null) => Promise<EditorState | null>): void => {
      const asked = entry.current
      queue.current = queue.current.then(async () => {
        if (entry.current !== asked) {
          return
        }
        setSaving(true)
        try {
          const next = await task(shown.current)
          if (entry.current === asked && next !== null) {
            shown.current = next
            setState(next)
          }
        } catch (failure) {
          if (entry.current !== asked) {
            return
          }
          tell((failure as Error).message)
          if (failure instanceof BridgeError && failure.status === STATUS_CONFLICT && asked.key !== null) {
            const reread = await client.sceneFile(asked.viewId, asked.key).catch(() => null)
            if (reread !== null && entry.current === asked) {
              shown.current = reread
              setState(reread)
            }
          }
        } finally {
          setSaving(false)
          setHistoryChanges((changes) => changes + 1)
        }
      })
    },
    [client, tell]
  )

  useEffect(() => {
    entry.current = { viewId, key }
    history.current = new EditHistory()
    shown.current = null
    setState(null)
  }, [viewId, key])

  useEffect(() => {
    if (key !== null) {
      run(() => client.sceneFile(viewId, key))
    }
  }, [client, viewId, key, packRevision, run])

  const edit = useCallback(
    (change: GraphEdit) =>
      run(async (current) => {
        if (current === null || key === null) {
          return null
        }
        const next = await client.sceneEdit(viewId, key, current.revision, change)
        if (next.text !== current.text) {
          history.current.edited(current.text)
        }
        return next
      }),
    [client, viewId, key, run]
  )

  const undo = useCallback(
    () =>
      run(async (current) => {
        const earlier = history.current.nextUndo()
        if (current === null || key === null || earlier === undefined) {
          return null
        }
        const next = await client.sceneWrite(viewId, key, current.revision, earlier)
        history.current.undone(current.text)
        return next
      }),
    [client, viewId, key, run]
  )

  const redo = useCallback(
    () =>
      run(async (current) => {
        const later = history.current.nextRedo()
        if (current === null || key === null || later === undefined) {
          return null
        }
        const next = await client.sceneWrite(viewId, key, current.revision, later)
        history.current.redone(current.text)
        return next
      }),
    [client, viewId, key, run]
  )

  return { state, saving, canUndo: history.current.canUndo, canRedo: history.current.canRedo, edit, undo, redo }
}

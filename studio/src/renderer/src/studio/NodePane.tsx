import { IdeIcon } from '@/components/IdeIcon'
import { useEffect, useRef } from 'react'
import { Button } from '@/components/ui/button'
import type { GraphNode } from '../bridge/types'
import type { NodeEditor } from '../editor/useNodeEditor'
import { NodeCanvas } from './NodeCanvas'
import { isInEntryBrowser, isTypingInto, nodeEditorActionFor, type NodeEditorAction } from './shortcuts'
import { useBindings } from './useBindings'

interface NodePaneProps {
  editor: NodeEditor
  /** The entry the file belongs to. */
  entryKey: string
  selected: GraphNode | null
  onSelect(node: GraphNode | null): void
}

/** The node editor under the scene: the entry's file as nodes, with undo and redo. */
export function NodePane({ editor, entryKey, selected, onSelect }: NodePaneProps): React.JSX.Element {
  const { state } = editor
  const locked = state === null || state.locked !== null
  const showAllRef = useRef<(() => void) | null>(null)
  useEditorKeys(editor, selected, locked, showAllRef, onSelect)

  return (
    <section className="flex min-h-0 flex-[9] flex-col overflow-hidden rounded-lg bg-card">
      <header className="flex h-9 shrink-0 items-center gap-2 border-b bg-card px-3">
        <h2 className="text-[12px] font-semibold">Nodes</h2>
        <span className="min-w-0 truncate text-[11.5px] text-secondary-text">{state?.file ?? entryKey}</span>
        <span className="flex-1" />
        {state?.locked != null && (
          <span className="flex shrink-0 items-center gap-1 text-[11.5px] whitespace-nowrap text-secondary-text" title={state.locked}>
            <IdeIcon name="locked" className="size-3.5" />
            Read only
          </span>
        )}
        {editor.saving && <span className="text-[11.5px] text-secondary-text">Saving…</span>}
        <Button variant="ghost" size="icon-sm" title="Undo (Ctrl+Z)" disabled={locked || !editor.canUndo} onClick={editor.undo}>
          <IdeIcon name="undo" />
        </Button>
        <Button variant="ghost" size="icon-sm" title="Redo (Ctrl+Shift+Z)" disabled={locked || !editor.canRedo} onClick={editor.redo}>
          <IdeIcon name="redo" />
        </Button>
      </header>
      <div className="relative min-h-0 flex-1">
        {state === null && <p className="p-4 text-[12px] text-muted-foreground">Reading the file…</p>}
        {state !== null && state.roots === null && (
          <div className="p-4 text-[12px]">
            <p className="font-medium">This file can&apos;t be shown as nodes</p>
            <p className="pt-1 text-secondary-text">{state.problem}</p>
          </div>
        )}
        {state !== null && state.roots !== null && (
          <NodeCanvas
            roots={state.roots}
            fileKey={entryKey}
            selectedId={selected === null ? null : JSON.stringify(selected.path)}
            locked={locked}
            showAllRef={showAllRef}
            onSelect={onSelect}
            onEdit={editor.edit}
          />
        )}
      </div>
    </section>
  )
}

function useEditorKeys(
  editor: NodeEditor,
  selected: GraphNode | null,
  locked: boolean,
  showAllRef: React.RefObject<(() => void) | null>,
  onSelect: (node: GraphNode | null) => void
): void {
  const { undo, redo, edit } = editor
  const bindings = useBindings()
  useEffect(() => {
    const editSelected = (action: NodeEditorAction, event: KeyboardEvent): void => {
      if (action === 'undo') {
        event.preventDefault()
        undo()
      } else if (action === 'redo') {
        event.preventDefault()
        redo()
      } else if (selected !== null && selected.duplicable) {
        event.preventDefault()
        edit({ op: action === 'deleteNode' ? 'delete' : 'duplicate', path: selected.path })
      }
    }
    // Captured, so the editor's keys come before the window's own: F fits the nodes instead of following the player.
    const down = (event: KeyboardEvent): void => {
      const action = isTypingInto(event.target) ? null : nodeEditorActionFor(event, bindings)
      if (action === 'deleteNode' && isInEntryBrowser(event.target)) {
        return
      }
      if (action === 'dismiss') {
        onSelect(null)
      } else if (action === 'showAllNodes' && showAllRef.current !== null) {
        event.preventDefault()
        event.stopImmediatePropagation()
        showAllRef.current()
      } else if (action !== null && action !== 'showAllNodes' && !locked) {
        editSelected(action, event)
      }
    }
    window.addEventListener('keydown', down, true)
    return () => window.removeEventListener('keydown', down, true)
  }, [undo, redo, edit, selected, locked, bindings, showAllRef, onSelect])
}

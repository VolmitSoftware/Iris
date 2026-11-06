import { Lock, Redo2, Undo2 } from 'lucide-react'
import { useEffect } from 'react'
import { Button } from '@/components/ui/button'
import type { GraphNode } from '../bridge/types'
import type { NodeEditor } from '../editor/useNodeEditor'
import { NodeCanvas } from './NodeCanvas'
import { isTypingInto } from './shortcuts'

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
  useEditorKeys(editor, selected, locked)

  return (
    <section className="flex min-h-0 flex-[9] flex-col border-t">
      <header className="flex h-9 shrink-0 items-center gap-2 border-b bg-card px-3">
        <h2 className="text-[12px] font-semibold">Nodes</h2>
        <span className="min-w-0 truncate text-[11.5px] text-secondary-text">{state?.file ?? entryKey}</span>
        <span className="flex-1" />
        {state?.locked != null && (
          <span className="flex items-center gap-1 text-[11.5px] text-secondary-text">
            <Lock className="size-3.5" />
            {state.locked}
          </span>
        )}
        {editor.saving && <span className="text-[11.5px] text-secondary-text">Saving…</span>}
        <Button variant="ghost" size="icon-sm" title="Undo (Ctrl+Z)" disabled={locked || !editor.canUndo} onClick={editor.undo}>
          <Undo2 />
        </Button>
        <Button variant="ghost" size="icon-sm" title="Redo (Ctrl+Shift+Z)" disabled={locked || !editor.canRedo} onClick={editor.redo}>
          <Redo2 />
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
            onSelect={onSelect}
            onEdit={editor.edit}
          />
        )}
      </div>
    </section>
  )
}

function useEditorKeys(editor: NodeEditor, selected: GraphNode | null, locked: boolean): void {
  const { undo, redo, edit } = editor
  useEffect(() => {
    if (locked) {
      return
    }
    const down = (event: KeyboardEvent): void => {
      if (isTypingInto(event.target)) {
        return
      }
      const command = event.ctrlKey || event.metaKey
      if (command && event.key.toLowerCase() === 'z') {
        event.preventDefault()
        if (event.shiftKey) {
          redo()
        } else {
          undo()
        }
      } else if (command && event.key.toLowerCase() === 'y') {
        event.preventDefault()
        redo()
      } else if (event.key === 'Delete' && selected !== null && selected.duplicable) {
        event.preventDefault()
        edit({ op: 'delete', path: selected.path })
      }
    }
    window.addEventListener('keydown', down)
    return () => window.removeEventListener('keydown', down)
  }, [undo, redo, edit, selected, locked])
}

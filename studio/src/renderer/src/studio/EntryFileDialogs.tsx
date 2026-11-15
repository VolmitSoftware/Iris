import { useMemo, useState } from 'react'
import { Button } from '@/components/ui/button'
import { Dialog, DialogContent, DialogDescription, DialogFooter, DialogHeader, DialogTitle } from '@/components/ui/dialog'
import { Input } from '@/components/ui/input'
import type { BridgeClient } from '../bridge/BridgeClient'
import { refusalOfNewKey } from '../editor/entryKeys'
import type { EntryFileActions } from './EntryBrowser'

const MOST_USES_LISTED = 8

type Ask = { kind: 'create'; folderPrefix: string } | { kind: 'delete'; key: string; uses: string[] }

interface EntryFiles {
  actions: EntryFileActions
  dialogs: React.JSX.Element | null
}

/**
 * Creating and deleting a view's entries: asking for a new key, and listing what still uses an entry before it goes to
 * the trash. The new entry is listed once the pack has reloaded, so `onCreated` names it to be chosen then.
 */
export function useEntryFiles(
  client: BridgeClient,
  viewId: string,
  keys: string[],
  onCreated: (key: string) => void,
  tell: (message: string) => void
): EntryFiles {
  const [ask, setAsk] = useState<Ask | null>(null)
  const close = (): void => setAsk(null)
  const actions = useMemo<EntryFileActions>(
    () => ({
      onCreate: (folderPrefix) => setAsk({ kind: 'create', folderPrefix }),
      onDelete: (key) => {
        client
          .sceneUses(viewId, key)
          .then((uses) => setAsk({ kind: 'delete', key, uses }))
          .catch((failure: Error) => tell(failure.message))
      }
    }),
    [client, viewId, tell]
  )
  const create = (key: string): void => {
    close()
    client
      .sceneCreate(viewId, key)
      .then(() => {
        onCreated(key)
        tell(`Created ${key}`)
      })
      .catch((failure: Error) => tell(failure.message))
  }
  const remove = (key: string): void => {
    close()
    client
      .sceneDelete(viewId, key)
      .then((to) => tell(`Moved ${key} to ${to}`))
      .catch((failure: Error) => tell(failure.message))
  }
  const dialogs =
    ask === null ? null : ask.kind === 'create' ? (
      <NewEntryDialog initial={ask.folderPrefix} existing={keys} onCreate={create} onClose={close} />
    ) : (
      <DeleteEntryDialog entryKey={ask.key} uses={ask.uses} onDelete={() => remove(ask.key)} onClose={close} />
    )
  return { actions, dialogs }
}

interface NewEntryDialogProps {
  initial: string
  existing: string[]
  onCreate(key: string): void
  onClose(): void
}

function NewEntryDialog({ initial, existing, onCreate, onClose }: NewEntryDialogProps): React.JSX.Element {
  const [typed, setTyped] = useState(initial)
  const key = typed.trim()
  const refusal = refusalOfNewKey(key, existing)
  return (
    <Dialog open onOpenChange={(open) => !open && onClose()}>
      <DialogContent className="sm:max-w-sm">
        <form
          className="grid gap-4"
          onSubmit={(event) => {
            event.preventDefault()
            if (refusal === null) {
              onCreate(key)
            }
          }}
        >
          <DialogHeader>
            <DialogTitle className="text-[15px]">New entry</DialogTitle>
            <DialogDescription>Name, with its folders, like trees/oak</DialogDescription>
          </DialogHeader>
          <Input autoFocus value={typed} onChange={(event) => setTyped(event.target.value)} />
          <p className="h-4 text-[11.5px] text-secondary-text">{typed === initial ? '' : (refusal ?? '')}</p>
          <DialogFooter>
            <Button type="button" variant="ghost" onClick={onClose}>
              Cancel
            </Button>
            <Button type="submit" disabled={refusal !== null}>
              Create
            </Button>
          </DialogFooter>
        </form>
      </DialogContent>
    </Dialog>
  )
}

interface DeleteEntryDialogProps {
  entryKey: string
  uses: string[]
  onDelete(): void
  onClose(): void
}

// Used elsewhere, Cancel has the focus; nothing using it, moving it to the trash does.
function DeleteEntryDialog({ entryKey, uses, onDelete, onClose }: DeleteEntryDialogProps): React.JSX.Element {
  const unused = uses.length === 0
  return (
    <Dialog open onOpenChange={(open) => !open && onClose()}>
      <DialogContent className="sm:max-w-md">
        <DialogHeader>
          <DialogTitle className="text-[15px]">Delete {entryKey}?</DialogTitle>
          <DialogDescription>{unused ? 'Nothing in the pack uses it.' : 'Still used by these files, which will warn that it is missing:'}</DialogDescription>
        </DialogHeader>
        {!unused && (
          <ul className="flex flex-col gap-0.5 text-[12px]">
            {uses.slice(0, MOST_USES_LISTED).map((file) => (
              <li key={file} className="truncate">
                {file}
              </li>
            ))}
            {uses.length > MOST_USES_LISTED && <li className="text-secondary-text">and {uses.length - MOST_USES_LISTED} more</li>}
          </ul>
        )}
        <DialogFooter>
          <Button variant="ghost" autoFocus={!unused} onClick={onClose}>
            Cancel
          </Button>
          <Button variant="destructive" autoFocus={unused} onClick={onDelete}>
            Move to trash
          </Button>
        </DialogFooter>
      </DialogContent>
    </Dialog>
  )
}

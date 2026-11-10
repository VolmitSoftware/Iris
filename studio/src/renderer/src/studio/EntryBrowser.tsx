import { Plus } from 'lucide-react'
import { useMemo, useState } from 'react'
import { Button } from '@/components/ui/button'
import { ContextMenu, ContextMenuContent, ContextMenuItem, ContextMenuTrigger } from '@/components/ui/context-menu'
import { Input } from '@/components/ui/input'
import { ScrollArea } from '@/components/ui/scroll-area'
import { cn } from '@/lib/utils'
import { folderPrefixOf } from '../editor/entryKeys'

const NUMBER = new Intl.NumberFormat('en-US')
const DELETE_KEY = 'Delete'

/** An entry of a module view: its key, the name it is listed by, and the heading it is listed under (empty for none). */
export interface BrowsableEntry {
  key: string
  name: string
  group: string
}

/** What the browser can do with the files of its entries, for a view whose entries are files of the pack. */
export interface EntryFileActions {
  /** Asks for a new entry, offering `folderPrefix`, such as `trees/`, to name it in. */
  onCreate(folderPrefix: string): void
  /** Deletes the entry at `key`, once what still uses it was shown. */
  onDelete(key: string): void
}

interface EntryBrowserProps {
  title: string
  entries: BrowsableEntry[]
  current: string | null
  onSelect(key: string): void
  fileActions?: EntryFileActions
  className?: string
}

type MenuTarget = { entry: string } | { folder: string } | null

/** A view's entries under their headings, with a search over keys, names and headings (v3e ObjectBrowser). */
export function EntryBrowser({ title, entries, current, onSelect, fileActions, className }: EntryBrowserProps): React.JSX.Element {
  const [search, setSearch] = useState('')
  const [menuTarget, setMenuTarget] = useState<MenuTarget>(null)
  const wanted = search.trim().toLowerCase()
  const groups = useMemo(() => groupedEntries(entries, wanted), [entries, wanted])
  const list = (
    <div
      className="px-2 py-1.5"
      onContextMenu={(event) => {
        const target = menuTargetOf(event.target)
        setMenuTarget(target)
        if (target !== null && 'entry' in target) {
          onSelect(target.entry)
        }
      }}
    >
      {groups.map(([group, members]) => (
        <section key={group}>
          {group !== '' && (
            <h3 data-entry-folder={group} className="truncate px-2 pt-2 pb-1 text-[11px] font-semibold text-muted-foreground">
              {group}
            </h3>
          )}
          {members.map((entry) => (
            <button
              key={entry.key}
              data-entry-key={entry.key}
              title={entry.key}
              onClick={() => onSelect(entry.key)}
              onKeyDown={(event) => fileActions !== undefined && event.key === DELETE_KEY && fileActions.onDelete(entry.key)}
              className={cn('flex h-[26px] w-full items-center rounded-md px-2 text-left', entry.key === current ? 'bg-selection' : 'hover:bg-accent')}
            >
              <span className="truncate">{entry.name}</span>
            </button>
          ))}
        </section>
      ))}
      {groups.length === 0 && <p className="px-2 py-1 text-[12px] text-muted-foreground">{entries.length === 0 ? 'Nothing to show' : 'Nothing matches'}</p>}
    </div>
  )
  return (
    <section data-entry-browser className={cn('flex min-h-0 flex-col border-b', className)}>
      <header className="px-4 pt-2.5 pb-2.5">
        <div className="flex items-center gap-1">
          <h2 className="min-w-0 flex-1 truncate text-[13px] font-semibold">{title}</h2>
          {fileActions !== undefined && (
            <Button variant="ghost" size="icon-xs" title="New entry in the chosen one's folder" onClick={() => fileActions.onCreate(folderPrefixOf(current ?? ''))}>
              <Plus />
            </Button>
          )}
        </div>
        <p className="truncate text-[11.5px] text-secondary-text">{NUMBER.format(entries.length)} in the pack</p>
        <Input
          className="mt-2 h-7 text-[12px]"
          placeholder="Find by name or folder"
          value={search}
          onChange={(event) => setSearch(event.target.value)}
          onKeyDown={(event) => event.key === 'Escape' && setSearch('')}
        />
      </header>
      <ScrollArea className="min-h-0 flex-1 border-t">
        {fileActions === undefined ? (
          list
        ) : (
          <ContextMenu onOpenChange={(open) => !open && setMenuTarget(null)}>
            <ContextMenuTrigger asChild>{list}</ContextMenuTrigger>
            <EntryMenu target={menuTarget} actions={fileActions} />
          </ContextMenu>
        )}
      </ScrollArea>
    </section>
  )
}

function EntryMenu({ target, actions }: { target: MenuTarget; actions: EntryFileActions }): React.JSX.Element {
  if (target !== null && 'entry' in target) {
    return (
      <ContextMenuContent className="min-w-[180px]">
        <ContextMenuItem onSelect={() => actions.onCreate(folderPrefixOf(target.entry))}>New entry beside it…</ContextMenuItem>
        <ContextMenuItem variant="destructive" onSelect={() => actions.onDelete(target.entry)}>
          Delete…
        </ContextMenuItem>
      </ContextMenuContent>
    )
  }
  const folder = target === null ? '' : target.folder
  return (
    <ContextMenuContent className="min-w-[180px]">
      <ContextMenuItem onSelect={() => actions.onCreate(folder === '' ? '' : `${folder}/`)}>{folder === '' ? 'New entry…' : `New entry in ${folder}…`}</ContextMenuItem>
    </ContextMenuContent>
  )
}

function menuTargetOf(eventTarget: EventTarget): MenuTarget {
  if (!(eventTarget instanceof Element)) {
    return null
  }
  const entry = eventTarget.closest<HTMLElement>('[data-entry-key]')?.dataset.entryKey
  if (entry !== undefined) {
    return { entry }
  }
  const folder = eventTarget.closest<HTMLElement>('[data-entry-folder]')?.dataset.entryFolder
  return folder === undefined ? null : { folder }
}

function groupedEntries<Entry extends BrowsableEntry>(entries: Entry[], wanted: string): [string, Entry[]][] {
  const groups = new Map<string, Entry[]>()
  for (const entry of entries) {
    if (wanted !== '' && ![entry.key, entry.name, entry.group].some((text) => text.toLowerCase().includes(wanted))) {
      continue
    }
    const members = groups.get(entry.group) ?? []
    members.push(entry)
    groups.set(entry.group, members)
  }
  return [...groups.entries()]
}

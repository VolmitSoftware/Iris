import { IdeIcon } from '@/components/IdeIcon'
import { useEffect, useMemo, useState } from 'react'
import { Button } from '@/components/ui/button'
import { ContextMenu, ContextMenuContent, ContextMenuItem, ContextMenuTrigger } from '@/components/ui/context-menu'
import { Input } from '@/components/ui/input'
import { ScrollArea } from '@/components/ui/scroll-area'
import { cn } from '@/lib/utils'
import { folderPrefixOf } from '../editor/entryKeys'

const NUMBER = new Intl.NumberFormat('en-US')
const DELETE_KEY = 'Delete'
const FOLDER_SEPARATOR = '/'
const ROW_PADDING_PX = 8
const TREE_INDENT_PX = 14
const CHEVRON_SLOT_PX = 18

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

/** A view's entries in a tree of their folders, with a search over keys, names and folders that opens every folder holding a match (v3e ObjectBrowser). */
export function EntryBrowser({ title, entries, current, onSelect, fileActions, className }: EntryBrowserProps): React.JSX.Element {
  const [search, setSearch] = useState('')
  const [menuTarget, setMenuTarget] = useState<MenuTarget>(null)
  const [openFolders, setOpenFolders] = useState<ReadonlySet<string>>(new Set())
  const wanted = search.trim().toLowerCase()
  const tree = useMemo(() => folderTreeOf(entries, wanted), [entries, wanted])
  const currentGroup = useMemo(() => entries.find((entry) => entry.key === current)?.group ?? '', [entries, current])
  useEffect(() => setOpenFolders((open) => withFolderAndAncestors(open, currentGroup)), [currentGroup])
  const toggleFolder = (path: string): void => setOpenFolders((open) => toggled(open, path))
  const tier: TierContext = { current, isOpen: (path) => wanted !== '' || openFolders.has(path), onToggle: toggleFolder, onSelect, fileActions }
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
      <FolderTier folder={tree} depth={0} context={tier} />
      {tree.size === 0 && <p className="px-2 py-1 text-[12px] text-muted-foreground">{entries.length === 0 ? 'Nothing to show' : 'Nothing matches'}</p>}
    </div>
  )
  return (
    <section data-entry-browser className={cn('flex min-h-0 flex-col', className)}>
      <header className="px-4 pt-2.5 pb-2.5">
        <div className="flex items-center gap-1">
          <h2 className="min-w-0 flex-1 truncate text-[13px] font-semibold">{title}</h2>
          {fileActions !== undefined && (
            <Button variant="ghost" size="icon-xs" title="New entry in the chosen one's folder" onClick={() => fileActions.onCreate(folderPrefixOf(current ?? ''))}>
              <IdeIcon name="add" />
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

/** A folder of the tree: its path such as `trees/oak`, its subfolders and entries, and how many entries it holds in all. */
interface FolderNode {
  path: string
  name: string
  folders: FolderNode[]
  entries: BrowsableEntry[]
  size: number
}

interface TierContext {
  current: string | null
  isOpen(path: string): boolean
  onToggle(path: string): void
  onSelect(key: string): void
  fileActions?: EntryFileActions
}

function FolderTier({ folder, depth, context }: { folder: FolderNode; depth: number; context: TierContext }): React.JSX.Element {
  return (
    <>
      {folder.folders.map((child) => (
        <FolderBranch key={child.path} folder={child} depth={depth} context={context} />
      ))}
      {folder.entries.map((entry) => (
        <EntryRow key={entry.key} entry={entry} depth={depth} context={context} />
      ))}
    </>
  )
}

function FolderBranch({ folder, depth, context }: { folder: FolderNode; depth: number; context: TierContext }): React.JSX.Element {
  const open = context.isOpen(folder.path)
  return (
    <>
      <button
        data-entry-folder={folder.path}
        title={folder.path}
        onClick={() => context.onToggle(folder.path)}
        className="flex h-[26px] w-full items-center gap-1 rounded-md px-2 text-left hover:bg-accent"
        style={{ paddingLeft: indentOf(depth) }}
      >
        <IdeIcon name="chevronRight" className={cn('size-3.5 text-muted-foreground transition-transform', open && 'rotate-90')} />
        <span className="min-w-0 truncate">{folder.name}</span>
        <span className="ml-auto shrink-0 pl-2 text-[11px] text-secondary-text">{NUMBER.format(folder.size)}</span>
      </button>
      {open && <FolderTier folder={folder} depth={depth + 1} context={context} />}
    </>
  )
}

function EntryRow({ entry, depth, context }: { entry: BrowsableEntry; depth: number; context: TierContext }): React.JSX.Element {
  const { fileActions } = context
  return (
    <button
      data-entry-key={entry.key}
      title={entry.key}
      onClick={() => context.onSelect(entry.key)}
      onKeyDown={(event) => fileActions !== undefined && event.key === DELETE_KEY && fileActions.onDelete(entry.key)}
      className={cn('flex h-[26px] w-full items-center rounded-md px-2 text-left', entry.key === context.current ? 'bg-selection' : 'hover:bg-accent')}
      style={{ paddingLeft: indentOf(depth) + CHEVRON_SLOT_PX }}
    >
      <span className="truncate">{entry.name}</span>
    </button>
  )
}

function indentOf(depth: number): number {
  return ROW_PADDING_PX + depth * TREE_INDENT_PX
}

/** The entries matching `wanted` as a tree of their folders, folders before entries at each tier. */
function folderTreeOf(entries: BrowsableEntry[], wanted: string): FolderNode {
  const root = folderNamed('')
  const folders = new Map<string, FolderNode>([['', root]])
  const folderAt = (path: string): FolderNode => {
    const known = folders.get(path)
    if (known !== undefined) {
      return known
    }
    const folder = folderNamed(path)
    folders.set(path, folder)
    folderAt(parentOf(path)).folders.push(folder)
    return folder
  }
  for (const entry of entries) {
    if (wanted !== '' && ![entry.key, entry.name, entry.group].some((text) => text.toLowerCase().includes(wanted))) {
      continue
    }
    folderAt(entry.group).entries.push(entry)
    for (let path = entry.group; ; path = parentOf(path)) {
      folderAt(path).size++
      if (path === '') {
        break
      }
    }
  }
  return root
}

function folderNamed(path: string): FolderNode {
  return { path, name: path.slice(path.lastIndexOf(FOLDER_SEPARATOR) + 1), folders: [], entries: [], size: 0 }
}

function parentOf(path: string): string {
  return path.slice(0, Math.max(path.lastIndexOf(FOLDER_SEPARATOR), 0))
}

function withFolderAndAncestors(open: ReadonlySet<string>, path: string): ReadonlySet<string> {
  const opened = new Set(open)
  for (let folder = path; folder !== ''; folder = parentOf(folder)) {
    opened.add(folder)
  }
  return opened.size === open.size ? open : opened
}

function toggled(open: ReadonlySet<string>, path: string): ReadonlySet<string> {
  const next = new Set(open)
  if (!next.delete(path)) {
    next.add(path)
  }
  return next
}

import { useMemo, useState } from 'react'
import { Input } from '@/components/ui/input'
import { ScrollArea } from '@/components/ui/scroll-area'
import { cn } from '@/lib/utils'

const NUMBER = new Intl.NumberFormat('en-US')

/** An entry of a module view: its key, the name it is listed by, and the heading it is listed under (empty for none). */
export interface BrowsableEntry {
  key: string
  name: string
  group: string
}

interface EntryBrowserProps {
  title: string
  entries: BrowsableEntry[]
  current: string | null
  onSelect(key: string): void
  className?: string
}

/** A view's entries under their headings, with a search over keys, names and headings (v3e ObjectBrowser). */
export function EntryBrowser({ title, entries, current, onSelect, className }: EntryBrowserProps): React.JSX.Element {
  const [search, setSearch] = useState('')
  const wanted = search.trim().toLowerCase()
  const groups = useMemo(() => groupedEntries(entries, wanted), [entries, wanted])
  return (
    <section className={cn('flex min-h-0 flex-col border-b', className)}>
      <header className="px-4 pt-2.5 pb-2.5">
        <h2 className="truncate text-[13px] font-semibold">{title}</h2>
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
        <div className="px-2 py-1.5">
          {groups.map(([group, members]) => (
            <section key={group}>
              {group !== '' && <h3 className="truncate px-2 pt-2 pb-1 text-[11px] font-semibold text-muted-foreground">{group}</h3>}
              {members.map((entry) => (
                <button
                  key={entry.key}
                  title={entry.key}
                  onClick={() => onSelect(entry.key)}
                  className={cn('flex h-[26px] w-full items-center rounded-md px-2 text-left', entry.key === current ? 'bg-selection' : 'hover:bg-accent')}
                >
                  <span className="truncate">{entry.name}</span>
                </button>
              ))}
            </section>
          ))}
          {groups.length === 0 && <p className="px-2 py-1 text-[12px] text-muted-foreground">{entries.length === 0 ? 'Nothing to show' : 'Nothing matches'}</p>}
        </div>
      </ScrollArea>
    </section>
  )
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

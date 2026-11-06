import { Gem, Map as MapIcon, Mountain, Sprout, Trees, Waves, type LucideIcon } from 'lucide-react'
import { Kbd } from '@/components/ui/kbd'
import { ScrollArea } from '@/components/ui/scroll-area'
import { cn } from '@/lib/utils'
import type { ViewInfo } from '../bridge/types'
import { VIEW_KEYS } from './shortcuts'

const VIEW_ICONS: Record<string, LucideIcon> = {
  biomes: Trees,
  regions: MapIcon,
  height: Mountain,
  landBiomes: Sprout,
  seaBiomes: Waves,
  caveBiomes: Gem
}

interface ViewListProps {
  views: ViewInfo[]
  current: string
  onSelect(view: string): void
  /** What the current view adds below the views, such as the regions it can preview. */
  children?: React.ReactNode
}

/** The views the server offers, in its groups, each with what it shows and its key. */
export function ViewList({ views, current, onSelect, children }: ViewListProps): React.JSX.Element {
  const groups = [...new Set(views.map((view) => view.groupLabel))]
  return (
    <aside className="w-60 shrink-0 border-r bg-card">
      <ScrollArea className="h-full">
        <nav className="flex flex-col gap-3 p-2">
          {groups.map((group) => (
            <section key={group}>
              <h2 className="px-2 pt-1 pb-1.5 text-[11px] font-semibold text-muted-foreground">{group}</h2>
              {views
                .filter((view) => view.groupLabel === group)
                .map((view) => (
                  <ViewRow key={view.id} view={view} selected={view.id === current} onSelect={() => onSelect(view.id)} />
                ))}
            </section>
          ))}
          {children}
        </nav>
      </ScrollArea>
    </aside>
  )
}

function ViewRow({ view, selected, onSelect }: { view: ViewInfo; selected: boolean; onSelect(): void }): React.JSX.Element {
  const Icon = VIEW_ICONS[view.id] ?? MapIcon
  return (
    <button
      onClick={onSelect}
      className={cn('flex w-full items-start gap-2.5 rounded-md px-2 py-1.5 text-left', selected ? 'bg-selection' : 'hover:bg-accent')}
    >
      <Icon className="mt-0.5 size-4 shrink-0 text-secondary-text" />
      <span className="min-w-0 flex-1">
        <span className="block font-medium">{view.label}</span>
        <span className="block text-[11.5px] leading-snug text-secondary-text">{view.description}</span>
      </span>
      {VIEW_KEYS[view.id] !== undefined && <Kbd className="mt-0.5">{VIEW_KEYS[view.id]}</Kbd>}
    </button>
  )
}

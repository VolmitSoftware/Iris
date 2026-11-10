import { IdeIcon, type IdeIconName } from '@/components/IdeIcon'
import { Kbd } from '@/components/ui/kbd'
import { cn } from '@/lib/utils'
import type { ViewInfo } from '../bridge/types'
import { viewShortcutId } from './shortcuts'
import { useKeyText } from './useBindings'

const VIEW_ICONS: Record<string, IdeIconName> = {
  biomes: 'views/biomes',
  regions: 'views/regions',
  height: 'views/height',
  world3d: 'views/world-3d',
  savedWorld: 'views/saved-world',
  landBiomes: 'views/land-biomes',
  seaBiomes: 'views/sea-biomes',
  caveBiomes: 'views/cave-biomes',
  'iris:terrain/palettes': 'views/palettes',
  'iris:objects': 'views/objects',
  'iris:objects/procedural': 'views/procedural',
  noise: 'views/noise'
}
const FALLBACK_ICON: IdeIconName = 'views/regions'

interface ViewListProps {
  views: ViewInfo[]
  current: string
  onSelect(view: string): void
  /** Receives the element the shown view fills with its own lists, under the views. */
  slotRef(element: HTMLElement | null): void
}

/** The left island: the views the server offers in their groups, then the shown view's own lists. */
export function ViewList({ views, current, onSelect, slotRef }: ViewListProps): React.JSX.Element {
  const groups = [...new Set(views.map((view) => view.groupLabel))]
  return (
    <div className="flex w-60 shrink-0 flex-col overflow-hidden rounded-lg bg-card">
      <nav className="flex shrink-0 flex-col gap-2 overflow-y-auto p-1.5">
        {groups.map((group) => (
          <section key={group}>
            <h2 className="px-2 pt-1 pb-1 text-[10.5px] font-semibold tracking-wider text-muted-foreground uppercase">{group}</h2>
            {views
              .filter((view) => view.groupLabel === group)
              .map((view) => (
                <ViewRow key={view.id} view={view} selected={view.id === current} onSelect={() => onSelect(view.id)} />
              ))}
          </section>
        ))}
      </nav>
      <div ref={slotRef} className="flex min-h-0 flex-1 flex-col border-t border-overlay-border/60 empty:hidden" />
    </div>
  )
}

function ViewRow({ view, selected, onSelect }: { view: ViewInfo; selected: boolean; onSelect(): void }): React.JSX.Element {
  const keyText = useKeyText()(viewShortcutId(view.id))
  return (
    <button
      onClick={onSelect}
      title={view.description}
      className={cn('flex h-[26px] w-full items-center gap-2 rounded-md px-2 text-left', selected ? 'bg-selection' : 'hover:bg-accent')}
    >
      <IdeIcon name={VIEW_ICONS[view.id] ?? FALLBACK_ICON} className="size-3.5" />
      <span className="min-w-0 flex-1 truncate">{view.label}</span>
      {keyText !== '' && <Kbd className="h-4 min-w-4 border border-input/60 bg-transparent text-[10px] uppercase">{keyText}</Kbd>}
    </button>
  )
}

import { Fragment } from 'react'
import { IdeIcon, type IdeIconName } from '@/components/IdeIcon'
import { Kbd } from '@/components/ui/kbd'
import { Tooltip, TooltipContent, TooltipTrigger } from '@/components/ui/tooltip'
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
  /** Receives the element the shown view fills with its own lists, beside the views. */
  slotRef(element: HTMLElement | null): void
}

/**
 * The views the server offers, as a stripe of buttons on the window's edge in their groups, and beside it the island
 * the shown view fills with its own lists, which is not there for a view without any (IntelliJ's tool window stripe).
 */
export function ViewList({ views, current, onSelect, slotRef }: ViewListProps): React.JSX.Element {
  const groups = [...new Set(views.map((view) => view.groupLabel))]
  return (
    <>
      <nav className="flex w-8 shrink-0 flex-col items-center gap-1.5 overflow-y-auto">
        {groups.map((group, index) => (
          <Fragment key={group}>
            {index > 0 && <span className="my-0.5 h-px w-5 shrink-0 bg-input" />}
            {views
              .filter((view) => view.groupLabel === group)
              .map((view) => (
                <ViewButton key={view.id} view={view} selected={view.id === current} onSelect={() => onSelect(view.id)} />
              ))}
          </Fragment>
        ))}
      </nav>
      <div ref={slotRef} className="flex w-60 shrink-0 flex-col overflow-hidden rounded-lg bg-card empty:hidden" />
    </>
  )
}

function ViewButton({ view, selected, onSelect }: { view: ViewInfo; selected: boolean; onSelect(): void }): React.JSX.Element {
  const keyText = useKeyText()(viewShortcutId(view.id))
  return (
    <Tooltip>
      <TooltipTrigger asChild>
        <button
          onClick={onSelect}
          aria-label={view.label}
          aria-pressed={selected}
          className={cn('flex size-8 shrink-0 items-center justify-center rounded-lg', selected ? 'bg-primary [&_img]:brightness-0 [&_img]:invert' : 'hover:bg-selection')}
        >
          <IdeIcon name={VIEW_ICONS[view.id] ?? FALLBACK_ICON} className="size-[18px]" />
        </button>
      </TooltipTrigger>
      <TooltipContent side="right" className="flex max-w-64 flex-col items-start gap-0.5">
        <span className="flex items-center gap-2 font-medium">
          {view.label}
          {keyText !== '' && <Kbd className="uppercase">{keyText}</Kbd>}
        </span>
        <span className="text-secondary-text">{view.description}</span>
      </TooltipContent>
    </Tooltip>
  )
}

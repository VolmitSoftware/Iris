import { DropdownMenu } from 'radix-ui'
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

/** The icon a view has in the stripe, which its island's header repeats beside its name. */
export function viewIcon(viewId: string): IdeIconName {
  return VIEW_ICONS[viewId] ?? FALLBACK_ICON
}

interface ViewListProps {
  views: ViewInfo[]
  current: string
  onSelect(view: string): void
  /** Receives the element the shown view fills with its own lists, beside the views. */
  slotRef(element: HTMLElement | null): void
}

/**
 * The views the server offers, as a stripe of buttons on the window's edge in their groups with a list of their names
 * under them, and beside it the island the shown view fills with its own lists, which is not there for a view without
 * any (IntelliJ's tool window stripe).
 */
export function ViewList({ views, current, onSelect, slotRef }: ViewListProps): React.JSX.Element {
  const groups = groupsOf(views)
  return (
    <>
      <nav className="flex w-8 shrink-0 flex-col items-center gap-1.5 overflow-y-auto">
        {groups.map((group, index) => (
          <Fragment key={group.label}>
            {index > 0 && <StripeLine />}
            {group.views.map((view) => (
              <ViewButton key={view.id} view={view} selected={view.id === current} onSelect={() => onSelect(view.id)} />
            ))}
          </Fragment>
        ))}
        <StripeLine />
        <NamedViews groups={groups} current={current} onSelect={onSelect} />
      </nav>
      <div ref={slotRef} className="flex w-60 shrink-0 flex-col overflow-hidden rounded-lg bg-card empty:hidden" />
    </>
  )
}

interface ViewGroup {
  label: string
  views: ViewInfo[]
}

function groupsOf(views: ViewInfo[]): ViewGroup[] {
  const labels = [...new Set(views.map((view) => view.groupLabel))]
  return labels.map((label) => ({ label, views: views.filter((view) => view.groupLabel === label) }))
}

function StripeLine(): React.JSX.Element {
  return <span className="my-0.5 h-px w-5 shrink-0 bg-input" />
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
          <IdeIcon name={viewIcon(view.id)} className="size-[18px]" />
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

/** Every view by name, for when its icon alone does not say which it is (IntelliJ's "more tool windows"). */
function NamedViews({ groups, current, onSelect }: { groups: ViewGroup[]; current: string; onSelect(view: string): void }): React.JSX.Element {
  const keyText = useKeyText()
  return (
    <DropdownMenu.Root>
      <DropdownMenu.Trigger asChild>
        <button aria-label="All views" title="All views" className="flex size-8 shrink-0 items-center justify-center rounded-lg text-secondary-text hover:bg-selection data-[state=open]:bg-selection">
          <svg viewBox="0 0 16 16" className="size-4" fill="currentColor">
            <circle cx="3" cy="8" r="1.25" />
            <circle cx="8" cy="8" r="1.25" />
            <circle cx="13" cy="8" r="1.25" />
          </svg>
        </button>
      </DropdownMenu.Trigger>
      <DropdownMenu.Portal>
        <DropdownMenu.Content side="right" align="end" sideOffset={8} className="z-50 w-56 rounded-lg border border-overlay-border bg-popover p-1 text-popover-foreground shadow-lg">
          {groups.map((group) => (
            <DropdownMenu.Group key={group.label}>
              <DropdownMenu.Label className="px-2 pt-1.5 pb-1 text-[11px] font-semibold text-muted-foreground">{group.label}</DropdownMenu.Label>
              {group.views.map((view) => (
                <DropdownMenu.Item
                  key={view.id}
                  onSelect={() => onSelect(view.id)}
                  className={cn(
                    'flex h-[26px] cursor-default items-center gap-2 rounded-md px-2 outline-none data-[highlighted]:bg-accent',
                    view.id === current && 'bg-selection'
                  )}
                >
                  <IdeIcon name={viewIcon(view.id)} className="size-3.5" />
                  <span className="min-w-0 flex-1 truncate">{view.label}</span>
                  {keyText(viewShortcutId(view.id)) !== '' && (
                    <Kbd className="h-4 min-w-4 border border-input/60 bg-transparent text-[10px] uppercase">{keyText(viewShortcutId(view.id))}</Kbd>
                  )}
                </DropdownMenu.Item>
              ))}
            </DropdownMenu.Group>
          ))}
        </DropdownMenu.Content>
      </DropdownMenu.Portal>
    </DropdownMenu.Root>
  )
}

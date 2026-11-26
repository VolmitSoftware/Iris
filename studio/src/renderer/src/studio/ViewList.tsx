import { useState } from 'react'
import {
  AudioWaveform,
  Box,
  Castle,
  Boxes,
  Droplets,
  Fish,
  Flower2,
  Gem,
  HardDrive,
  ImageIcon,
  Layers,
  LayoutGrid,
  ListTree,
  Map,
  MountainSnow,
  Palette,
  Pickaxe,
  Pyramid,
  Shapes,
  Sprout,
  TreeDeciduous,
  TreePine,
  Trees,
  Waves,
  Workflow,
  Zap,
  type LucideIcon
} from 'lucide-react'
import { Kbd } from '@/components/ui/kbd'
import { ScrollArea } from '@/components/ui/scroll-area'
import { Tooltip, TooltipContent, TooltipTrigger } from '@/components/ui/tooltip'
import { cn } from '@/lib/utils'
import type { ViewInfo } from '../bridge/types'
import { viewShortcutId } from './shortcuts'
import { useKeyText } from './useBindings'

const VIEW_ICONS: Record<string, LucideIcon> = {
  biomes: Trees,
  regions: Map,
  rivers: Waves,
  height: MountainSnow,
  'land-biomes': TreeDeciduous,
  'sea-biomes': Fish,
  'cave-biomes': Gem,
  'object-load': Boxes,
  'decorator-load': Sprout,
  'layer-load': Layers,
  caveProfiles: Pickaxe,
  treeProfiles: TreePine,
  formationProfiles: Pyramid,
  fluidProfiles: Droplets,
  world3d: Box,
  savedWorld: HardDrive,
  'iris:terrain/palettes': Palette,
  'iris:decorant/decorators': Flower2,
  'iris:objects': Castle,
  'iris:objects/procedural': Workflow,
  noise: AudioWaveform,
  imageMaps: ImageIcon,
  pregen: Zap
}
const FALLBACK_ICON: LucideIcon = Shapes
/** Inset, because the stripe scrolls and so clips anything drawn outside its buttons. */
const STRIPE_BUTTON = 'flex size-[30px] shrink-0 items-center justify-center rounded-lg outline-none focus-visible:ring-2 focus-visible:ring-ring focus-visible:ring-inset'
const GROUP_LABEL = 'truncate px-2 pt-2 pb-1 text-[11px] font-semibold text-muted-foreground'
const OPEN_WINDOWS_KEY = 'studio.leftToolWindows'

/** The icon a view has on its tabs and in the list of views. */
export function ViewIcon({ viewId, className }: { viewId: string; className: string }): React.JSX.Element {
  const Icon = VIEW_ICONS[viewId] ?? FALLBACK_ICON
  return <Icon className={className} />
}

interface ViewListProps {
  views: ViewInfo[]
  /** The view of the focused tab. */
  current: string
  onOpen(view: string): void
  /** Opens a view in a tab of its own that the next view opened does not reuse. */
  onKeep(view: string): void
  /** Receives the element the shown view fills with its own lists, while the window holding them is open. */
  slotRef(element: HTMLElement | null): void
}

/**
 * The tool windows on the window's left edge, as IntelliJ has them: the list of every view, and under it the lists of
 * the view that is shown, each opened and closed from its icon on the stripe and both sharing the column when open.
 */
export function ViewList({ views, current, onOpen, onKeep, slotRef }: ViewListProps): React.JSX.Element {
  const [open, toggle] = useOpenWindows()
  const currentLabel = views.find((view) => view.id === current)?.label ?? current
  return (
    <>
      <nav aria-label="Tool windows" className="flex w-10 shrink-0 flex-col items-center gap-2.5 overflow-y-auto pt-1 pb-1.5">
        <WindowButton label="Views" Icon={LayoutGrid} open={open.views} onToggle={() => toggle('views')} />
        <span className="my-0.5 h-px w-5 shrink-0 bg-input" />
        <WindowButton label="Contents" Icon={ListTree} open={open.contents} onToggle={() => toggle('contents')} />
      </nav>
      <div className="flex w-60 shrink-0 flex-col overflow-hidden rounded-[10px] bg-card empty:hidden">
        {open.views && <ViewsWindow views={views} current={current} onOpen={onOpen} onKeep={onKeep} />}
        {open.contents && (
          <section
            aria-label="Contents"
            className={cn(
              'flex min-h-[50%] flex-1 flex-col has-[>div:empty]:min-h-0 has-[>div:empty]:flex-none',
              open.views && 'border-t'
            )}
          >
            <div ref={slotRef} className="peer flex min-h-0 flex-1 flex-col empty:hidden" />
            <p className="hidden px-4 py-2.5 text-[12px] text-muted-foreground peer-empty:block">Nothing to show for {currentLabel}</p>
          </section>
        )}
      </div>
    </>
  )
}

interface OpenWindows {
  views: boolean
  contents: boolean
}

/** Which tool windows are open, kept for the next time the studio opens; both are at first. */
function useOpenWindows(): [OpenWindows, (window: keyof OpenWindows) => void] {
  const [open, setOpen] = useState<OpenWindows>(() => ({ views: true, contents: true, ...storedOpenWindows() }))
  const toggle = (window: keyof OpenWindows): void => {
    const next = { ...open, [window]: !open[window] }
    localStorage.setItem(OPEN_WINDOWS_KEY, JSON.stringify(next))
    setOpen(next)
  }
  return [open, toggle]
}

function storedOpenWindows(): Partial<OpenWindows> {
  try {
    const stored: unknown = JSON.parse(localStorage.getItem(OPEN_WINDOWS_KEY) ?? '{}')
    return typeof stored === 'object' && stored !== null ? (stored as Partial<OpenWindows>) : {}
  } catch {
    return {}
  }
}

interface WindowButtonProps {
  label: string
  Icon: LucideIcon
  open: boolean
  onToggle(): void
}

/** A tool window's icon on the stripe, which opens it and closes it again. */
function WindowButton({ label, Icon, open, onToggle }: WindowButtonProps): React.JSX.Element {
  return (
    <Tooltip>
      <TooltipTrigger asChild>
        <button aria-label={label} aria-pressed={open} onClick={onToggle} className={cn(STRIPE_BUTTON, open ? 'bg-secondary' : 'hover:bg-accent')}>
          <Icon className="size-5" />
        </button>
      </TooltipTrigger>
      <TooltipContent side="right">{label}</TooltipContent>
    </Tooltip>
  )
}

interface ViewGroup {
  id: string
  label: string
  views: ViewInfo[]
}

function groupsOf(views: ViewInfo[]): ViewGroup[] {
  const ids = [...new Set(views.map((view) => view.group))]
  return ids.map((id) => {
    const members = views.filter((view) => view.group === id)
    return { id, label: members[0].groupLabel, views: members }
  })
}

/** Every view by group; a click shows one, a double click keeps its tab, as IntelliJ's project view opens files. */
function ViewsWindow({ views, current, onOpen, onKeep }: Omit<ViewListProps, 'slotRef'>): React.JSX.Element {
  const keyText = useKeyText()
  return (
    <section aria-label="Views" className="flex min-h-0 flex-1 flex-col">
      <h2 className="shrink-0 px-4 pt-2.5 pb-1 text-[13px] font-semibold">Views</h2>
      <ScrollArea className="min-h-0 flex-1">
        <div className="px-2 pb-1.5">
          {groupsOf(views).map((group) => (
            <div key={group.id} role="group" aria-label={group.label}>
              <h3 className={GROUP_LABEL}>{group.label}</h3>
              {group.views.map((view) => (
                <button
                  key={view.id}
                  title={view.description}
                  aria-current={view.id === current || undefined}
                  onClick={() => onOpen(view.id)}
                  onDoubleClick={() => onKeep(view.id)}
                  className={cn('flex h-[26px] w-full items-center gap-2 rounded-md px-2 text-left', view.id === current ? 'bg-selection' : 'hover:bg-accent')}
                >
                  <ViewIcon viewId={view.id} className="size-3.5 shrink-0" />
                  <span className="min-w-0 flex-1 truncate">{view.label}</span>
                  {keyText(viewShortcutId(view.id)) !== '' && (
                    <Kbd className="h-4 min-w-4 border border-input/60 bg-transparent text-[10px] uppercase">{keyText(viewShortcutId(view.id))}</Kbd>
                  )}
                </button>
              ))}
            </div>
          ))}
        </div>
      </ScrollArea>
    </section>
  )
}

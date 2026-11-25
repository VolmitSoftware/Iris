import { DropdownMenu } from 'radix-ui'
import { useState } from 'react'
import {
  AudioWaveform,
  Box,
  Boxes,
  Droplets,
  Fish,
  Flower2,
  Gauge,
  Gem,
  Globe,
  HardDrive,
  ImageIcon,
  Layers,
  Map,
  MountainSnow,
  Package,
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
  'iris:objects': Package,
  'iris:objects/procedural': Workflow,
  noise: AudioWaveform,
  imageMaps: ImageIcon,
  pregen: Zap
}
const GROUP_ICONS: Record<string, LucideIcon> = {
  WORLD: Globe,
  BIOME_LAYERS: Layers,
  DIAGNOSTICS: Gauge,
  PACK: AudioWaveform,
  BLOCKS: Boxes
}
const FALLBACK_ICON: LucideIcon = Shapes

/** The icon a view has on its tabs and in its group's list. */
export function ViewIcon({ viewId, className }: { viewId: string; className: string }): React.JSX.Element {
  const Icon = VIEW_ICONS[viewId] ?? FALLBACK_ICON
  return <Icon className={className} />
}

interface ViewListProps {
  views: ViewInfo[]
  /** The view of the focused tab. */
  current: string
  onSelect(view: string): void
  /** Receives the element the shown view fills with its own lists, beside the stripe. */
  slotRef(element: HTMLElement | null): void
}

/**
 * The views the server offers, as one button for each of their groups on the window's edge with the group's name under
 * it, each opening the list of its views; beside it the island the focused tab's view fills with its own lists, which
 * is not there for a view without any (IntelliJ's tool window stripe with its names shown).
 */
export function ViewList({ views, current, onSelect, slotRef }: ViewListProps): React.JSX.Element {
  return (
    <>
      <nav aria-label="Views" className="flex w-16 shrink-0 flex-col items-center gap-1 overflow-y-auto pt-1 pb-1.5">
        {groupsOf(views).map((group) => (
          <GroupButton key={group.id} group={group} current={current} onSelect={onSelect} />
        ))}
      </nav>
      <div ref={slotRef} className="flex w-60 shrink-0 flex-col overflow-hidden rounded-[10px] bg-card empty:hidden" />
    </>
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

function GroupButton({ group, current, onSelect }: { group: ViewGroup; current: string; onSelect(view: string): void }): React.JSX.Element {
  const keyText = useKeyText()
  const [open, setOpen] = useState(false)
  const Icon = GROUP_ICONS[group.id] ?? FALLBACK_ICON
  const holdsCurrent = group.views.some((view) => view.id === current)
  return (
    <DropdownMenu.Root open={open} onOpenChange={setOpen}>
      <DropdownMenu.Trigger asChild>
        <button
          aria-label={group.label}
          className={cn(
            'flex w-[60px] shrink-0 flex-col items-center gap-0.5 rounded-lg px-0.5 pt-1.5 pb-1 outline-none focus-visible:ring-2 focus-visible:ring-ring focus-visible:ring-inset',
            holdsCurrent || open ? 'bg-accent text-foreground' : 'text-secondary-text hover:bg-accent hover:text-foreground'
          )}
        >
          <Icon className="size-5" strokeWidth={1.6} />
          <span className="line-clamp-2 w-full text-center text-[10px] leading-[1.15]">{group.label}</span>
        </button>
      </DropdownMenu.Trigger>
      <DropdownMenu.Portal>
        <DropdownMenu.Content side="right" align="start" sideOffset={6} className="popup z-50 w-60 p-1 popup-motion">
          <DropdownMenu.Label className="px-2 pt-1 pb-1 text-[11px] font-semibold text-muted-foreground">{group.label}</DropdownMenu.Label>
          {group.views.map((view) => (
            <DropdownMenu.Item
              key={view.id}
              title={view.description}
              onSelect={() => onSelect(view.id)}
              className={cn(
                'flex h-[26px] cursor-default items-center gap-2 rounded-md px-2 outline-none data-[highlighted]:bg-accent',
                view.id === current && 'bg-selection'
              )}
            >
              <ViewIcon viewId={view.id} className="size-3.5" />
              <span className="min-w-0 flex-1 truncate">{view.label}</span>
              {keyText(viewShortcutId(view.id)) !== '' && (
                <Kbd className="h-4 min-w-4 border border-input/60 bg-transparent text-[10px] uppercase">{keyText(viewShortcutId(view.id))}</Kbd>
              )}
            </DropdownMenu.Item>
          ))}
        </DropdownMenu.Content>
      </DropdownMenu.Portal>
    </DropdownMenu.Root>
  )
}

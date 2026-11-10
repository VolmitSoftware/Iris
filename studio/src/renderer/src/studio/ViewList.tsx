import { AudioWaveform, Box, Gem, Globe, HardDrive, Layers, Map as MapIcon, Mountain, Shapes, Sprout, Trees, Waves, type LucideIcon } from 'lucide-react'
import { Kbd } from '@/components/ui/kbd'
import { cn } from '@/lib/utils'
import type { ViewInfo } from '../bridge/types'
import { viewShortcutId } from './shortcuts'
import { useKeyText } from './useBindings'

interface ViewLook {
  icon: LucideIcon
  /** The icon's colour, from v3e's view palette. */
  tint: string
}

const VIEW_LOOKS: Record<string, ViewLook> = {
  biomes: { icon: Trees, tint: 'text-[#5fb865]' },
  regions: { icon: MapIcon, tint: 'text-[#548af7]' },
  height: { icon: Mountain, tint: 'text-[#c08552]' },
  world3d: { icon: Globe, tint: 'text-[#4fb6c6]' },
  savedWorld: { icon: HardDrive, tint: 'text-[#8c9099]' },
  landBiomes: { icon: Sprout, tint: 'text-[#a3b84f]' },
  seaBiomes: { icon: Waves, tint: 'text-[#4fb6c6]' },
  caveBiomes: { icon: Gem, tint: 'text-[#9c7bd6]' },
  'iris:terrain/palettes': { icon: Layers, tint: 'text-[#d9a23f]' },
  'iris:objects': { icon: Box, tint: 'text-[#548af7]' },
  'iris:objects/procedural': { icon: Shapes, tint: 'text-[#d8709a]' },
  noise: { icon: AudioWaveform, tint: 'text-[#f75464]' }
}
const FALLBACK_LOOK: ViewLook = { icon: MapIcon, tint: 'text-secondary-text' }

interface ViewListProps {
  views: ViewInfo[]
  current: string
  onSelect(view: string): void
  /** Receives the element the shown view fills with its own lists, under the views. */
  slotRef(element: HTMLElement | null): void
}

/** The left column: the views the server offers in their groups, then the shown view's own lists. */
export function ViewList({ views, current, onSelect, slotRef }: ViewListProps): React.JSX.Element {
  const groups = [...new Set(views.map((view) => view.groupLabel))]
  return (
    <div className="flex w-60 shrink-0 flex-col gap-1.5">
      <nav className="flex shrink-0 flex-col gap-2 overflow-y-auto rounded-lg bg-card p-1.5">
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
      <div ref={slotRef} className="flex min-h-0 flex-1 flex-col gap-1.5 empty:hidden" />
    </div>
  )
}

function ViewRow({ view, selected, onSelect }: { view: ViewInfo; selected: boolean; onSelect(): void }): React.JSX.Element {
  const { icon: Icon, tint } = VIEW_LOOKS[view.id] ?? FALLBACK_LOOK
  const keyText = useKeyText()(viewShortcutId(view.id))
  return (
    <button
      onClick={onSelect}
      title={view.description}
      className={cn('flex h-[26px] w-full items-center gap-2 rounded-md px-2 text-left', selected ? 'bg-selection' : 'hover:bg-accent')}
    >
      <Icon className={cn('size-3.5 shrink-0', tint)} />
      <span className="min-w-0 flex-1 truncate">{view.label}</span>
      {keyText !== '' && <Kbd className="h-4 min-w-4 border border-input/60 bg-transparent text-[10px] uppercase">{keyText}</Kbd>}
    </button>
  )
}

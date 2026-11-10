import { IdeIcon } from '@/components/IdeIcon'
import { useState } from 'react'
import { ScrollArea } from '@/components/ui/scroll-area'
import { ToggleGroup, ToggleGroupItem } from '@/components/ui/toggle-group'
import type { PickDisplay, PickInspection, PickSection } from '../bridge/types'
import { hexColor } from './colors'
import { NoiseChart, percent } from './NoiseChart'

// The chosen display reads like the toolbar's switched-on toggles.
const DISPLAY_ITEM = 'h-7 text-[12px] data-[state=on]:bg-selection data-[state=on]:text-foreground'

/** How the map draws the picks, for a view that recolours its tiles. */
export interface PickDisplayChoice {
  /** What the map shows when it draws the picks: "Regions" or "Biomes". */
  picksLabel: string
  value: PickDisplay
  onChange(display: PickDisplay): void
}

interface PickInspectorProps {
  /** What is inspected: "Regions", "Biomes", or the previewed region's name. */
  title: string
  /** The latest measurement of the view; null until the first one arrives. */
  inspection: PickInspection | null
  /** Absent in a view that draws no map tiles, such as the 3D world. */
  display?: PickDisplayChoice
}

/**
 * How noise picks the regions or biomes in view, measured against what is in view, with a switch for how the map
 * draws the picks (v3e PickInspectorPanel).
 */
export function PickInspector({ title, inspection, display }: PickInspectorProps): React.JSX.Element {
  return (
    <section className="flex min-h-0 flex-1 flex-col">
      <header className="px-4 pt-2.5 pb-2.5">
        <h2 className="truncate text-[13px] font-semibold">{title}</h2>
        <p className="truncate text-[11.5px] text-secondary-text">{inspection?.detail ?? 'Measuring what is in view…'}</p>
        {display !== undefined && (
          <ToggleGroup
            type="single"
            variant="outline"
            size="sm"
            className="mt-2 w-full"
            value={display.value}
            onValueChange={(value) => value !== '' && display.onChange(value as PickDisplay)}
          >
            <ToggleGroupItem value="picks" className={DISPLAY_ITEM}>
              {display.picksLabel}
            </ToggleGroupItem>
            <ToggleGroupItem value="noise" className={DISPLAY_ITEM}>
              Noise
            </ToggleGroupItem>
            <ToggleGroupItem value="both" className={DISPLAY_ITEM}>
              Both
            </ToggleGroupItem>
          </ToggleGroup>
        )}
      </header>
      <ScrollArea className="min-h-0 flex-1 border-t">
        <div className="px-4 pb-2">{inspection !== null && <Sections sections={inspection.sections} />}</div>
      </ScrollArea>
    </section>
  )
}

// A single section is the whole view, so its charts stand on their own; several are one region each, folded.
function Sections({ sections }: { sections: PickSection[] }): React.JSX.Element {
  const [opened, setOpened] = useState<Set<string> | null>(null)
  if (sections.length === 1) {
    return <Charts section={sections[0]} />
  }
  const open = opened ?? new Set(sections.slice(0, 1).map(sectionKey))
  const toggle = (key: string): void => {
    const next = new Set(open)
    if (!next.delete(key)) {
      next.add(key)
    }
    setOpened(next)
  }
  return (
    <>
      {sections.map((section) => (
        <RegionSection key={sectionKey(section)} section={section} open={open.has(sectionKey(section))} onToggle={() => toggle(sectionKey(section))} />
      ))}
    </>
  )
}

function RegionSection({ section, open, onToggle }: { section: PickSection; open: boolean; onToggle(): void }): React.JSX.Element {
  const region = section.region
  return (
    <div className="border-b last:border-b-0">
      <button className="-mx-1.5 flex w-[calc(100%+12px)] items-center gap-2 rounded-md px-1.5 py-2 text-left hover:bg-accent" onClick={onToggle}>
        <IdeIcon name="chevronRight" className={`size-3.5 transition-transform ${open ? 'rotate-90' : ''}`} />
        {region !== null && <span className="size-2.5 shrink-0 rounded-[3px]" style={{ backgroundColor: hexColor(region.color) }} />}
        <span className="min-w-0 flex-1 truncate font-medium">{region?.name ?? 'Regions'}</span>
        <span className="shrink-0 text-[11.5px] text-secondary-text tabular-nums">
          {percent(section.share)} · {section.biomeCount === 1 ? '1 biome' : `${section.biomeCount} biomes`}
        </span>
      </button>
      {open && <Charts section={section} />}
    </div>
  )
}

function Charts({ section }: { section: PickSection }): React.JSX.Element {
  const optionNoun = section.region === null ? 'Regions' : 'Biomes'
  return (
    <div className="divide-y">
      {section.spreads.map((spread) => (
        <NoiseChart key={spread.title} spread={spread} optionNoun={optionNoun} />
      ))}
    </div>
  )
}

function sectionKey(section: PickSection): string {
  return section.region?.key ?? ''
}

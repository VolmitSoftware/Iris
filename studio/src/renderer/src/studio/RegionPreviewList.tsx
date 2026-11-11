import { useState } from 'react'
import { Input } from '@/components/ui/input'
import { cn } from '@/lib/utils'
import type { MapLabel } from '../bridge/types'
import { hexColor } from './colors'

// Short lists are scanned at a glance; longer ones get a search field.
const SEARCHABLE_FROM_REGIONS = 8

interface RegionPreviewListProps {
  regions: MapLabel[]
  /** The previewed region's key, or null for all regions. */
  current: string | null
  onSelect(regionKey: string | null): void
}

/** The region view's regions, any of which can fill the map with its biomes, or all of them as they are (v3e RegionNavigator). */
export function RegionPreviewList({ regions, current, onSelect }: RegionPreviewListProps): React.JSX.Element {
  const [search, setSearch] = useState('')
  const wanted = search.trim().toLowerCase()
  const shown = wanted === '' ? regions : regions.filter((region) => `${region.name} ${region.key}`.toLowerCase().includes(wanted))
  return (
    <section>
      <h2 className="px-2 pt-1 pb-2 text-[13px] font-semibold">Preview a region</h2>
      {regions.length >= SEARCHABLE_FROM_REGIONS && (
        <Input
          className="mb-1 h-7 text-[12px]"
          placeholder="Find a region"
          value={search}
          onChange={(event) => setSearch(event.target.value)}
          onKeyDown={(event) => event.key === 'Escape' && setSearch('')}
        />
      )}
      <RegionRow name="All regions" color={null} selected={current === null} onSelect={() => onSelect(null)} />
      {shown.map((region) => (
        <RegionRow
          key={region.key}
          name={region.name}
          color={region.color}
          title={region.key}
          selected={region.key === current}
          onSelect={() => onSelect(region.key)}
        />
      ))}
      {shown.length === 0 && <p className="px-2 py-1 text-[12px] text-muted-foreground">No region matches</p>}
    </section>
  )
}

interface RegionRowProps {
  name: string
  color: number | null
  title?: string
  selected: boolean
  onSelect(): void
}

function RegionRow({ name, color, title, selected, onSelect }: RegionRowProps): React.JSX.Element {
  return (
    <button
      onClick={onSelect}
      title={title}
      className={cn('flex h-[26px] w-full items-center gap-2 rounded-md px-2 text-left', selected ? 'bg-selection' : 'hover:bg-accent')}
    >
      <span
        className={cn('size-3 shrink-0 rounded-[3px]', color === null && 'border border-secondary-text/60')}
        style={color === null ? undefined : { backgroundColor: hexColor(color) }}
      />
      <span className="truncate">{name}</span>
    </button>
  )
}

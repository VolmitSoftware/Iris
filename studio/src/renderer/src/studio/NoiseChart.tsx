import { useState } from 'react'
import { cn } from '@/lib/utils'
import type { PickShare, PickSpread } from '../bridge/types'
import { hexColor } from './colors'

// A long table keeps the options with the most ground in view until asked for all.
const SHORT_TABLE_ROWS = 6
// An option getting under half or over twice its share of the noise stands out.
const FAR_FROM_WEIGHT_FACTOR = 2

interface NoiseChartProps {
  spread: PickSpread
  /** What the noise picks, as the table's heading: "Regions" or "Biomes". */
  optionNoun: string
}

/**
 * One noise that picks regions or biomes: how its values in view spread over the bands that pick each option, and a
 * table of each option's share of the noise against its share of the ground in view (v3e NoisePickChart).
 */
export function NoiseChart({ spread, optionNoun }: NoiseChartProps): React.JSX.Element {
  if (spread.rows.length === 1) {
    return (
      <section className="py-2.5">
        <ChartTitle>{spread.title}</ChartTitle>
        <div className="mt-1.5 flex items-center gap-2 text-[12px]">
          <OptionName share={spread.rows[0]} rows={spread.rows} />
          <span className="shrink-0 text-secondary-text">Picked everywhere</span>
        </div>
      </section>
    )
  }
  return (
    <section className="py-2.5">
      <ChartTitle>{spread.title}</ChartTitle>
      <Spread spread={spread} />
      <ShareTable rows={spread.rows} optionNoun={optionNoun} />
    </section>
  )
}

function ChartTitle({ children }: { children: React.ReactNode }): React.JSX.Element {
  return <h4 className="truncate text-[11px] font-semibold text-muted-foreground">{children}</h4>
}

function Spread({ spread }: { spread: PickSpread }): React.JSX.Element {
  const peak = Math.max(1, ...spread.histogram)
  return (
    <div className="mt-1.5">
      <div className="relative flex h-12 items-end gap-px">
        {spread.histogram.map((count, bin) => (
          <span key={bin} className="flex-1 rounded-t-[1px] bg-secondary-text/45" style={{ height: `${(count / peak) * 100}%` }} />
        ))}
        {spread.bands.slice(0, -1).map((band, index) => (
          <span key={index} className="absolute inset-y-0 w-px bg-foreground/20" style={{ left: `${band.to * 100}%` }} />
        ))}
      </div>
      <div className="mt-0.5 flex h-2.5 overflow-hidden rounded-[2px]">
        {spread.bands.map((band, index) => (
          <span
            key={index}
            title={band.option.name}
            className="h-full"
            style={{ flexGrow: band.to - band.from, flexBasis: 0, backgroundColor: hexColor(band.option.color) }}
          />
        ))}
      </div>
      <div className="mt-0.5 flex justify-between font-mono text-[10.5px] text-secondary-text">
        <span>0</span>
        <span>0.5</span>
        <span>1</span>
      </div>
    </div>
  )
}

function ShareTable({ rows, optionNoun }: { rows: PickShare[]; optionNoun: string }): React.JSX.Element {
  const [showsAll, setShowsAll] = useState(false)
  const long = rows.length > SHORT_TABLE_ROWS
  const shown = showsAll || !long ? rows : largestInView(rows)
  return (
    <div className="mt-2 grid grid-cols-[1fr_auto_auto] items-center gap-x-2.5 gap-y-0.5 text-[12px]">
      <span className="text-[11px] font-semibold text-muted-foreground">{optionNoun}</span>
      <span className="text-right text-[11px] font-semibold text-muted-foreground">Weight</span>
      <span className="text-right text-[11px] font-semibold text-muted-foreground">In view</span>
      {shown.map((share) => (
        <ShareRow key={share.option.key} share={share} rows={rows} />
      ))}
      {long && (
        <button className="col-span-3 justify-self-start py-0.5 text-link hover:underline" onClick={() => setShowsAll(!showsAll)}>
          {showsAll ? 'Show fewer' : `Show all ${rows.length} ${optionNoun.toLowerCase()}`}
        </button>
      )}
    </div>
  )
}

function ShareRow({ share, rows }: { share: PickShare; rows: PickShare[] }): React.JSX.Element {
  const farFromWeight = share.inView * FAR_FROM_WEIGHT_FACTOR < share.weight || share.inView > share.weight * FAR_FROM_WEIGHT_FACTOR
  return (
    <>
      <OptionName share={share} rows={rows} />
      <span className="text-right text-secondary-text tabular-nums">{percent(share.weight)}</span>
      <span className={cn('text-right tabular-nums', farFromWeight && 'text-warning')}>{percent(share.inView)}</span>
    </>
  )
}

// Two biomes in different folders can share a name, so their keys tell them apart.
function OptionName({ share, rows }: { share: PickShare; rows: PickShare[] }): React.JSX.Element {
  const shared = rows.some((other) => other !== share && other.option.name === share.option.name)
  return (
    <span className="flex min-w-0 flex-1 items-center gap-1.5" title={share.option.key}>
      <span className="size-2.5 shrink-0 rounded-[3px]" style={{ backgroundColor: hexColor(share.option.color) }} />
      <span className="truncate">{shared ? `${share.option.name} (${share.option.key})` : share.option.name}</span>
    </span>
  )
}

/** The rows with the most ground in view, still in the order the noise meets them. */
export function largestInView(rows: PickShare[]): PickShare[] {
  const largest = new Set([...rows].sort((first, second) => second.inView - first.inView).slice(0, SHORT_TABLE_ROWS))
  return rows.filter((row) => largest.has(row))
}

export function percent(fraction: number): string {
  if (fraction > 0 && fraction < 0.001) {
    return '<0.1%'
  }
  return `${(fraction * 100).toFixed(1)}%`
}

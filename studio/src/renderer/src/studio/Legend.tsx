import { ScrollArea } from '@/components/ui/scroll-area'
import { cn } from '@/lib/utils'
import type { Share } from '../map/census'
import type { Highlight } from '../map/tileStyle'
import { hexColor } from './colors'

const ROW = 'flex h-[26px] w-full items-center gap-2 rounded-md px-2.5 text-left'

/** What highlighting does in a view that can highlight. */
export interface LegendSelection {
  highlight: Highlight | null
  onSelect(subject: Highlight): void
}

interface LegendProps {
  title: string
  shares: Share[]
  /** Absent in a view with nothing to highlight, such as the 3D world, where the legend only lists. */
  selection?: LegendSelection
  /** What to say while there is nothing to list. */
  emptyText?: string
  className?: string
}

/** What is on screen and how much of it each covers; clicking one highlights it on the map. */
export function Legend({ title, shares, selection, emptyText = 'Drawing the map…', className }: LegendProps): React.JSX.Element {
  const sharedNames = namesUsedMoreThanOnce(shares)
  return (
    <section className={cn('flex min-h-0 flex-col', className)}>
      <h2 className="px-4 pt-2.5 pb-1.5 text-[11px] font-semibold text-muted-foreground">{title}</h2>
      <ScrollArea className="min-h-0 flex-1">
        <ul className="px-1.5 pb-1">
          {shares.length === 0 && <li className="px-2.5 py-1 text-muted-foreground">{emptyText}</li>}
          {shares.map((share) => {
            const row = (
              <>
                <span className="size-3 shrink-0 rounded-[3px]" style={{ backgroundColor: hexColor(share.color) }} />
                <span className="flex min-w-0 flex-1 items-baseline gap-1.5">
                  <span className={cn('truncate', sharedNames.has(share.subject.name) && 'max-w-[60%] shrink-0')}>{share.subject.name}</span>
                  {sharedNames.has(share.subject.name) && (
                    <span className="min-w-0 truncate text-[11.5px] text-secondary-text">{fileName(share.subject.key)}</span>
                  )}
                </span>
                <span className="w-[42px] shrink-0 text-right text-[11.5px] text-secondary-text tabular-nums">{percent(share.fraction)}</span>
              </>
            )
            const selected = selection?.highlight != null && selection.highlight.kind === share.subject.kind && selection.highlight.key === share.subject.key
            return (
              <li key={`${share.subject.kind}:${share.subject.key}`}>
                {selection === undefined ? (
                  <div className={ROW}>{row}</div>
                ) : (
                  <button
                    onClick={() => selection.onSelect(share.subject)}
                    title={share.subject.key}
                    className={cn(ROW, selected ? 'bg-selection' : 'hover:bg-accent')}
                  >
                    {row}
                  </button>
                )}
              </li>
            )
          })}
        </ul>
      </ScrollArea>
      {selection !== undefined && <p className="px-4 pt-1.5 pb-2 text-[11px] text-muted-foreground">Click to highlight · Esc clears</p>}
    </section>
  )
}

function namesUsedMoreThanOnce(shares: Share[]): Set<string> {
  const seen = new Set<string>()
  const repeated = new Set<string>()
  for (const share of shares) {
    if (seen.has(share.subject.name)) {
      repeated.add(share.subject.name)
    }
    seen.add(share.subject.name)
  }
  return repeated
}

function fileName(key: string): string {
  return key.slice(key.lastIndexOf('/') + 1)
}

function percent(fraction: number): string {
  if (fraction < 0.001) {
    return '<0.1%'
  }
  return fraction < 0.1 ? `${(fraction * 100).toFixed(1)}%` : `${Math.round(fraction * 100)}%`
}

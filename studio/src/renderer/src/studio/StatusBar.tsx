import { MemoryStick, Zap } from 'lucide-react'
import { Tooltip, TooltipContent, TooltipTrigger } from '@/components/ui/tooltip'
import { cn } from '@/lib/utils'
import type { ServerUsage } from '../bridge/types'
import type { MapStatus } from '../map/MapController'
import { cpuIsHigh, formatCpu, formatHeap, heapIsHigh } from './serverUsage'

interface StatusBarProps {
  viewLabel: string
  status: MapStatus | null
  notice: string | null
  usage: ServerUsage | null
}

const NUMBER = new Intl.NumberFormat('en-US')

/** What the map shows, what it is busy with, and where the cursor is (v3e StudioStatusBar). */
export function StatusBar({ viewLabel, status, notice, usage }: StatusBarProps): React.JSX.Element {
  const drawing = status !== null && status.tilesReady < status.tilesTotal
  return (
    <footer className="flex h-6 shrink-0 items-center gap-6 border-t bg-card px-3 text-[11.5px] text-secondary-text tabular-nums">
      <span className="truncate">
        {viewLabel}
        {status !== null && (
          <>
            <Gap />
            {formatScale(status.blocksPerPixel)} blocks/px
            <Gap />
            {NUMBER.format(status.widthBlocks)} × {NUMBER.format(status.heightBlocks)} blocks
          </>
        )}
      </span>
      <span className="flex min-w-0 flex-1 items-center justify-center gap-2 truncate">
        {notice ?? (drawing ? `Drawing ${status.tilesReady} of ${status.tilesTotal} tiles` : '')}
        {notice === null && drawing && (
          <span className="h-1 w-24 overflow-hidden rounded-full bg-secondary">
            <span className="block h-full bg-primary" style={{ width: `${(status.tilesReady / status.tilesTotal) * 100}%` }} />
          </span>
        )}
      </span>
      {usage !== null && <UsageReadout usage={usage} />}
      {status !== null && (
        <span>
          X {NUMBER.format(status.cursorX)}
          <Gap />Z {NUMBER.format(status.cursorZ)}
        </span>
      )}
    </footer>
  )
}

/** The server's CPU, 100% per busy thread, and Java heap; each turns warning-coloured at 80%. */
function UsageReadout({ usage }: { usage: ServerUsage }): React.JSX.Element {
  return (
    <span className="flex items-center gap-4">
      <Reading
        icon={<Zap className="size-3.5" />}
        value={formatCpu(usage)}
        high={cpuIsHigh(usage)}
        hint={`Server CPU, 100% per busy thread, ${usage.threads} threads in all`}
      />
      <Reading icon={<MemoryStick className="size-3.5" />} value={formatHeap(usage)} high={heapIsHigh(usage)} hint="Java memory the server uses, of its maximum" />
    </span>
  )
}

interface ReadingProps {
  icon: React.ReactNode
  value: string
  high: boolean
  hint: string
}

function Reading({ icon, value, high, hint }: ReadingProps): React.JSX.Element {
  return (
    <Tooltip>
      <TooltipTrigger asChild>
        <span className={cn('flex items-center gap-1', high && 'text-warning')}>
          {icon}
          {value}
        </span>
      </TooltipTrigger>
      <TooltipContent>{hint}</TooltipContent>
    </Tooltip>
  )
}

function Gap(): React.JSX.Element {
  return <span className="inline-block w-3" />
}

function formatScale(blocksPerPixel: number): string {
  if (blocksPerPixel >= 10) {
    return String(Math.round(blocksPerPixel))
  }
  return blocksPerPixel >= 1 ? blocksPerPixel.toFixed(1) : blocksPerPixel.toFixed(2)
}

import type { MapStatus } from '../map/MapController'

interface StatusBarProps {
  viewLabel: string
  status: MapStatus | null
  notice: string | null
}

const NUMBER = new Intl.NumberFormat('en-US')

/** What the map shows, what it is busy with, and where the cursor is (v3e StudioStatusBar). */
export function StatusBar({ viewLabel, status, notice }: StatusBarProps): React.JSX.Element {
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
      {status !== null && (
        <span>
          X {NUMBER.format(status.cursorX)}
          <Gap />Z {NUMBER.format(status.cursorZ)}
        </span>
      )}
    </footer>
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

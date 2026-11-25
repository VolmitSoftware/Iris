import { useEffect, useState } from 'react'
import { Button } from '@/components/ui/button'
import type { BridgeClient } from '../bridge/BridgeClient'
import type { PregenSnapshot } from '../bridge/types'
import { Panel, ViewIsland } from './Sidebar'

const POLL_MILLIS = 1000
const MILLIS_PER_SECOND = 1000
const SECONDS_PER_MINUTE = 60
const MINUTES_PER_HOUR = 60
const PERCENT = 100
const NUMBER = new Intl.NumberFormat('en-US')
const RATE = new Intl.NumberFormat('en-US', { maximumFractionDigits: 1 })
const PHASE_LABELS = {
  INITIALIZING: 'Starting',
  GENERATING: 'Generating',
  PAUSED: 'Paused',
  SAVING: 'Saving',
  STOPPING: 'Stopping',
  COMPLETED: 'Done',
  ERROR: 'Failed'
} as const

interface PregenStudioProps {
  title: string
  client: BridgeClient
  onSummary(summary: string | null): void
  tell(message: string): void
}

/** The running pregeneration: its chunks as they are generated in the middle, its progress on the right. */
export function PregenStudio({ title, client, onSummary, tell }: PregenStudioProps): React.JSX.Element {
  const { snapshot, picture } = usePregen(client, tell)

  useEffect(() => onSummary(snapshot === null ? null : `${PHASE_LABELS[snapshot.phase]} · ${snapshot.progress.worldName}`), [snapshot, onSummary])
  useEffect(() => () => onSummary(null), [onSummary])

  return (
    <>
      <ViewIsland title={title}>
        <div className="absolute inset-0 flex items-center justify-center bg-map-background p-4">
          {picture !== null && <img src={picture} alt="Chunks of the pregeneration" className="max-h-full max-w-full object-contain [image-rendering:pixelated]" />}
          {snapshot === null && <p className="text-[12px] text-secondary-text">No pregeneration is running. Start one with /iris pregen start.</p>}
        </div>
      </ViewIsland>
      <Panel className="w-[250px]">{snapshot !== null && <Progress snapshot={snapshot} onPause={() => void client.pregenPause().catch((failure: Error) => tell(failure.message))} />}</Panel>
    </>
  )
}

function Progress({ snapshot, onPause }: { snapshot: PregenSnapshot; onPause(): void }): React.JSX.Element {
  const { progress } = snapshot
  const canPause = snapshot.phase === 'GENERATING' || snapshot.phase === 'PAUSED'
  return (
    <section className="flex flex-col gap-2 px-4 py-3 text-[12px]">
      <h2 className="text-[11px] font-semibold text-muted-foreground">{PHASE_LABELS[snapshot.phase]}</h2>
      <div className="h-1.5 overflow-hidden rounded-full bg-muted">
        <div className="h-full bg-primary meter-motion" style={{ width: `${Math.min(PERCENT, progress.percent)}%` }} />
      </div>
      <Row label="Done" value={`${RATE.format(progress.percent)}%`} />
      <Row label="Chunks" value={`${NUMBER.format(progress.generated)} of ${NUMBER.format(progress.totalChunks)}`} />
      <Row label="Remaining" value={NUMBER.format(progress.chunksRemaining)} />
      <Row label="In flight" value={NUMBER.format(progress.chunksInFlight)} />
      <Row label="Speed" value={`${RATE.format(progress.chunksPerSecond)} chunks/s`} />
      <Row label="Average" value={`${RATE.format(progress.overallChunksPerSecond)} chunks/s`} />
      <Row label="Elapsed" value={duration(progress.elapsed)} />
      <Row label="Left" value={progress.eta < 0 ? 'unknown' : duration(progress.eta)} />
      <Row label="Method" value={progress.method} />
      {progress.failed > 0 && <Row label="Failed" value={NUMBER.format(progress.failed)} />}
      {snapshot.failure !== null && <p className="text-destructive">{snapshot.failure}</p>}
      {canPause && (
        <Button variant="outline" size="sm" onClick={onPause}>
          {snapshot.phase === 'PAUSED' ? 'Resume' : 'Pause'}
        </Button>
      )}
    </section>
  )
}

function Row({ label, value }: { label: string; value: string }): React.JSX.Element {
  return (
    <div className="flex justify-between gap-2">
      <span className="text-secondary-text">{label}</span>
      <span className="truncate tabular-nums">{value}</span>
    </div>
  )
}

function duration(millis: number): string {
  const totalSeconds = Math.max(0, Math.round(millis / MILLIS_PER_SECOND))
  const hours = Math.floor(totalSeconds / (SECONDS_PER_MINUTE * MINUTES_PER_HOUR))
  const minutes = Math.floor(totalSeconds / SECONDS_PER_MINUTE) % MINUTES_PER_HOUR
  const seconds = totalSeconds % SECONDS_PER_MINUTE
  return hours > 0 ? `${hours}h ${minutes}m` : minutes > 0 ? `${minutes}m ${seconds}s` : `${seconds}s`
}

/** The pregeneration's status and map, read again every second while the view is shown. */
function usePregen(client: BridgeClient, tell: (message: string) => void): { snapshot: PregenSnapshot | null; picture: string | null } {
  const [snapshot, setSnapshot] = useState<PregenSnapshot | null>(null)
  const [picture, setPicture] = useState<string | null>(null)
  useEffect(() => {
    const stop = new AbortController()
    let url: string | null = null
    const show = (next: string | null): void => {
      if (url !== null) {
        URL.revokeObjectURL(url)
      }
      url = next
      setPicture(next)
    }
    const refresh = async (): Promise<void> => {
      try {
        const status = await client.pregen(stop.signal)
        setSnapshot(status.snapshot)
        show(status.active ? URL.createObjectURL(await client.pregenMap(stop.signal)) : null)
      } catch (failure) {
        if (!stop.signal.aborted) {
          tell(failure instanceof Error ? failure.message : String(failure))
        }
      }
    }
    void refresh()
    const timer = window.setInterval(() => void refresh(), POLL_MILLIS)
    return () => {
      stop.abort()
      window.clearInterval(timer)
      show(null)
    }
  }, [client, tell])
  return { snapshot, picture }
}

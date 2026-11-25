import { useEffect, useState } from 'react'
import type { BridgeClient } from '../bridge/BridgeClient'
import type { ImageMapDetails, SceneEntry } from '../bridge/types'
import { EntryBrowser } from './EntryBrowser'
import { Panel, SidebarPanel, ViewIsland } from './Sidebar'

const PREVIEW_PIXELS = 1024
const NUMBER = new Intl.NumberFormat('en-US')

interface ImageMapStudioProps {
  title: string
  client: BridgeClient
  /** Changes when the pack reloads, so the maps are read again. */
  packRevision: number
  onSummary(summary: string | null): void
  tell(message: string): void
}

/** The pack's image maps: the list on the left, the chosen one painted over the world area it covers in the middle, what it is on the right. */
export function ImageMapStudio({ title, client, packRevision, onSummary, tell }: ImageMapStudioProps): React.JSX.Element {
  const entries = useImageMapEntries(client, packRevision, tell)
  const [chosen, setChosen] = useState<string | null>(null)
  const entry = entries.find((candidate) => candidate.key === chosen) ?? entries[0] ?? null
  const { details, picture } = useImageMap(client, entry?.key ?? null, packRevision, tell)

  useEffect(() => onSummary(entry === null ? null : entry.key), [entry, onSummary])
  useEffect(() => () => onSummary(null), [onSummary])

  return (
    <>
      <ViewIsland title={title} subtitle={entry?.name}>
        <div className="absolute inset-0 flex items-center justify-center overflow-auto bg-map-background p-4">
          {picture !== null && <img src={picture} alt={entry?.name ?? title} className="max-h-full max-w-full object-contain [image-rendering:pixelated]" />}
          {entry === null && <p className="text-[12px] text-secondary-text">This pack has no image maps. Put them in the pack's imageMaps folder.</p>}
        </div>
      </ViewIsland>
      <SidebarPanel className="flex-1">
        <EntryBrowser className="flex-1" title={title} entries={entries} current={entry?.key ?? null} onSelect={setChosen} />
      </SidebarPanel>
      <Panel className="w-[250px]">{details !== null && <Facts details={details} />}</Panel>
    </>
  )
}

function Facts({ details }: { details: ImageMapDetails }): React.JSX.Element {
  const [minimumX, minimumZ, maximumX, maximumZ] = details.bounds
  return (
    <section className="px-4 pt-2.5 pb-2.5">
      <h2 className="truncate text-[11px] font-semibold text-muted-foreground" title={details.key}>
        {details.type}
      </h2>
      <p className="pt-1 text-[12px] leading-snug text-secondary-text">
        Covers X {NUMBER.format(Math.round(minimumX))} to {NUMBER.format(Math.round(maximumX))}, Z {NUMBER.format(Math.round(minimumZ))} to {NUMBER.format(Math.round(maximumZ))}
      </p>
      {details.facts.map((fact) => (
        <p key={fact} className="pt-1 text-[12px] leading-snug text-secondary-text">
          {fact}
        </p>
      ))}
    </section>
  )
}

function useImageMapEntries(client: BridgeClient, packRevision: number, tell: (message: string) => void): SceneEntry[] {
  const [entries, setEntries] = useState<SceneEntry[]>([])
  useEffect(() => {
    let active = true
    client
      .imageMaps()
      .then((found) => active && setEntries(found))
      .catch((failure: Error) => active && tell(failure.message))
    return () => {
      active = false
    }
  }, [client, packRevision, tell])
  return entries
}

function useImageMap(client: BridgeClient, key: string | null, packRevision: number, tell: (message: string) => void): { details: ImageMapDetails | null; picture: string | null } {
  const [details, setDetails] = useState<ImageMapDetails | null>(null)
  const [picture, setPicture] = useState<string | null>(null)
  useEffect(() => {
    setDetails(null)
    setPicture(null)
    if (key === null) {
      return
    }
    const stop = new AbortController()
    let url: string | null = null
    client
      .imageMap(key, stop.signal)
      .then(setDetails)
      .catch((failure: Error) => !stop.signal.aborted && tell(failure.message))
    client
      .imageMapPreview(key, PREVIEW_PIXELS, stop.signal)
      .then((blob) => {
        url = URL.createObjectURL(blob)
        setPicture(url)
      })
      .catch((failure: Error) => !stop.signal.aborted && tell(failure.message))
    return () => {
      stop.abort()
      if (url !== null) {
        URL.revokeObjectURL(url)
      }
    }
  }, [client, key, packRevision, tell])
  return { details, picture }
}

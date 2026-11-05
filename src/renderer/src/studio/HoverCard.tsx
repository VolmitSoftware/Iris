import { useLayoutEffect, useRef, useState } from 'react'
import { Kbd } from '@/components/ui/kbd'
import type { MapHover, MapLabel } from '../bridge/types'
import { hexColor } from './colors'
import type { CanvasPoint } from './MapCanvas'
import { PickRows, replacedParent } from './PickRows'
import { shortcutFor } from './shortcuts'

interface HoverCardProps {
  hover: MapHover
  point: CanvasPoint
  viewId: string
  detailed: boolean
  minY: number
}

const CURSOR_GAP_PIXELS = 18
const EDGE_MARGIN_PIXELS = 8

interface Row {
  label: string
  value: string
  mono?: boolean
}

/** The spot under the cursor: its biome and ground, and with Shift held, the rest of what the server knows. */
export function HoverCard({ hover, point, viewId, detailed, minY }: HoverCardProps): React.JSX.Element {
  const card = useRef<HTMLDivElement>(null)
  const [position, setPosition] = useState({ left: point.x + CURSOR_GAP_PIXELS, top: point.y + CURSOR_GAP_PIXELS })

  useLayoutEffect(() => {
    const element = card.current
    const parent = element?.parentElement
    if (!element || !parent) {
      return
    }
    const fitsRight = point.x + CURSOR_GAP_PIXELS + element.offsetWidth + EDGE_MARGIN_PIXELS <= parent.clientWidth
    const fitsBelow = point.y + CURSOR_GAP_PIXELS + element.offsetHeight + EDGE_MARGIN_PIXELS <= parent.clientHeight
    setPosition({
      left: fitsRight ? point.x + CURSOR_GAP_PIXELS : Math.max(EDGE_MARGIN_PIXELS, point.x - CURSOR_GAP_PIXELS - element.offsetWidth),
      top: fitsBelow ? point.y + CURSOR_GAP_PIXELS : Math.max(EDGE_MARGIN_PIXELS, point.y - CURSOR_GAP_PIXELS - element.offsetHeight)
    })
  }, [point, hover, detailed])

  const subject = viewId === 'caveBiomes' ? hover.caveBiome : viewId === 'regions' ? hover.region : hover.biome
  const groups = detailed ? detailGroups(hover, viewId) : []
  const parent = replacedParent(hover.pickPath)
  return (
    <div
      ref={card}
      className="pointer-events-none absolute z-10 w-80 rounded-[10px] border border-overlay-border bg-popover px-3.5 py-3 text-popover-foreground shadow-xl backdrop-blur-sm"
      style={position}
    >
      <Header subject={subject} region={viewId === 'regions' ? null : hover.region} />
      {parent !== null && <p className="mt-0.5 truncate text-[12px] text-secondary-text">child of {parent}</p>}
      <p className="mt-0.5 truncate text-[12px] text-secondary-text tabular-nums">{ground(hover, minY)}</p>
      <PickRows path={hover.pickPath} />
      {groups.map((group) => (
        <section key={group.title} className="mt-2.5">
          <h3 className="text-[11px] font-semibold text-muted-foreground">{group.title}</h3>
          <dl className="mt-1 grid grid-cols-[auto_1fr] gap-x-3 gap-y-0.5 text-[12px]">
            {group.rows.map((row) => (
              <div key={row.label} className="contents">
                <dt className="text-secondary-text">{row.label}</dt>
                <dd className={row.mono ? 'truncate font-mono text-[11.5px]' : 'truncate'}>{row.value}</dd>
              </div>
            ))}
          </dl>
        </section>
      ))}
      <div className="mt-2.5 flex flex-wrap gap-x-3 gap-y-1 text-[11px] text-muted-foreground">
        {!detailed && <Hint keyText="Shift" label="More" />}
        <Hint keyText={shortcutFor('highlight').keyText} label="Highlight" />
        <Hint keyText="Alt+Click" label="Open file" />
      </div>
    </div>
  )
}

function Header({ subject, region }: { subject: MapLabel | null; region: MapLabel | null }): React.JSX.Element {
  return (
    <div className="flex items-baseline gap-2">
      {subject !== null && <span className="size-2.5 shrink-0 translate-y-[1px] rounded-[3px]" style={{ backgroundColor: hexColor(subject.color) }} />}
      <span className="min-w-0 flex-1 truncate text-[13.5px] font-semibold">{subject?.name ?? 'Nothing here'}</span>
      {region !== null && <span className="shrink-0 truncate text-[12px] text-secondary-text">{region.name}</span>}
    </div>
  )
}

function Hint({ keyText, label }: { keyText: string; label: string }): React.JSX.Element {
  return (
    <span className="flex items-center gap-1">
      <Kbd className="h-4 min-w-4 text-[10.5px]">{keyText}</Kbd>
      {label}
    </span>
  )
}

/** Height as the world's Y, how far above or below the water, and how steep. */
function ground(hover: MapHover, minY: number): string {
  const aboveWater = Math.round(hover.height - hover.fluidHeight)
  const signed = aboveWater > 0 ? `+${aboveWater}` : String(aboveWater)
  return `Y ${Math.round(hover.height + minY)} · ${signed} from water · ${Math.round(hover.slopeDegrees)}°`
}

function detailGroups(hover: MapHover, viewId: string): { title: string; rows: Row[] }[] {
  const groups: { title: string; rows: Row[] }[] = []
  const terrain: Row[] = []
  if (viewId === 'caveBiomes' && hover.biome !== null) {
    terrain.push({ label: 'Surface biome', value: hover.biome.name })
  } else if (hover.caveBiome !== null && hover.caveBiome.key !== hover.biome?.key) {
    terrain.push({ label: 'Cave biome', value: hover.caveBiome.name })
  }
  if (hover.vanillaBiome !== null) {
    terrain.push({ label: 'Minecraft', value: hover.vanillaBiome.replace('minecraft:', '').replaceAll('_', ' ') })
  }
  if (terrain.length > 0) {
    groups.push({ title: 'Terrain', rows: terrain })
  }
  const blockX = Math.floor(hover.worldX)
  const blockZ = Math.floor(hover.worldZ)
  groups.push({
    title: 'Location',
    rows: [
      { label: 'Block', value: `${blockX}, ${blockZ}` },
      { label: 'Chunk', value: `${blockX >> 4}, ${blockZ >> 4}` },
      { label: 'Region file', value: `r.${blockX >> 9}.${blockZ >> 9}.mca`, mono: true }
    ]
  })
  const files: Row[] = []
  if (hover.biomeFile !== null) {
    files.push({ label: 'Biome', value: hover.biomeFile, mono: true })
  }
  if (hover.regionFile !== null) {
    files.push({ label: 'Region', value: hover.regionFile, mono: true })
  }
  if (files.length > 0) {
    groups.push({ title: 'Files', rows: files })
  }
  return groups
}

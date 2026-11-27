import type { PickBand, PickStep } from '../bridge/types'
import { hexColor } from './colors'

// Gaps between bands shrink as there are more of them, so many thin bands still read as a bar.
const WIDE_GAP_MOST_BANDS = 18
const NARROW_GAP_MOST_BANDS = 50
/** Why a step that nothing picked has its label. */
const UNPICKED_NOTES: Partial<Record<PickStep['kind'], string>> = { FOCUS: 'Focus', IMAGE_MAP: 'Image map', PREVIEW: 'Preview' }

/** How the spot got its region and biome: one row per pick, the noise value marked on every option's share of it. */
export function PickRows({ path }: { path: PickStep[] }): React.JSX.Element | null {
  if (path.length === 0) {
    return null
  }
  return (
    <div className="mt-2 grid grid-cols-[auto_1fr_auto] items-center gap-x-2.5 gap-y-1.5 text-[11.5px]">
      {path.map((step, index) => (
        <PickRow key={index} step={step} />
      ))}
    </div>
  )
}

function PickRow({ step }: { step: PickStep }): React.JSX.Element {
  return (
    <>
      <span className="text-muted-foreground">{rowLabel(step)}</span>
      {step.noise === null ? (
        <span className="truncate text-secondary-text">{UNPICKED_NOTES[step.kind] ?? ''}</span>
      ) : (
        <NoiseBar bands={step.bands} noise={step.noise} />
      )}
      <span className="text-right font-mono text-[11px] text-secondary-text tabular-nums">{step.noise === null ? '' : step.noise.toFixed(3)}</span>
    </>
  )
}

function NoiseBar({ bands, noise }: { bands: PickBand[]; noise: number }): React.JSX.Element {
  const picked = pickedBandIndex(bands, noise)
  const gap = bands.length <= WIDE_GAP_MOST_BANDS ? 2 : bands.length <= NARROW_GAP_MOST_BANDS ? 1 : 0
  return (
    <div className="relative flex h-1.5 min-w-0" style={{ gap }}>
      {bands.map((band, index) => (
        <span
          key={index}
          className="h-full min-w-px rounded-[2px]"
          style={{
            flexGrow: band.to - band.from,
            flexBasis: 0,
            backgroundColor: index === picked ? hexColor(band.option.color) : 'var(--overlay-border)'
          }}
        />
      ))}
      <span
        className="absolute top-1/2 h-3 w-0.5 -translate-x-1/2 -translate-y-1/2 bg-foreground shadow-[0_0_0_1px_var(--popover)]"
        style={{ left: `${Math.min(1, Math.max(0, noise)) * 100}%` }}
      />
    </div>
  )
}

/** The first band the noise does not pass; values beyond 1 pick the last, as the generator does. */
export function pickedBandIndex(bands: PickBand[], noise: number): number {
  const index = bands.findIndex((band) => noise <= band.to)
  return index === -1 ? bands.length - 1 : index
}

function rowLabel(step: PickStep): string {
  switch (step.kind) {
    case 'REGION':
      return 'Region'
    case 'BIOME':
      return step.list === null ? 'Biome' : step.list.charAt(0).toUpperCase() + step.list.slice(1)
    case 'CHILD':
      return 'Child'
    case 'IMAGE_MAP':
    case 'FOCUS':
    case 'PREVIEW':
      return step.picked.kind === 'REGION' ? 'Region' : 'Biome'
  }
}

/** The parent the spot's biome replaced, when a child step replaced one. */
export function replacedParent(path: PickStep[]): string | null {
  return path.findLast((step) => step.kind === 'CHILD' && step.replaced !== null)?.replaced ?? null
}

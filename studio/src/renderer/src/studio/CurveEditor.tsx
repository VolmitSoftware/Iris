import { IdeIcon } from '@/components/IdeIcon'
import { useRef, useState } from 'react'
import { Button } from '@/components/ui/button'
import { Input } from '@/components/ui/input'
import {
  CURVE_SHAPES,
  isShape,
  keyframesFrom,
  plotRange,
  roundedNumber,
  samplesOf,
  shapeFrom,
  shapeNamed,
  sortedKeyframes,
  withAddedKeyframe,
  withKeyframe,
  withoutKeyframe,
  withSetting,
  withShape,
  type Keyframe
} from '../editor/curves'

const PREVIEW_SAMPLES = 40
const PREVIEW_WIDTH = 100
const PREVIEW_HEIGHT = 12
const PLOT_WIDTH = 280
const PLOT_HEIGHT = 140
const PLOT_INSET = 10
const PLOT_SAMPLES = 96
const POINT_RADIUS = 4
const POINTS_HINT = 'Drag a point · Double-click adds · Right-click removes'
const GUIDE_LEVELS = [0, 1]
const DRAGGED_PRECISION = 1000

/** A curve drawn across a value's field, its lowest to highest filling the height, with what it starts and ends at beside it. */
export function CurvePreview({ value }: { value: unknown }): React.JSX.Element {
  const samples = samplesOf(value, PREVIEW_SAMPLES) ?? [0]
  const low = Math.min(...samples)
  const span = Math.max(...samples) - low
  const points = samples.map((sample, index) => {
    const x = (index / Math.max(1, samples.length - 1)) * PREVIEW_WIDTH
    const y = span === 0 ? PREVIEW_HEIGHT / 2 : PREVIEW_HEIGHT - ((sample - low) / span) * PREVIEW_HEIGHT
    return `${x.toFixed(1)},${y.toFixed(1)}`
  })
  return (
    <>
      <svg viewBox={`0 0 ${PREVIEW_WIDTH} ${PREVIEW_HEIGHT}`} preserveAspectRatio="none" className="h-3 min-w-4 flex-1 overflow-visible text-primary" aria-hidden>
        <polyline points={points.join(' ')} fill="none" stroke="currentColor" strokeWidth={1.4} strokeLinecap="round" strokeLinejoin="round" vectorEffect="non-scaling-stroke" />
      </svg>
      <span className="shrink-0 text-muted-foreground tabular-nums">
        {roundedNumber(samples[0])} → {roundedNumber(samples[samples.length - 1])}
      </span>
    </>
  )
}

interface CurveEditorProps {
  value: unknown
  locked: boolean
  /** Called with the curve after every change, so the object bakes again while it is shaped. */
  onWrite(next: unknown): void
}

/** Edits a value that changes along a part as a curve: points to drag or type, or a named shape with its settings. */
export function CurveEditor({ value, locked, onWrite }: CurveEditorProps): React.JSX.Element {
  const byShape = isShape(value)
  return (
    <div className="w-[300px] space-y-2">
      <div className="flex gap-1">
        <Button variant={byShape ? 'ghost' : 'secondary'} size="xs" disabled={locked} onClick={() => byShape && onWrite(keyframesFrom(value))}>
          Points
        </Button>
        <Button variant={byShape ? 'secondary' : 'ghost'} size="xs" disabled={locked} onClick={() => !byShape && onWrite(shapeFrom(value))}>
          Shape
        </Button>
      </div>
      <CurvePlot value={value} locked={locked || byShape} onWrite={onWrite} />
      {byShape ? <ShapeForm shape={value as Record<string, unknown>} locked={locked} onWrite={onWrite} /> : <PointsForm keys={value as Keyframe[]} locked={locked} onWrite={onWrite} />}
    </div>
  )
}

interface PlotDrag {
  index: number
  keys: Keyframe[]
  range: [number, number]
}

/** The curve from t = 0 to 1, with its points to drag while it is made of points; a drag is written when it ends. */
function CurvePlot({ value, locked, onWrite }: CurveEditorProps): React.JSX.Element {
  const [drag, setDrag] = useState<PlotDrag | null>(null)
  const [hint, setHint] = useState(POINTS_HINT)
  const plot = useRef<SVGSVGElement>(null)
  const shown = drag?.keys ?? value
  const samples = samplesOf(shown, PLOT_SAMPLES) ?? []
  const range = drag?.range ?? plotRange(samples)
  const keys = isShape(shown) ? [] : sortedKeyframes(shown as Keyframe[])
  const area = { left: PLOT_INSET, top: PLOT_INSET, width: PLOT_WIDTH - 2 * PLOT_INSET, height: PLOT_HEIGHT - 2 * PLOT_INSET }
  const screenX = (t: number): number => area.left + t * area.width
  const screenY = (level: number): number => area.top + area.height - ((level - range[0]) / (range[1] - range[0])) * area.height
  const keyframeAt = (event: React.PointerEvent | React.MouseEvent): Keyframe => {
    const bounds = plot.current!.getBoundingClientRect()
    const x = ((event.clientX - bounds.left) / bounds.width) * PLOT_WIDTH
    const y = ((event.clientY - bounds.top) / bounds.height) * PLOT_HEIGHT
    const t = clamp((x - area.left) / area.width, 0, 1)
    const level = range[0] + ((area.top + area.height - y) / area.height) * (range[1] - range[0])
    return [roundTo(t), roundTo(clamp(level, range[0], range[1]))]
  }

  // A point stays between its neighbours along t, so the points never swap order under the pointer.
  const dragTo = (event: React.PointerEvent): void => {
    if (drag === null) {
      return
    }
    const [t, level] = keyframeAt(event)
    const earliest = drag.index === 0 ? 0 : drag.keys[drag.index - 1][0]
    const latest = drag.index === drag.keys.length - 1 ? 1 : drag.keys[drag.index + 1][0]
    const moved: Keyframe = [clamp(t, earliest, latest), level]
    setDrag({ ...drag, keys: withKeyframe(drag.keys, drag.index, moved) })
    setHint(`t ${roundedNumber(moved[0])} · ${roundedNumber(moved[1])}`)
  }

  const finishDrag = (): void => {
    if (drag !== null) {
      onWrite(drag.keys)
    }
    setDrag(null)
    setHint(POINTS_HINT)
  }

  return (
    <div>
      <svg
        ref={plot}
        viewBox={`0 0 ${PLOT_WIDTH} ${PLOT_HEIGHT}`}
        className="block w-full touch-none rounded-md bg-background"
        onPointerMove={dragTo}
        onPointerUp={finishDrag}
        onDoubleClick={(event) => !locked && onWrite(sortedKeyframes([...keys, keyframeAt(event)]))}
      >
        {GUIDE_LEVELS.map((level) => (
          <g key={level}>
            <line x1={area.left} x2={area.left + area.width} y1={screenY(level)} y2={screenY(level)} className="stroke-border" strokeWidth={1} />
            <text x={area.left + 2} y={screenY(level) - 2} className="fill-muted-foreground text-[10px]">
              {level}
            </text>
          </g>
        ))}
        <polyline
          points={samples.map((sample, index) => `${screenX(index / (samples.length - 1)).toFixed(1)},${screenY(sample).toFixed(1)}`).join(' ')}
          fill="none"
          className="stroke-primary"
          strokeWidth={2}
          strokeLinecap="round"
          strokeLinejoin="round"
        />
        {!locked &&
          keys.map(([t, level], index) => (
            <circle
              key={index}
              cx={screenX(t)}
              cy={screenY(level)}
              r={POINT_RADIUS}
              className={drag?.index === index ? 'fill-primary stroke-primary' : 'fill-popover stroke-primary hover:fill-primary'}
              strokeWidth={1.5}
              onPointerDown={(event) => {
                event.stopPropagation()
                if (event.button === 2) {
                  return
                }
                event.currentTarget.ownerSVGElement?.setPointerCapture(event.pointerId)
                setDrag({ index, keys, range })
              }}
              onContextMenu={(event) => {
                event.preventDefault()
                event.stopPropagation()
                onWrite(withoutKeyframe(keys, index))
              }}
            />
          ))}
      </svg>
      {!isShape(value) && <p className="pt-1 text-[11px] text-secondary-text">{hint}</p>}
    </div>
  )
}

function PointsForm({ keys, locked, onWrite }: { keys: Keyframe[]; locked: boolean; onWrite(next: Keyframe[]): void }): React.JSX.Element {
  return (
    <div className="space-y-1">
      {keys.map(([t, value], index) => (
        <div key={index} className="flex items-center gap-1">
          <NumberBox label="at" value={t} disabled={locked} onCommit={(next) => next !== undefined && onWrite(withKeyframe(keys, index, [next, value]))} />
          <NumberBox label="value" value={value} disabled={locked} onCommit={(next) => next !== undefined && onWrite(withKeyframe(keys, index, [t, next]))} />
          <Button variant="ghost" size="icon-sm" title="Remove the point" className="size-6 shrink-0" disabled={locked || keys.length <= 1} onClick={() => onWrite(withoutKeyframe(keys, index))}>
            <IdeIcon name="delete" className="size-3.5" />
          </Button>
        </div>
      ))}
      <Button variant="ghost" size="xs" disabled={locked} onClick={() => onWrite(withAddedKeyframe(keys))}>
        <IdeIcon name="add" className="size-3.5" /> Point
      </Button>
    </div>
  )
}

function ShapeForm({ shape, locked, onWrite }: { shape: Record<string, unknown>; locked: boolean; onWrite(next: unknown): void }): React.JSX.Element {
  const named = shapeNamed(shape.shape)!
  const numeric = (name: string): number | undefined => (typeof shape[name] === 'number' ? shape[name] : undefined)
  return (
    <div className="space-y-1">
      <select
        className="h-7 w-full min-w-0 rounded-md border border-input bg-transparent px-1.5 text-[12px] outline-none focus-visible:border-ring dark:bg-input/30"
        value={named.key}
        disabled={locked}
        onChange={(event) => onWrite(withShape(shape, event.target.value))}
      >
        {CURVE_SHAPES.map((candidate) => (
          <option key={candidate.key} value={candidate.key}>
            {candidate.key}
          </option>
        ))}
      </select>
      <div className="flex items-center gap-1">
        <NumberBox label="from" value={numeric('from')} placeholder="0" disabled={locked} onCommit={(next) => onWrite(withSetting(shape, 'from', next))} />
        <NumberBox label="to" value={numeric('to')} placeholder="1" disabled={locked} onCommit={(next) => onWrite(withSetting(shape, 'to', next))} />
      </div>
      {named.parameters.map((parameter) => (
        <NumberBox
          key={parameter.name}
          label={parameter.name}
          value={numeric(parameter.name)}
          placeholder={String(parameter.fallback)}
          disabled={locked}
          onCommit={(next) => onWrite(withSetting(shape, parameter.name, next))}
        />
      ))}
    </div>
  )
}

interface NumberBoxProps {
  label: string
  value: number | undefined
  placeholder?: string
  disabled: boolean
  /** The number typed, or undefined when it was emptied; text that is no number is dropped. */
  onCommit(next: number | undefined): void
}

function NumberBox({ label, value, placeholder, disabled, onCommit }: NumberBoxProps): React.JSX.Element {
  const shown = value === undefined ? '' : String(value)
  const [draft, setDraft] = useState(shown)
  const [seen, setSeen] = useState(shown)
  if (seen !== shown) {
    setSeen(shown)
    setDraft(shown)
  }
  const commit = (): void => {
    if (draft === shown) {
      return
    }
    const next = draft.trim() === '' ? undefined : Number(draft)
    if (next !== undefined && !Number.isFinite(next)) {
      setDraft(shown)
      return
    }
    onCommit(next)
  }
  return (
    <label className="flex min-w-0 flex-1 items-center gap-1 text-[11px] text-secondary-text">
      {label}
      <Input
        value={draft}
        disabled={disabled}
        placeholder={placeholder}
        inputMode="decimal"
        className="h-6 px-1.5 text-[11.5px] tabular-nums md:text-[11.5px]"
        onChange={(event) => setDraft(event.target.value)}
        onBlur={commit}
        onKeyDown={(event) => {
          if (event.key === 'Enter') {
            commit()
          } else if (event.key === 'Escape') {
            setDraft(shown)
          }
        }}
      />
    </label>
  )
}

function roundTo(value: number): number {
  return Math.round(value * DRAGGED_PRECISION) / DRAGGED_PRECISION
}

function clamp(value: number, lowest: number, highest: number): number {
  return Math.min(highest, Math.max(lowest, value))
}

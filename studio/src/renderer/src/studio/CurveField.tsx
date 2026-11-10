import { ChevronRight, Plus, Trash2 } from 'lucide-react'
import { useState } from 'react'
import { Button } from '@/components/ui/button'
import { Input } from '@/components/ui/input'
import { cn } from '@/lib/utils'
import {
  CURVE_SHAPES,
  isShape,
  keyframesFrom,
  samplesOf,
  shapeFrom,
  shapeNamed,
  withAddedKeyframe,
  withKeyframe,
  withoutKeyframe,
  withSetting,
  withShape,
  type Keyframe
} from '../editor/curves'
import { FieldRow, type SettingFieldProps } from './FieldRow'

const SPARKLINE_WIDTH = 100
const SPARKLINE_HEIGHT = 24
const SPARKLINE_SAMPLES = 40
const SPARKLINE_MARGIN = 2

interface CurveFieldProps extends SettingFieldProps {
  open: boolean
  onToggle(): void
}

/** A value written as a curve: a sparkline of it that opens an editor for its keyframes, or for the named shape it follows. */
export function CurveField(props: CurveFieldProps): React.JSX.Element {
  const { value, locked, open, onToggle, onSet } = props
  return (
    <>
      <FieldRow {...props} problem={null}>
        <button
          type="button"
          title="Edit the curve"
          className="flex h-7 min-w-0 flex-1 items-center gap-1 rounded-md border border-input px-1.5 hover:bg-accent/50 dark:bg-input/30"
          onClick={onToggle}
        >
          <Sparkline value={value} />
          <ChevronRight className={cn('size-3.5 shrink-0 text-secondary-text transition-transform', open && 'rotate-90')} />
        </button>
      </FieldRow>
      {open && <CurveEditor value={value} locked={locked} onWrite={(next) => onSet(JSON.stringify(next))} />}
    </>
  )
}

function Sparkline({ value }: { value: unknown }): React.JSX.Element {
  const samples = samplesOf(value, SPARKLINE_SAMPLES) ?? []
  const low = Math.min(...samples)
  const span = Math.max(...samples) - low
  const drawable = SPARKLINE_HEIGHT - 2 * SPARKLINE_MARGIN
  const points = samples.map((sample, index) => {
    const x = (index / (samples.length - 1)) * SPARKLINE_WIDTH
    const y = SPARKLINE_MARGIN + (span === 0 ? drawable / 2 : drawable - ((sample - low) / span) * drawable)
    return `${x.toFixed(1)},${y.toFixed(1)}`
  })
  return (
    <svg viewBox={`0 0 ${SPARKLINE_WIDTH} ${SPARKLINE_HEIGHT}`} preserveAspectRatio="none" className="h-5 min-w-0 flex-1 text-primary" aria-hidden>
      <polyline points={points.join(' ')} fill="none" stroke="currentColor" strokeWidth={1.5} vectorEffect="non-scaling-stroke" />
    </svg>
  )
}

function CurveEditor({ value, locked, onWrite }: { value: unknown; locked: boolean; onWrite(next: unknown): void }): React.JSX.Element {
  const byShape = isShape(value)
  return (
    <div className="mb-1 ml-2 space-y-1.5 rounded-md border bg-muted/40 p-2">
      <div className="flex gap-1">
        <Button variant={byShape ? 'ghost' : 'secondary'} size="xs" disabled={locked} onClick={() => byShape && onWrite(keyframesFrom(value))}>
          Points
        </Button>
        <Button variant={byShape ? 'secondary' : 'ghost'} size="xs" disabled={locked} onClick={() => !byShape && onWrite(shapeFrom(value))}>
          Shape
        </Button>
      </div>
      {isShape(value) ? <ShapeEditor shape={value} locked={locked} onWrite={onWrite} /> : <PointsEditor keys={value as Keyframe[]} locked={locked} onWrite={onWrite} />}
    </div>
  )
}

function PointsEditor({ keys, locked, onWrite }: { keys: Keyframe[]; locked: boolean; onWrite(next: Keyframe[]): void }): React.JSX.Element {
  return (
    <>
      {keys.map(([t, value], index) => (
        <div key={index} className="flex items-center gap-1">
          <NumberBox label="at" value={t} disabled={locked} onCommit={(next) => next !== undefined && onWrite(withKeyframe(keys, index, [next, value]))} />
          <NumberBox label="value" value={value} disabled={locked} onCommit={(next) => next !== undefined && onWrite(withKeyframe(keys, index, [t, next]))} />
          <Button variant="ghost" size="icon-sm" title="Remove the point" className="size-6 shrink-0" disabled={locked || keys.length <= 1} onClick={() => onWrite(withoutKeyframe(keys, index))}>
            <Trash2 className="size-3.5" />
          </Button>
        </div>
      ))}
      <Button variant="ghost" size="xs" disabled={locked} onClick={() => onWrite(withAddedKeyframe(keys))}>
        <Plus /> Point
      </Button>
    </>
  )
}

function ShapeEditor({ shape, locked, onWrite }: { shape: Record<string, unknown>; locked: boolean; onWrite(next: unknown): void }): React.JSX.Element {
  const named = shapeNamed(shape.shape)!
  const numeric = (name: string): number | undefined => (typeof shape[name] === 'number' ? shape[name] : undefined)
  return (
    <>
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
    </>
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

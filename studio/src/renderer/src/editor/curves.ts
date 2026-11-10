/** One keyframe of a curve: where it is from 0 to 1, and the value there. */
export type Keyframe = [number, number]

export interface ShapeParameter {
  name: string
  fallback: number
}

/** A named curve (ProceduralValue.CurveShape): from 0 at t = 0 toward 1, stretched between a value's from and to. */
export interface CurveShape {
  key: string
  parameters: ShapeParameter[]
  at(t: number, parameter: (name: string) => number): number
}

const DEFAULT_FROM = 0
const DEFAULT_TO = 1
const KEYFRAME_SIZE = 2
const RANGE_SIZE = 2
const NARROWEST_BELL = 1e-6
const SHAPE_STOPS = 5
const FIRST_STOP = 0
const LAST_STOP = 1

export const CURVE_SHAPES: CurveShape[] = [
  { key: 'linear', parameters: [], at: (t) => t },
  { key: 'smooth', parameters: [], at: (t) => t * t * (3 - 2 * t) },
  { key: 'power', parameters: [{ name: 'exponent', fallback: 2 }], at: (t, p) => Math.pow(t, p('exponent')) },
  {
    key: 'sigmoid',
    parameters: [
      { name: 'steepness', fallback: 10 },
      { name: 'mid', fallback: 0.5 }
    ],
    at: (t, p) => 1 / (1 + Math.exp(-p('steepness') * (t - p('mid'))))
  },
  {
    key: 'bell',
    parameters: [
      { name: 'mid', fallback: 0.5 },
      { name: 'width', fallback: 0.15 }
    ],
    at: (t, p) => Math.exp(-0.5 * Math.pow((t - p('mid')) / Math.max(NARROWEST_BELL, p('width')), 2))
  },
  {
    key: 'periodic',
    parameters: [
      { name: 'periods', fallback: 4 },
      { name: 'sharpness', fallback: 8 }
    ],
    at: (t, p) => Math.pow(Math.max(0, Math.sin(2 * Math.PI * t * p('periods'))), p('sharpness'))
  },
  { key: 'sine', parameters: [{ name: 'periods', fallback: 1 }], at: (t, p) => 0.5 + 0.5 * Math.sin(2 * Math.PI * t * p('periods')) }
]

export function shapeNamed(key: unknown): CurveShape | undefined {
  return typeof key === 'string' ? CURVE_SHAPES.find((shape) => shape.key === key.toLowerCase()) : undefined
}

function isObject(value: unknown): value is Record<string, unknown> {
  return typeof value === 'object' && value !== null && !Array.isArray(value)
}

function isKeyframe(value: unknown): value is Keyframe {
  return Array.isArray(value) && value.length === KEYFRAME_SIZE && value.every((part) => typeof part === 'number')
}

/** Whether {@code value} is keyframes [[t, value], ...], which a range [min, max] and a number are not. */
export function isKeyframes(value: unknown): value is Keyframe[] {
  return Array.isArray(value) && value.length > 0 && value.every(isKeyframe)
}

/** Whether {@code value} is a named curve {"shape": ...} the studio knows. */
export function isShape(value: unknown): value is Record<string, unknown> {
  return isObject(value) && shapeNamed(value.shape) !== undefined
}

/** Whether {@code value} is a curve, as opposed to a number or a [min, max] range. */
export function isCurve(value: unknown): boolean {
  return isKeyframes(value) || isShape(value)
}

/** Whether {@code value} is a number or a range, which {@link curveFrom} makes a curve. */
export function canBecomeCurve(value: unknown): boolean {
  return typeof value === 'number' || rangeOf(value) !== null
}

function rangeOf(value: unknown): [number, number] | null {
  if (Array.isArray(value) && value.length === RANGE_SIZE && value.every((part) => typeof part === 'number')) {
    return [value[0], value[1]]
  }
  if (isObject(value) && typeof value.min === 'number' && typeof value.max === 'number') {
    return [value.min, value.max]
  }
  return null
}

/** A number as a curve that stays at it, a range as a curve rising from its low end to its high end; null for anything else. */
export function curveFrom(value: unknown): Keyframe[] | null {
  if (typeof value === 'number') {
    return [
      [0, value],
      [1, value]
    ]
  }
  const range = rangeOf(value)
  return range === null
    ? null
    : [
        [0, range[0]],
        [1, range[1]]
      ]
}

function numberOf(written: unknown, fallback: number): number {
  return typeof written === 'number' ? written : fallback
}

/** The value of a curve at {@code t}, from 0 to 1; NaN for a value that is no curve. */
export function curveAt(value: unknown, t: number): number {
  if (isKeyframes(value)) {
    return keyframesAt(sortedKeyframes(value), t)
  }
  if (!isShape(value)) {
    return Number.NaN
  }
  const shape = shapeNamed(value.shape)!
  const from = numberOf(value.from, DEFAULT_FROM)
  const to = numberOf(value.to, DEFAULT_TO)
  const parameter = (name: string): number => numberOf(value[name], shape.parameters.find((candidate) => candidate.name === name)!.fallback)
  return from + (to - from) * shape.at(Math.max(0, Math.min(1, t)), parameter)
}

export function sortedKeyframes(keys: Keyframe[]): Keyframe[] {
  return [...keys].sort((a, b) => a[0] - b[0])
}

function keyframesAt(keys: Keyframe[], t: number): number {
  if (t <= keys[0][0]) {
    return keys[0][1]
  }
  for (let index = 1; index < keys.length; index++) {
    if (t <= keys[index][0]) {
      const span = keys[index][0] - keys[index - 1][0]
      const blend = span <= 0 ? 1 : (t - keys[index - 1][0]) / span
      return keys[index - 1][1] + (keys[index][1] - keys[index - 1][1]) * blend
    }
  }
  return keys[keys.length - 1][1]
}

/** {@code count} values of the curve, from its start to its end; null for a value that is no curve. */
export function samplesOf(value: unknown, count: number): number[] | null {
  if (!isCurve(value)) {
    return null
  }
  return Array.from({ length: count }, (_, index) => curveAt(value, count === 1 ? 0 : index / (count - 1)))
}

/** The curve as keyframes, such as to move a named curve's shape by hand: its values at evenly spaced points. */
export function keyframesFrom(value: unknown): Keyframe[] {
  if (isKeyframes(value)) {
    return value
  }
  const stops = Array.from({ length: SHAPE_STOPS }, (_, index) => FIRST_STOP + ((LAST_STOP - FIRST_STOP) * index) / (SHAPE_STOPS - 1))
  return stops.map((t): Keyframe => [t, curveAt(value, t)])
}

/** A linear named curve between the first and last keyframes' values, or the shape itself. */
export function shapeFrom(value: unknown): Record<string, unknown> {
  if (isShape(value)) {
    return value
  }
  const keys = isKeyframes(value) ? sortedKeyframes(value) : []
  return { shape: 'linear', from: keys.length > 0 ? keys[0][1] : DEFAULT_FROM, to: keys.length > 0 ? keys[keys.length - 1][1] : DEFAULT_TO }
}

/** The shape with {@code key}, keeping its from and to; the other settings of the old shape do not carry over. */
export function withShape(shape: Record<string, unknown>, key: string): Record<string, unknown> {
  return { shape: key, from: numberOf(shape.from, DEFAULT_FROM), to: numberOf(shape.to, DEFAULT_TO) }
}

/** The shape with {@code name} set to {@code number}, or left out when it is undefined so the shape's own default applies. */
export function withSetting(shape: Record<string, unknown>, name: string, number: number | undefined): Record<string, unknown> {
  const { [name]: _replaced, ...others } = shape
  return number === undefined ? others : { ...others, [name]: number }
}

/** The keyframes with the one at {@code index} (as listed) set to {@code keyframe}. */
export function withKeyframe(keys: Keyframe[], index: number, keyframe: Keyframe): Keyframe[] {
  return keys.map((existing, position) => (position === index ? keyframe : existing))
}

/** The keyframes without the one at {@code index}; the last one stays, since a curve needs a keyframe. */
export function withoutKeyframe(keys: Keyframe[], index: number): Keyframe[] {
  return keys.length <= 1 ? keys : keys.filter((_, position) => position !== index)
}

/** The keyframes with one more halfway across the widest gap, on the curve as it is; a lone keyframe gets one at the other end. */
export function withAddedKeyframe(keys: Keyframe[]): Keyframe[] {
  const sorted = sortedKeyframes(keys)
  if (sorted.length === 1) {
    const t = sorted[0][0] === LAST_STOP ? FIRST_STOP : LAST_STOP
    return sortedKeyframes([...sorted, [t, sorted[0][1]]])
  }
  let widest = 0
  let before = 0
  for (let index = 1; index < sorted.length; index++) {
    const gap = sorted[index][0] - sorted[index - 1][0]
    if (gap > widest) {
      widest = gap
      before = index - 1
    }
  }
  const t = sorted[before][0] + widest / 2
  return sortedKeyframes([...sorted, [t, keyframesAt(sorted, t)]])
}

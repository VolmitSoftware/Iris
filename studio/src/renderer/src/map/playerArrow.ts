export interface ScreenPoint {
  x: number
  z: number
}

const MINECRAFT_YAW_TO_SCREEN_DEGREES = 180
const ARROW_HALF_LENGTH_PIXELS = 9.5
const ARROW_HALF_WIDTH_PIXELS = 7.5
const ARROW_NOTCH_PIXELS = 3.5
const ARROW_EDGE_WIDTH_PIXELS = 1.75
const ARROW_OUTLINE_WIDTH_PIXELS = 1
const ARROW_SHADOW_OFFSET_PIXELS = 1.5
const ARROW_REACH_PIXELS = ARROW_HALF_LENGTH_PIXELS + ARROW_EDGE_WIDTH_PIXELS + ARROW_OUTLINE_WIDTH_PIXELS
const ARROW_FILL = '#3574f0'
const ARROW_EDGE = '#ffffff'
const ARROW_OUTLINE = 'rgba(0, 0, 0, 0.59)'
const ARROW_SHADOW = 'rgba(0, 0, 0, 0.35)'
const LABEL_GAP_PIXELS = 5
const LABEL_HEIGHT_PIXELS = 18
const LABEL_PADDING_X_PIXELS = 7
const LABEL_FONT = '600 12px Inter, "Segoe UI", system-ui, sans-serif'
const LABEL_BACKGROUND = 'rgba(30, 31, 34, 0.86)'
const LABEL_BORDER = 'rgba(255, 255, 255, 0.22)'
const LABEL_TEXT = '#ffffff'

/**
 * The corners of a player's arrow around the origin: tip, right wing, notch, left wing. Minecraft's yaw 0 faces
 * south and 90 west, which on the map is down and left, so the heading is (-sin, cos) in (x, z).
 */
export function arrowCorners(yawDegrees: number, halfLength: number, halfWidth: number, notch: number): ScreenPoint[] {
  const turn = ((yawDegrees + MINECRAFT_YAW_TO_SCREEN_DEGREES) * Math.PI) / 180
  const sine = Math.sin(turn)
  const cosine = Math.cos(turn)
  const upright: ScreenPoint[] = [
    { x: 0, z: -halfLength },
    { x: halfWidth, z: halfLength },
    { x: 0, z: halfLength - notch },
    { x: -halfWidth, z: halfLength }
  ]
  return upright.map((corner) => ({ x: corner.x * cosine - corner.z * sine, z: corner.x * sine + corner.z * cosine }))
}

/** A blue arrow with a white edge, dark outline and shadow pointing where the player looks, and their name in a pill above. */
export function drawPlayerArrow(context: CanvasRenderingContext2D, x: number, z: number, yawDegrees: number, name: string): void {
  const corners = arrowCorners(yawDegrees, ARROW_HALF_LENGTH_PIXELS, ARROW_HALF_WIDTH_PIXELS, ARROW_NOTCH_PIXELS)
  context.save()
  context.lineJoin = 'round'
  context.fillStyle = ARROW_SHADOW
  context.fill(arrowPath(corners, x, z + ARROW_SHADOW_OFFSET_PIXELS))
  const arrow = arrowPath(corners, x, z)
  // Strokes are centred on the outline, so each ring is drawn twice its width and the next one covers its inner half.
  context.lineWidth = (ARROW_EDGE_WIDTH_PIXELS + ARROW_OUTLINE_WIDTH_PIXELS) * 2
  context.strokeStyle = ARROW_OUTLINE
  context.stroke(arrow)
  context.lineWidth = ARROW_EDGE_WIDTH_PIXELS * 2
  context.strokeStyle = ARROW_EDGE
  context.stroke(arrow)
  context.fillStyle = ARROW_FILL
  context.fill(arrow)
  drawNamePill(context, name, x, z - ARROW_REACH_PIXELS - LABEL_GAP_PIXELS)
  context.restore()
}

function arrowPath(corners: ScreenPoint[], x: number, z: number): Path2D {
  const path = new Path2D()
  corners.forEach((corner, index) => (index === 0 ? path.moveTo(x + corner.x, z + corner.z) : path.lineTo(x + corner.x, z + corner.z)))
  path.closePath()
  return path
}

function drawNamePill(context: CanvasRenderingContext2D, name: string, centerX: number, bottom: number): void {
  context.font = LABEL_FONT
  context.textAlign = 'center'
  context.textBaseline = 'middle'
  const width = context.measureText(name).width + LABEL_PADDING_X_PIXELS * 2
  const pill = new Path2D()
  pill.roundRect(centerX - width / 2, bottom - LABEL_HEIGHT_PIXELS, width, LABEL_HEIGHT_PIXELS, LABEL_HEIGHT_PIXELS / 2)
  context.fillStyle = LABEL_BACKGROUND
  context.fill(pill)
  context.lineWidth = 1
  context.strokeStyle = LABEL_BORDER
  context.stroke(pill)
  context.fillStyle = LABEL_TEXT
  context.fillText(name, centerX, bottom - LABEL_HEIGHT_PIXELS / 2)
}

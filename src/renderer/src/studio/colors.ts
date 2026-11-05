/** A 0xRRGGBB colour as CSS. */
export function hexColor(color: number): string {
  return `#${color.toString(16).padStart(6, '0')}`
}

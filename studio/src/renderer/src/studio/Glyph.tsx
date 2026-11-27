import { cn } from '@/lib/utils'

const ICON_MARKUP = import.meta.glob('../icons/intellij/**/*.svg', { eager: true, query: '?raw', import: 'default' }) as Record<string, string>

/** Each glyph's IntelliJ new-UI icon, by its path under `icons/intellij` without the `_dark.svg` ending. */
const GLYPHS = {
  add: 'general/add',
  chevronDown: 'general/chevronDown',
  chevronLeft: 'general/chevronLeft',
  chevronRight: 'general/chevronRight',
  close: 'general/close',
  delete: 'general/delete',
  help: 'general/questionMark',
  locate: 'general/locate',
  locked: 'general/locked',
  more: 'general/moreHorizontal',
  redo: 'general/redo',
  undo: 'general/undo',
  refresh: 'general/refresh',
  reset: 'general/reset',
  search: 'general/search',
  settings: 'general/settings',
  check: 'actions/checked',
  stopWatch: 'actions/stopWatch',
  actualZoom: 'image/actualZoom',
  grid: 'image/grid',
  web: 'toolwindows/web',
}

export type GlyphName = keyof typeof GLYPHS

const ICON_COLOUR = /#[0-9A-Fa-f]{6}\b/g

/** The icon's markup drawn in the current colour, sized by its box, so it takes the theme's icon colour. */
function markupOf(path: string): string {
  return path.replace(ICON_COLOUR, 'currentColor').replace('<svg ', '<svg class="size-full" ')
}

const MARKUP = new Map(Object.entries(ICON_MARKUP).map(([path, markup]) => [path, markupOf(markup)]))

/**
 * An IntelliJ icon: 16 px, 12 px when small, or 20 px when large, as IntelliJ draws stripe and main toolbar icons.
 * Large takes the icon's own 20 px drawing where IntelliJ has one.
 */
export function Glyph({ glyph, small, large, className }: { glyph: GlyphName; small?: boolean; large?: boolean; className?: string }): React.JSX.Element {
  const path = `../icons/intellij/${GLYPHS[glyph]}`
  const markup = (large && MARKUP.get(`${path}@20x20_dark.svg`)) || MARKUP.get(`${path}_dark.svg`)
  return (
    <span
      aria-hidden
      className={cn('glyph inline-flex shrink-0', small ? 'size-3' : large ? 'size-5' : 'size-4', className)}
      dangerouslySetInnerHTML={{ __html: markup ?? '' }}
    />
  )
}

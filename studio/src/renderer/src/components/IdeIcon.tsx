import { cn } from '@/lib/utils'

// IntelliJ's expui icons and the studio's own drawn to match (see icons/NOTICE.txt): each has a light file and a "_dark" twin in its own colours.
const ICON_URLS = import.meta.glob<string>('../icons/**/*.svg', { eager: true, query: '?url', import: 'default' })
const DARK_SUFFIX = '_dark'

export type IdeIconName =
  | 'actualZoom'
  | 'add'
  | 'blocks'
  | 'borders'
  | 'checked'
  | 'closeSmall'
  | 'chevronLeft'
  | 'chevronRight'
  | 'decoration'
  | 'delete'
  | 'exit'
  | 'grid'
  | 'groups'
  | 'help'
  | 'lightning'
  | 'locate'
  | 'locked'
  | 'memory'
  | 'mob'
  | 'redo'
  | 'refresh'
  | 'reset'
  | 'search'
  | 'settings'
  | 'shading'
  | 'stopWatch'
  | 'symlink'
  | 'theme'
  | 'undo'
  | 'web'
  | 'views/biomes'
  | 'views/noise'
  | 'views/objects'
  | 'views/palettes'
  | 'views/procedural'
  | 'views/regions'
  | 'views/saved-world'
  | 'views/world-3d'

function iconUrl(name: string): string {
  const url = ICON_URLS[`../icons/${name}.svg`]
  if (url === undefined) {
    throw new Error(`No icon named ${name}`)
  }
  return url
}

/** An IntelliJ icon, 16 px unless sized, in the light or dark colours the window shows. */
export function IdeIcon({ name, className }: { name: IdeIconName; className?: string }): React.JSX.Element {
  const size = cn('size-4 shrink-0', className)
  return (
    <>
      <img src={iconUrl(name)} alt="" draggable={false} className={cn(size, 'dark:hidden')} />
      <img src={iconUrl(name + DARK_SUFFIX)} alt="" draggable={false} className={cn(size, 'hidden dark:block')} />
    </>
  )
}

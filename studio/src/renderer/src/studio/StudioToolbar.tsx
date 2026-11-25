import type { ReactNode } from 'react'
import { Blocks, Mountain, PawPrint, SquareDashed } from 'lucide-react'
import { Button } from '@/components/ui/button'
import { Kbd } from '@/components/ui/kbd'
import { Separator } from '@/components/ui/separator'
import { Toggle } from '@/components/ui/toggle'
import { Tooltip, TooltipContent, TooltipTrigger } from '@/components/ui/tooltip'
import type { StudioPack, WorldInfo } from '../bridge/types'
import { Glyph } from './Glyph'
import type { StudioToggles } from './MapStudio'
import type { ShortcutAction } from './shortcuts'
import { useKeyText } from './useBindings'
import { IrisLogo } from './IrisLogo'
import { PackSwitcher } from './PackSwitcher'
import { badgeColour } from './packBadges'

/** How strongly the open pack's colour tints the bar behind its name, as IntelliJ tints its toolbar with the project colour. */
const PACK_TINT_PERCENT = 28

interface StudioToolbarProps {
  world: WorldInfo
  /** The pack the studio is switching to, while the server opens it. */
  openingPack: StudioPack | null
  loadPacks(): Promise<StudioPack[]>
  onOpenPack(pack: StudioPack): void
  onHelp(): void
  onSettings(): void
}

/** The window's bar: the pack the studio is on, which switches to another, and the studio's own buttons. */
export function StudioToolbar({ world, openingPack, loadPacks, onOpenPack, onHelp, onSettings }: StudioToolbarProps): React.JSX.Element {
  return (
    <header className="relative isolate flex h-10 shrink-0 items-center gap-1 px-1.5">
      <PackTint colour={badgeColour(openingPack?.key ?? world.dimension)} />
      <IrisLogo className="mx-[5px] size-5" />
      <PackSwitcher world={world} opening={openingPack} loadPacks={loadPacks} onOpenPack={onOpenPack} />
      <div className="flex-1" />
      <MainToolbarButton action="help" hint="Show every mouse and key shortcut" onClick={onHelp}>
        <Glyph glyph="help" large />
      </MainToolbarButton>
      <MainToolbarButton hint="Settings" onClick={onSettings}>
        <Glyph glyph="settings" large />
      </MainToolbarButton>
    </header>
  )
}

function PackTint({ colour }: { colour: string }): React.JSX.Element {
  return (
    <span
      aria-hidden
      className="pointer-events-none absolute inset-0 -z-10"
      style={{ background: `radial-gradient(560px 110px at 150px 0%, color-mix(in srgb, ${colour} ${PACK_TINT_PERCENT}%, transparent), transparent 75%)` }}
    />
  )
}

interface MapToolsProps {
  toggles: StudioToggles
  hasPlayers: boolean
  onToggle(name: keyof StudioToggles): void
  onRefresh(): void
  onResetZoom(): void
}

/** The map's own buttons, for the header of its island. */
export function MapTools({ toggles, hasPlayers, onToggle, onRefresh, onResetZoom }: MapToolsProps): React.JSX.Element {
  return (
    <>
      <ToolToggle action="grid" hint="Show chunk and region borders" pressed={toggles.grid} onPressed={() => onToggle('grid')}>
        <Glyph glyph="grid" />
      </ToolToggle>
      <ToolToggle action="borders" hint="Outline the regions on every view" pressed={toggles.regionBorders} onPressed={() => onToggle('regionBorders')}>
        <SquareDashed />
      </ToolToggle>
      <ToolToggle action="shading" hint="Shade hills and valleys" pressed={toggles.shading} onPressed={() => onToggle('shading')}>
        <Mountain />
      </ToolToggle>
      <ToolToggle action="blocks" hint="Show the blocks the world generates, zoomed in to a block a pixel" pressed={toggles.blocks} onPressed={() => onToggle('blocks')}>
        <Blocks />
      </ToolToggle>
      <Separator orientation="vertical" className="mx-1 h-4!" />
      <ToolToggle action="entities" hint="Show mobs and other entities" pressed={toggles.entities} disabled={!hasPlayers} onPressed={() => onToggle('entities')}>
        <PawPrint />
      </ToolToggle>
      <ToolToggle action="follow" hint="Keep your player in the middle" pressed={toggles.follow} disabled={!hasPlayers} onPressed={() => onToggle('follow')}>
        <Glyph glyph="locate" />
      </ToolToggle>
      <Separator orientation="vertical" className="mx-1 h-4!" />
      <ToolButton action="resetZoom" hint="Go back to the default zoom" onClick={onResetZoom}>
        <Glyph glyph="actualZoom" />
      </ToolButton>
      <ToolButton action="refresh" hint="Redraw the map with the latest pack files" onClick={onRefresh}>
        <Glyph glyph="refresh" />
      </ToolButton>
    </>
  )
}

function IconHint({ hint, keyText, children }: { hint: string; keyText?: string; children: ReactNode }): React.JSX.Element {
  return (
    <Tooltip>
      <TooltipTrigger asChild>{children}</TooltipTrigger>
      <TooltipContent className="flex items-center gap-2">
        {hint}
        {keyText !== undefined && keyText !== '' && <Kbd>{keyText}</Kbd>}
      </TooltipContent>
    </Tooltip>
  )
}

/** A button in the window's main toolbar, at IntelliJ's 30 px for its 20 px icons. */
function MainToolbarButton({ action, hint, onClick, children }: { action?: ShortcutAction; hint: string; onClick(): void; children: ReactNode }): React.JSX.Element {
  const keyText = useKeyText()
  return (
    <IconHint hint={hint} keyText={action === undefined ? undefined : keyText(action)}>
      <Button variant="ghost" size="icon-sm" className="size-[30px]" onClick={onClick}>
        {children}
      </Button>
    </IconHint>
  )
}

/** A button in an island's header, at the size of IntelliJ's tool window header actions. */
function ToolButton({ action, hint, onClick, children }: { action: ShortcutAction; hint: string; onClick(): void; children: ReactNode }): React.JSX.Element {
  const keyText = useKeyText()
  return (
    <IconHint hint={hint} keyText={keyText(action)}>
      <Button variant="ghost" size="icon-sm" className="size-6" onClick={onClick}>
        {children}
      </Button>
    </IconHint>
  )
}

interface ToolToggleProps {
  action: ShortcutAction
  hint: string
  pressed: boolean
  disabled?: boolean
  onPressed(): void
  children: ReactNode
}

function ToolToggle({ action, hint, pressed, disabled, onPressed, children }: ToolToggleProps): React.JSX.Element {
  const keyText = useKeyText()
  return (
    <IconHint hint={disabled ? 'This server has no players to show' : hint} keyText={keyText(action)}>
      <span>
        <Toggle
          size="sm"
          pressed={pressed}
          disabled={disabled}
          onPressedChange={onPressed}
          className="size-6 min-w-6 p-0 hover:bg-accent hover:text-foreground data-[state=on]:bg-selection data-[state=on]:text-foreground"
        >
          {children}
        </Toggle>
      </span>
    </IconHint>
  )
}

import type { ReactNode } from 'react'
import { Blocks, Mountain, PawPrint, SquareDashed } from 'lucide-react'
import { Button } from '@/components/ui/button'
import { Kbd } from '@/components/ui/kbd'
import { Separator } from '@/components/ui/separator'
import { Toggle } from '@/components/ui/toggle'
import { Tooltip, TooltipContent, TooltipTrigger } from '@/components/ui/tooltip'
import type { WorldInfo } from '../bridge/types'
import { Glyph } from './Glyph'
import type { StudioToggles } from './MapStudio'
import type { ShortcutAction } from './shortcuts'
import { useKeyText } from './useBindings'
import { IrisLogo } from './IrisLogo'

interface StudioToolbarProps {
  world: WorldInfo
  onHelp(): void
  onSettings(): void
  onDisconnect(): void
}

/** The window's bar: the pack the studio is connected to, which leads back to the world list, and the studio's own buttons. */
export function StudioToolbar({ world, onHelp, onSettings, onDisconnect }: StudioToolbarProps): React.JSX.Element {
  return (
    <header className="flex h-10 shrink-0 items-center gap-1 px-1.5">
      <IrisLogo className="mx-[5px] size-5" />
      <IconHint hint="Back to the world list">
        <Button variant="ghost" size="sm" className="h-[30px] max-w-[50%] gap-2 px-2 font-semibold" onClick={onDisconnect}>
          <span className="flex size-5 shrink-0 items-center justify-center rounded-[5px] bg-primary text-[11px] font-semibold text-primary-foreground uppercase">
            {world.dimensionName.charAt(0)}
          </span>
          <span className="truncate">{world.dimensionName}</span>
          {world.packVersion !== undefined && <span className="shrink-0 font-normal text-secondary-text tabular-nums">v{world.packVersion}</span>}
          <Glyph glyph="chevronDown" />
        </Button>
      </IconHint>
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

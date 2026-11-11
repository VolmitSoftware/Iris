import type { ReactNode } from 'react'
import { IdeIcon } from '@/components/IdeIcon'
import { Button } from '@/components/ui/button'
import { Kbd } from '@/components/ui/kbd'
import { Separator } from '@/components/ui/separator'
import { Toggle } from '@/components/ui/toggle'
import { Tooltip, TooltipContent, TooltipTrigger } from '@/components/ui/tooltip'
import type { ThemeChoice } from '../../../shared/bridge'
import type { WorldInfo } from '../bridge/types'
import type { StudioToggles } from './MapStudio'
import type { ShortcutAction } from './shortcuts'
import { useKeyText } from './useBindings'

interface StudioToolbarProps {
  world: WorldInfo
  theme: { choice: ThemeChoice; next: () => void }
  onHelp(): void
  onSettings(): void
  onDisconnect(): void
}

const THEME_NAMES: Record<ThemeChoice, string> = { system: 'Theme: system', light: 'Theme: light', dark: 'Theme: dark' }

/** The window's bar: the world the studio is connected to, which leads back to the world list, and the studio's own buttons. */
export function StudioToolbar({ world, theme, onHelp, onSettings, onDisconnect }: StudioToolbarProps): React.JSX.Element {
  return (
    <header className="flex h-10 shrink-0 items-center gap-1 px-2">
      <IconHint hint="Back to the world list">
        <Button variant="ghost" size="sm" className="h-7 max-w-[50%] gap-2 px-1.5 text-[13px] font-medium" onClick={onDisconnect}>
          <span className="flex size-5 shrink-0 items-center justify-center rounded-[5px] bg-primary text-[11px] font-semibold text-primary-foreground uppercase">
            {world.world.charAt(0)}
          </span>
          <span className="truncate">{world.world}</span>
          <span className="truncate font-normal text-secondary-text">{world.dimensionName}</span>
          <IdeIcon name="chevronRight" className="size-3.5 rotate-90" />
        </Button>
      </IconHint>
      <div className="flex-1" />
      <IconHint hint={THEME_NAMES[theme.choice]}>
        <Button variant="ghost" size="icon-sm" onClick={theme.next}>
          <IdeIcon name="theme" />
        </Button>
      </IconHint>
      <ToolButton action="help" hint="Show every mouse and key shortcut" onClick={onHelp}>
        <IdeIcon name="help" />
      </ToolButton>
      <IconHint hint="Change the studio's keys">
        <Button variant="ghost" size="icon-sm" onClick={onSettings}>
          <IdeIcon name="settings" />
        </Button>
      </IconHint>
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
        <IdeIcon name="grid" />
      </ToolToggle>
      <ToolToggle action="borders" hint="Outline the regions on every view" pressed={toggles.regionBorders} onPressed={() => onToggle('regionBorders')}>
        <IdeIcon name="views/regions" />
      </ToolToggle>
      <ToolToggle action="shading" hint="Shade hills and valleys" pressed={toggles.shading} onPressed={() => onToggle('shading')}>
        <IdeIcon name="shading" />
      </ToolToggle>
      <ToolToggle action="blocks" hint="Show the blocks the world generates, zoomed in to a block a pixel" pressed={toggles.blocks} onPressed={() => onToggle('blocks')}>
        <IdeIcon name="blocks" />
      </ToolToggle>
      <Separator orientation="vertical" className="mx-1 h-4!" />
      <ToolToggle action="entities" hint="Show mobs and other entities" pressed={toggles.entities} disabled={!hasPlayers} onPressed={() => onToggle('entities')}>
        <IdeIcon name="user" />
      </ToolToggle>
      <ToolToggle action="follow" hint="Keep your player in the middle" pressed={toggles.follow} disabled={!hasPlayers} onPressed={() => onToggle('follow')}>
        <IdeIcon name="locate" />
      </ToolToggle>
      <Separator orientation="vertical" className="mx-1 h-4!" />
      <ToolButton action="resetZoom" hint="Go back to the default zoom" onClick={onResetZoom}>
        <IdeIcon name="zoomIn" />
      </ToolButton>
      <ToolButton action="refresh" hint="Redraw the map with the latest pack files" onClick={onRefresh}>
        <IdeIcon name="refresh" />
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

function ToolButton({ action, hint, onClick, children }: { action: ShortcutAction; hint: string; onClick(): void; children: ReactNode }): React.JSX.Element {
  const keyText = useKeyText()
  return (
    <IconHint hint={hint} keyText={keyText(action)}>
      <Button variant="ghost" size="icon-sm" className="size-7" onClick={onClick}>
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
          className="size-7 min-w-7 p-0 hover:bg-accent hover:text-foreground data-[state=on]:bg-selection data-[state=on]:text-foreground [&_svg:not([class*='size-'])]:size-3.5"
        >
          {children}
        </Toggle>
      </span>
    </IconHint>
  )
}

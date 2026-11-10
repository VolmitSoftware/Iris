import { CircleHelp, Grid3x3, LocateFixed, LogOut, Monitor, Moon, Mountain, PawPrint, RefreshCw, Settings, Spline, Sun, ZoomIn } from 'lucide-react'
import type { ReactNode } from 'react'
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
  title: string
  world: WorldInfo
  toggles: StudioToggles
  hasPlayers: boolean
  /** Whether the map's buttons apply: a scene view has no map to redraw, zoom or overlay. */
  mapTools: boolean
  theme: { choice: ThemeChoice; next: () => void }
  onToggle(name: keyof StudioToggles): void
  onRefresh(): void
  onResetZoom(): void
  onHelp(): void
  onSettings(): void
  onDisconnect(): void
}

const THEME_ICONS: Record<ThemeChoice, ReactNode> = { system: <Monitor />, light: <Sun />, dark: <Moon /> }
const THEME_NAMES: Record<ThemeChoice, string> = { system: 'Theme: system', light: 'Theme: light', dark: 'Theme: dark' }

export function StudioToolbar(props: StudioToolbarProps): React.JSX.Element {
  return (
    <header className="flex h-10 shrink-0 items-center gap-1 border-b bg-card px-2">
      <span className="px-1.5 pr-3 text-[14px] font-semibold">{props.title}</span>
      {props.mapTools && <MapTools {...props} />}

      <div className="flex-1" />
      <span className="truncate px-2 text-[12px] text-secondary-text" title={`${props.world.dimensionName} · seed ${props.world.seed}`}>
        {props.world.world}
      </span>
      <IconHint hint={THEME_NAMES[props.theme.choice]}>
        <Button variant="ghost" size="icon-sm" onClick={props.theme.next}>
          {THEME_ICONS[props.theme.choice]}
        </Button>
      </IconHint>
      <ToolButton action="help" hint="Show every mouse and key shortcut" onClick={props.onHelp}>
        <CircleHelp />
      </ToolButton>
      <IconHint hint="Change the studio's keys">
        <Button variant="ghost" size="icon-sm" onClick={props.onSettings}>
          <Settings />
        </Button>
      </IconHint>
      <IconHint hint="Back to the world list">
        <Button variant="ghost" size="icon-sm" onClick={props.onDisconnect}>
          <LogOut />
        </Button>
      </IconHint>
    </header>
  )
}

function MapTools(props: StudioToolbarProps): React.JSX.Element {
  const { toggles, onToggle } = props
  return (
    <>
      <ToolButton action="refresh" hint="Redraw the map with the latest pack files" onClick={props.onRefresh}>
        <RefreshCw />
      </ToolButton>
      <ToolButton action="resetZoom" hint="Go back to the default zoom" onClick={props.onResetZoom}>
        <ZoomIn />
      </ToolButton>
      <ToolToggle action="grid" hint="Show chunk and region borders" pressed={toggles.grid} onPressed={() => onToggle('grid')}>
        <Grid3x3 /> Grid
      </ToolToggle>
      <ToolToggle action="entities" hint="Show mobs and other entities" pressed={toggles.entities} disabled={!props.hasPlayers} onPressed={() => onToggle('entities')}>
        <PawPrint /> Entities
      </ToolToggle>
      <ToolToggle action="follow" hint="Keep your player in the middle" pressed={toggles.follow} disabled={!props.hasPlayers} onPressed={() => onToggle('follow')}>
        <LocateFixed /> Follow
      </ToolToggle>
      <Separator orientation="vertical" className="mx-1 h-5! bg-input" />
      <ToolToggle action="shading" hint="Shade hills and valleys" pressed={toggles.shading} onPressed={() => onToggle('shading')}>
        <Mountain /> Shading
      </ToolToggle>
      <ToolToggle action="borders" hint="Outline the regions on every view" pressed={toggles.regionBorders} onPressed={() => onToggle('regionBorders')}>
        <Spline /> Borders
      </ToolToggle>
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
      <Button variant="ghost" size="icon-sm" onClick={onClick}>
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
          className="h-7 gap-1.5 px-2 text-[12.5px] font-normal data-[state=on]:bg-selection data-[state=on]:text-foreground [&_svg:not([class*='size-'])]:size-3.5"
        >
          {children}
        </Toggle>
      </span>
    </IconHint>
  )
}

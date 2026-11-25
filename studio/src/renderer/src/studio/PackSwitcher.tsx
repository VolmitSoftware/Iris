import { useState } from 'react'
import { Button } from '@/components/ui/button'
import { DropdownMenu, DropdownMenuContent, DropdownMenuItem, DropdownMenuTrigger } from '@/components/ui/dropdown-menu'
import { cn } from '@/lib/utils'
import type { StudioPack, WorldInfo } from '../bridge/types'
import { Glyph } from './Glyph'
import { badgeColour, badgeInitials } from './packBadges'

interface PackSwitcherProps {
  world: WorldInfo
  /** The pack the studio is switching to, while the server opens it. */
  opening: StudioPack | null
  loadPacks(): Promise<StudioPack[]>
  onOpenPack(pack: StudioPack): void
}

/** The open pack in the window's bar, which lists the server's other packs to switch the studio to, as IntelliJ's project widget does. */
export function PackSwitcher({ world, opening, loadPacks, onOpenPack }: PackSwitcherProps): React.JSX.Element {
  const [packs, setPacks] = useState<StudioPack[]>([])
  const openPack = opening ?? { key: world.dimension, name: world.dimensionName, version: world.packVersion }
  const otherPacks = packs.filter((pack) => pack.key !== world.dimension)
  const refreshPacks = (open: boolean): void => {
    if (!open) {
      return
    }
    loadPacks()
      .then(setPacks)
      .catch(() => setPacks([]))
  }
  return (
    <DropdownMenu onOpenChange={refreshPacks}>
      <DropdownMenuTrigger asChild disabled={opening !== null}>
        <Button variant="ghost" size="sm" className="h-[30px] max-w-[50%] gap-2 px-2 font-semibold data-[state=open]:bg-accent">
          <PackBadge pack={openPack} />
          <span className="truncate">{openPack.name}</span>
          {openPack.version !== undefined && <span className="shrink-0 font-normal text-secondary-text tabular-nums">v{openPack.version}</span>}
          <Glyph glyph={opening === null ? 'chevronDown' : 'refresh'} className={cn(opening !== null && 'animate-spin')} />
        </Button>
      </DropdownMenuTrigger>
      <DropdownMenuContent align="start" className="w-80 max-h-[min(640px,var(--radix-dropdown-menu-content-available-height))]">
        <SectionLabel>Open Pack</SectionLabel>
        <DropdownMenuItem className="bg-selection focus:bg-selection">
          <PackRow pack={openPack} />
        </DropdownMenuItem>
        {otherPacks.length > 0 && (
          <>
            <div className="-mx-1 my-1 h-px bg-border" />
            <SectionLabel>Packs</SectionLabel>
            {otherPacks.map((pack) => (
              <DropdownMenuItem key={pack.key} onSelect={() => onOpenPack(pack)}>
                <PackRow pack={pack} />
              </DropdownMenuItem>
            ))}
          </>
        )}
      </DropdownMenuContent>
    </DropdownMenu>
  )
}

/** A pack as the version may be unknown for: a server that does not send it. */
type ShownPack = Omit<StudioPack, 'version'> & { version?: number }

function PackRow({ pack }: { pack: ShownPack }): React.JSX.Element {
  return (
    <span className="flex min-w-0 flex-1 items-center gap-2.5 py-1">
      <PackBadge pack={pack} />
      <span className="flex min-w-0 flex-col">
        <span className="truncate">{pack.name}</span>
        <span className="truncate text-[11.5px] text-secondary-text">
          {pack.version === undefined ? pack.key : `${pack.key} · v${pack.version}`}
        </span>
      </span>
    </span>
  )
}

function SectionLabel({ children }: { children: React.ReactNode }): React.JSX.Element {
  return <p className="px-2 pt-1.5 pb-1 text-[11.5px] font-semibold text-muted-foreground">{children}</p>
}

function PackBadge({ pack }: { pack: ShownPack }): React.JSX.Element {
  return (
    <span
      className="flex h-5 min-w-5 shrink-0 items-center justify-center rounded-[5px] px-[3px] text-[10.5px] font-semibold text-white"
      style={{ backgroundColor: badgeColour(pack.key) }}
    >
      {badgeInitials(pack.name)}
    </span>
  )
}

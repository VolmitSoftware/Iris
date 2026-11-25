import { useState } from 'react'
import { Button } from '@/components/ui/button'
import { DropdownMenu, DropdownMenuContent, DropdownMenuItem, DropdownMenuTrigger } from '@/components/ui/dropdown-menu'
import { cn } from '@/lib/utils'
import type { StudioPack, WorldInfo } from '../bridge/types'
import { Glyph } from './Glyph'

interface PackSwitcherProps {
  world: WorldInfo
  /** The pack the studio is switching to, while the server opens it. */
  opening: StudioPack | null
  loadPacks(): Promise<StudioPack[]>
  onOpenPack(pack: StudioPack): void
  onDisconnect(): void
}

/** The open pack in the window's bar, which lists the server's packs to switch the studio to, as IntelliJ's project widget does. */
export function PackSwitcher({ world, opening, loadPacks, onOpenPack, onDisconnect }: PackSwitcherProps): React.JSX.Element {
  const [packs, setPacks] = useState<StudioPack[] | null>(null)
  const refreshPacks = (open: boolean): void => {
    if (!open) {
      return
    }
    loadPacks()
      .then(setPacks)
      .catch(() => setPacks([]))
  }
  const shownName = opening?.name ?? world.dimensionName
  const shownVersion = opening?.version ?? world.packVersion
  return (
    <DropdownMenu onOpenChange={refreshPacks}>
      <DropdownMenuTrigger asChild disabled={opening !== null}>
        <Button variant="ghost" size="sm" className="h-[30px] max-w-[50%] gap-2 px-2 font-semibold data-[state=open]:bg-accent">
          <PackBadge name={shownName} />
          <span className="truncate">{shownName}</span>
          {shownVersion !== undefined && <span className="shrink-0 font-normal text-secondary-text tabular-nums">v{shownVersion}</span>}
          <Glyph glyph={opening === null ? 'chevronDown' : 'refresh'} className={cn(opening !== null && 'animate-spin')} />
        </Button>
      </DropdownMenuTrigger>
      <DropdownMenuContent align="start" className="w-64">
        {packs === null && <p className="px-2 py-1 text-muted-foreground">Listing packs…</p>}
        {packs?.length === 0 && <p className="px-2 py-1 text-muted-foreground">No packs to switch to</p>}
        {packs?.map((pack) => {
          const current = pack.key === world.dimension
          return (
            <DropdownMenuItem key={pack.key} onSelect={() => !current && onOpenPack(pack)} className={cn(current && 'bg-selection')}>
              <PackBadge name={pack.name} />
              <span className="min-w-0 flex-1 truncate">{pack.name}</span>
              <span className="shrink-0 text-[11.5px] text-secondary-text tabular-nums">v{pack.version}</span>
            </DropdownMenuItem>
          )
        })}
        <div className="-mx-1 my-1 h-px bg-border" />
        <DropdownMenuItem onSelect={onDisconnect}>Disconnect</DropdownMenuItem>
      </DropdownMenuContent>
    </DropdownMenu>
  )
}

function PackBadge({ name }: { name: string }): React.JSX.Element {
  return (
    <span className="flex size-5 shrink-0 items-center justify-center rounded-[5px] bg-primary text-[11px] font-semibold text-primary-foreground uppercase">
      {name.charAt(0)}
    </span>
  )
}

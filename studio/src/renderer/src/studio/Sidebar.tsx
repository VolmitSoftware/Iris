import { createContext, useContext } from 'react'
import { createPortal } from 'react-dom'
import { Button } from '@/components/ui/button'
import { ToggleGroup, ToggleGroupItem } from '@/components/ui/toggle-group'
import { cn } from '@/lib/utils'
import { Glyph } from './Glyph'

/** Where a view puts its own lists: under the views in the left island. */
export const SidebarSlot = createContext<HTMLElement | null>(null)

/** A section of the left island under the views, filled by the view that is shown. */
export function SidebarPanel({ className, children }: { className?: string; children: React.ReactNode }): React.JSX.Element | null {
  const slot = useContext(SidebarSlot)
  if (slot === null) {
    return null
  }
  return createPortal(<section className={cn('flex min-h-0 flex-col overflow-hidden', className)}>{children}</section>, slot)
}

/** One choice of a switch in an island's header: plain until chosen and filled then. */
export const HEADER_CHOICE =
  'h-6 flex-none rounded-md! border border-transparent px-2 text-[12px] font-normal hover:bg-accent hover:text-foreground data-[state=on]:bg-secondary data-[state=on]:text-foreground'

interface StepperProps {
  /** Where the steps are now: "Variant 2 of 5". */
  label: string
  backHint: string
  onHint: string
  onBack(): void
  onOn(): void
}

/** Steps through numbered things from an island's header. */
export function Stepper({ label, backHint, onHint, onBack, onOn }: StepperProps): React.JSX.Element {
  return (
    <span className="flex items-center text-[12px] text-secondary-text tabular-nums">
      <Button variant="ghost" size="icon-sm" className="size-6" title={backHint} onClick={onBack}>
        <Glyph glyph="chevronLeft" />
      </Button>
      <span className="px-1">{label}</span>
      <Button variant="ghost" size="icon-sm" className="size-6" title={onHint} onClick={onOn}>
        <Glyph glyph="chevronRight" />
      </Button>
    </span>
  )
}

interface SizeChoiceProps {
  /** Each size, as blocks across, smallest first. */
  sizes: readonly number[]
  size: number
  onSize(size: number): void
}

/** Chooses how many blocks across an area is, from an island's header. */
export function SizeChoice({ sizes, size, onSize }: SizeChoiceProps): React.JSX.Element {
  return (
    <ToggleGroup type="single" size="sm" className="gap-0.5" value={String(size)} onValueChange={(value) => value !== '' && onSize(Number(value))}>
      {sizes.map((choice) => (
        <ToggleGroupItem key={choice} value={String(choice)} className={cn(HEADER_CHOICE, 'tabular-nums')} title={`${choice} × ${choice} blocks`}>
          {choice}
        </ToggleGroupItem>
      ))}
    </ToggleGroup>
  )
}

interface ViewIslandProps {
  title: string
  /** What the view shows now, such as the chosen entry, after the title in the muted colour. */
  subtitle?: string
  /** The view's own buttons, at the right of its header. */
  tools?: React.ReactNode
  className?: string
  children: React.ReactNode
}

/** The island a view draws in, under a header with its name and its tools, as an IntelliJ tool window has. */
export function ViewIsland({ title, subtitle, tools, className, children }: ViewIslandProps): React.JSX.Element {
  return (
    <section className={cn('flex min-h-0 min-w-0 flex-1 flex-col overflow-hidden rounded-[10px] bg-card', className)}>
      <header className="flex h-10 shrink-0 items-center gap-1 pr-1.5 pl-3">
        <h2 className="shrink-0 text-[13px] font-semibold">{title}</h2>
        {subtitle !== undefined && <span className="ml-1 truncate text-[12px] text-secondary-text">{subtitle}</span>}
        <span className="flex-1" />
        {tools}
      </header>
      <div className="relative min-h-0 flex-1 overflow-hidden">{children}</div>
    </section>
  )
}

/** A card in the shell: the panels sit apart on the window background instead of sharing borders. */
export function Panel({ className, children }: { className?: string; children: React.ReactNode }): React.JSX.Element {
  return <aside className={cn('flex shrink-0 flex-col overflow-hidden rounded-[10px] bg-card', className)}>{children}</aside>
}

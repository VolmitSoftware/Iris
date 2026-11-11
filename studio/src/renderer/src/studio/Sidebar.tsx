import { createContext, useContext } from 'react'
import { createPortal } from 'react-dom'
import { IdeIcon, type IdeIconName } from '@/components/IdeIcon'
import { Button } from '@/components/ui/button'
import { cn } from '@/lib/utils'

/** Where a view puts its own lists: under the views in the left island, as in v3e. */
export const SidebarSlot = createContext<HTMLElement | null>(null)

/** A section of the left island under the views, filled by the view that is shown. */
export function SidebarPanel({ className, children }: { className?: string; children: React.ReactNode }): React.JSX.Element | null {
  const slot = useContext(SidebarSlot)
  if (slot === null) {
    return null
  }
  return createPortal(<section className={cn('flex min-h-0 flex-col overflow-hidden', className)}>{children}</section>, slot)
}

/** One choice of a switch in an island's header: plain until chosen and outlined then, as IntelliJ's tabs are. */
export const HEADER_CHOICE =
  'h-7 flex-none rounded-md! border border-transparent px-2.5 text-[12.5px] font-normal hover:bg-accent hover:text-foreground data-[state=on]:border-input data-[state=on]:bg-transparent data-[state=on]:text-foreground'

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
      <Button variant="ghost" size="icon-sm" className="size-7" title={backHint} onClick={onBack}>
        <IdeIcon name="chevronLeft" />
      </Button>
      <span className="px-1">{label}</span>
      <Button variant="ghost" size="icon-sm" className="size-7" title={onHint} onClick={onOn}>
        <IdeIcon name="chevronRight" />
      </Button>
    </span>
  )
}

interface ViewIslandProps {
  title: string
  /** The view's own buttons, at the right of its header. */
  tools?: React.ReactNode
  className?: string
  children: React.ReactNode
}

/** The shown view's icon in the stripe, which its island repeats beside its name so the two are learned together. */
export const ShownViewIcon = createContext<IdeIconName | null>(null)

/** The island a view draws in, under a header with the view's icon, its name and its tools, as an IntelliJ tool window has. */
export function ViewIsland({ title, tools, className, children }: ViewIslandProps): React.JSX.Element {
  const icon = useContext(ShownViewIcon)
  return (
    <section className={cn('flex min-h-0 min-w-0 flex-1 flex-col overflow-hidden rounded-lg bg-card', className)}>
      <header className="flex h-9 shrink-0 items-center gap-1 pr-1.5 pl-3">
        {icon !== null && <IdeIcon name={icon} className="mr-1" />}
        <h2 className="truncate text-[13px] font-semibold">{title}</h2>
        <span className="flex-1" />
        {tools}
      </header>
      <div className="relative min-h-0 flex-1 overflow-hidden">{children}</div>
    </section>
  )
}

/** A card in the shell: the panels sit apart on the window background instead of sharing borders. */
export function Panel({ className, children }: { className?: string; children: React.ReactNode }): React.JSX.Element {
  return <aside className={cn('flex shrink-0 flex-col overflow-hidden rounded-lg bg-card', className)}>{children}</aside>
}

import { createContext, useContext } from 'react'
import { createPortal } from 'react-dom'
import { cn } from '@/lib/utils'

/** Where a view puts its own lists: under the views in the left column, as in v3e. */
export const SidebarSlot = createContext<HTMLElement | null>(null)

/** One card in the left column under the views, filled by the view that is shown. */
export function SidebarPanel({ className, children }: { className?: string; children: React.ReactNode }): React.JSX.Element | null {
  const slot = useContext(SidebarSlot)
  if (slot === null) {
    return null
  }
  return createPortal(<section className={cn('flex min-h-0 flex-col overflow-hidden rounded-lg bg-card', className)}>{children}</section>, slot)
}

/** A card in the shell: the panels sit apart on the window background instead of sharing borders. */
export function Panel({ className, children }: { className?: string; children: React.ReactNode }): React.JSX.Element {
  return <aside className={cn('flex shrink-0 flex-col overflow-hidden rounded-lg bg-card', className)}>{children}</aside>
}

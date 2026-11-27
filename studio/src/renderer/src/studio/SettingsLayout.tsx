import type { ReactNode } from 'react'
import { ScrollArea } from '@/components/ui/scroll-area'

interface SettingsPageProps {
  title: string
  description?: string
  /** Stays above the scrolling settings, such as a filter. */
  toolbar?: ReactNode
  /** Stays below the scrolling settings. */
  footer?: ReactNode
  children: ReactNode
}

/** One page of the settings: its title and what it is for, over its settings. */
export function SettingsPage({ title, description, toolbar, footer, children }: SettingsPageProps): React.JSX.Element {
  return (
    <section className="flex min-w-0 flex-1 flex-col">
      <header className="border-b px-6 pt-5 pb-4 pr-14">
        <h2 className="text-[15px] font-semibold">{title}</h2>
        {description !== undefined && <p className="mt-1 text-muted-foreground">{description}</p>}
      </header>
      {toolbar !== undefined && <div className="flex items-center gap-2 px-6 py-3">{toolbar}</div>}
      <ScrollArea className="min-h-0 flex-1">
        <div className="flex flex-col gap-5 px-6 py-4">{children}</div>
      </ScrollArea>
      {footer !== undefined && <footer className="flex h-11 shrink-0 items-center gap-2 border-t px-6">{footer}</footer>}
    </section>
  )
}

/** Settings that belong together, under a heading when the page has more than one group. */
export function SettingGroup({ title, children }: { title?: string; children: ReactNode }): React.JSX.Element {
  return (
    <section>
      {title !== undefined && <h3 className="mb-1 text-[12px] font-semibold text-muted-foreground">{title}</h3>}
      {children}
    </section>
  )
}

interface SettingRowProps {
  label: string
  detail?: string
  children: ReactNode
}

/** A setting's name, with what it does below it, and its control on the right. */
export function SettingRow({ label, detail, children }: SettingRowProps): React.JSX.Element {
  return (
    <div className="flex min-h-10 items-center gap-6 py-1">
      <div className="min-w-0 flex-1">
        <div>{label}</div>
        {detail !== undefined && <div className="text-[12px] text-muted-foreground">{detail}</div>}
      </div>
      <div className="flex shrink-0 items-center">{children}</div>
    </div>
  )
}

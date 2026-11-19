import { Dialog, DialogContent, DialogHeader, DialogTitle } from '@/components/ui/dialog'
import { Kbd } from '@/components/ui/kbd'
import type { ViewInfo } from '../bridge/types'
import { MOUSE_HINTS, keyTextOf, shortcutRows, type ShortcutGroup } from './shortcuts'
import { useBindings } from './useBindings'

interface HelpDialogProps {
  open: boolean
  onOpenChange(open: boolean): void
  views: ViewInfo[]
}

const KEY_GROUPS: ShortcutGroup[] = ['Views', 'Everywhere', 'Map', 'Node editor']

/** Every key and mouse gesture of the map, with the keys as they are set now. */
export function HelpDialog({ open, onOpenChange, views }: HelpDialogProps): React.JSX.Element {
  const bindings = useBindings()
  const rows = shortcutRows(views)
  const groups = [
    ...KEY_GROUPS.map((group) => ({
      title: group,
      rows: rows.filter((row) => row.group === group).map((row) => ({ key: keyTextOf(row.id, bindings), label: row.label })).filter((row) => row.key !== '')
    })),
    { title: 'Mouse', rows: MOUSE_HINTS.map((hint) => ({ key: hint.gesture, label: hint.label })) }
  ]
  return (
    <Dialog open={open} onOpenChange={onOpenChange}>
      <DialogContent className="max-w-[640px] bg-card">
        <DialogHeader>
          <DialogTitle className="text-[15px]">Shortcuts</DialogTitle>
        </DialogHeader>
        <div className="grid grid-cols-2 gap-x-8 gap-y-4">
          {groups.map((group) => (
            <section key={group.title}>
              <h3 className="mb-1.5 text-[11px] font-semibold text-muted-foreground">{group.title}</h3>
              <ul className="flex flex-col gap-1">
                {group.rows.map((row) => (
                  <li key={row.label} className="flex items-center justify-between gap-3">
                    <span className="text-secondary-text">{row.label}</span>
                    <Kbd>{row.key}</Kbd>
                  </li>
                ))}
              </ul>
            </section>
          ))}
        </div>
      </DialogContent>
    </Dialog>
  )
}

import { useEffect, useMemo, useState } from 'react'
import { Button } from '@/components/ui/button'
import { Dialog, DialogContent, DialogDescription, DialogFooter, DialogHeader, DialogTitle } from '@/components/ui/dialog'
import { cn } from '@/lib/utils'
import type { GraphicsInfo } from '../../../shared/bridge'
import type { ViewInfo } from '../bridge/types'
import { chordOf, chordText, chordsOf, chordsText, clashesWith, defaultChords, shortcutRows, type ShortcutGroup, type ShortcutRow } from './shortcuts'
import { shortcutStore, useBindings } from './useBindings'

interface ShortcutSettingsProps {
  open: boolean
  onOpenChange(open: boolean): void
  views: ViewInfo[]
}

interface Note {
  text: string
  warning: boolean
}

const GROUPS: ShortcutGroup[] = ['Views', 'Everywhere', 'Map', 'Node editor']
const RECORDING_TEXT = 'Press a key…'
const NO_KEYS_TEXT = 'None'

/** Every shortcut with its keys, rebound by clicking its keys and pressing the new one. Changes are saved at once. */
export function ShortcutSettings({ open, onOpenChange, views }: ShortcutSettingsProps): React.JSX.Element {
  const bindings = useBindings()
  const rows = useMemo(() => shortcutRows(views), [views])
  const [recording, setRecording] = useState<ShortcutRow | null>(null)
  const [note, setNote] = useState<Note | null>(null)

  useEffect(() => {
    if (!open) {
      setRecording(null)
    }
  }, [open])

  useEffect(() => {
    if (recording === null) {
      return
    }
    const record = (event: KeyboardEvent): void => {
      event.preventDefault()
      event.stopPropagation()
      const chord = chordOf(event)
      if (chord === null) {
        return
      }
      const clashes = clashesWith(recording, chord, rows, shortcutStore.snapshot())
      shortcutStore.rebind(recording.id, [chord])
      setRecording(null)
      setNote(
        clashes.length === 0
          ? { text: `${recording.label} is now ${chordText(chord)}`, warning: false }
          : { text: `${chordText(chord)} is also ${clashes.map((clash) => clash.label).join(', ')}`, warning: true }
      )
    }
    window.addEventListener('keydown', record, true)
    return () => window.removeEventListener('keydown', record, true)
  }, [recording, rows])

  return (
    <Dialog open={open} onOpenChange={onOpenChange}>
      <DialogContent className="max-w-[560px] bg-card">
        <DialogHeader>
          <DialogTitle className="text-[15px]">Settings</DialogTitle>
          <DialogDescription>Click a shortcut's keys, then press the new key. Esc can be chosen too; click the keys again to cancel.</DialogDescription>
        </DialogHeader>
        <div className="flex max-h-[60vh] flex-col gap-3 overflow-y-auto pr-1">
          <GraphicsSection />
          {GROUPS.map((group) => (
            <section key={group}>
              <h3 className="mb-1 text-[11px] font-semibold text-muted-foreground">{group}</h3>
              {rows
                .filter((row) => row.group === group)
                .map((row) => (
                  <ShortcutRowView
                    key={row.id}
                    row={row}
                    keys={chordsOf(row.id, bindings)}
                    changed={bindings[row.id] !== undefined}
                    recording={recording?.id === row.id}
                    onRecord={() => setRecording((current) => (current?.id === row.id ? null : row))}
                    onReset={() => {
                      setRecording(null)
                      shortcutStore.reset(row.id)
                    }}
                  />
                ))}
            </section>
          ))}
        </div>
        <DialogFooter className="items-center sm:justify-between">
          <span className={cn('min-w-0 flex-1 truncate text-[12px]', note?.warning ? 'text-warning' : 'text-secondary-text')}>{note?.text}</span>
          <Button
            variant="outline"
            size="sm"
            onClick={() => {
              setRecording(null)
              setNote(null)
              shortcutStore.resetAll()
            }}
          >
            Reset all
          </Button>
        </DialogFooter>
      </DialogContent>
    </Dialog>
  )
}

/** The GPU choice, shown only where there is an NVIDIA GPU to choose. Changing it restarts the studio. */
function GraphicsSection(): React.JSX.Element | null {
  const [graphics, setGraphics] = useState<GraphicsInfo | null>(null)

  useEffect(() => {
    void window.studio.graphics().then(setGraphics)
  }, [])

  if (graphics === null || !graphics.canUseDedicatedGpu) {
    return null
  }
  const dedicated = graphics.usesDedicatedGpu
  return (
    <section>
      <h3 className="mb-1 text-[11px] font-semibold text-muted-foreground">Graphics</h3>
      <div className="flex items-center gap-2 py-0.5">
        <span className="min-w-0 flex-1 truncate text-secondary-text">Draw with the NVIDIA GPU (restarts)</span>
        <Button variant="outline" size="xs" className="w-36" onClick={() => window.studio.useDedicatedGpu(!dedicated)}>
          {dedicated ? 'On' : 'Off'}
        </Button>
        <span className="w-14" />
      </div>
    </section>
  )
}

interface ShortcutRowViewProps {
  row: ShortcutRow
  keys: string[]
  changed: boolean
  recording: boolean
  onRecord(): void
  onReset(): void
}

function ShortcutRowView({ row, keys, changed, recording, onRecord, onReset }: ShortcutRowViewProps): React.JSX.Element {
  return (
    <div className="flex items-center gap-2 py-0.5">
      <span className="min-w-0 flex-1 truncate text-secondary-text" title={row.label}>
        {row.label}
      </span>
      <Button variant="outline" size="xs" className={cn('w-36', recording && 'border-ring')} onClick={onRecord} title="Click, then press the new key">
        {recording ? RECORDING_TEXT : keys.length === 0 ? NO_KEYS_TEXT : chordsText(keys)}
      </Button>
      <Button
        variant="link"
        size="xs"
        className={cn('w-14 text-link', !changed && 'invisible')}
        onClick={onReset}
        title={chordsText(defaultChords(row.id)) || NO_KEYS_TEXT}
      >
        Default
      </Button>
    </div>
  )
}

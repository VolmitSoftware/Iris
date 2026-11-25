import { useEffect, useMemo, useState } from 'react'
import { Button } from '@/components/ui/button'
import { Input } from '@/components/ui/input'
import { cn } from '@/lib/utils'
import type { ViewInfo } from '../bridge/types'
import { Glyph } from './Glyph'
import { chordOf, chordText, chordsOf, chordsText, clashesWith, defaultChords, shortcutRows, type Bindings, type ShortcutGroup, type ShortcutRow } from './shortcuts'
import { SettingGroup, SettingsPage } from './SettingsLayout'
import { shortcutStore, useBindings } from './useBindings'

interface Note {
  text: string
  warning: boolean
}

const GROUPS: ShortcutGroup[] = ['Views', 'Everywhere', 'Map', 'Node editor']
const RECORDING_TEXT = 'Press a key…'
const NO_KEYS_TEXT = 'None'

/** Every shortcut with its keys, rebound by clicking its keys and pressing the new one. Changes are saved at once. */
export function KeymapPage({ views }: { views: ViewInfo[] }): React.JSX.Element {
  const bindings = useBindings()
  const rows = useMemo(() => shortcutRows(views), [views])
  const [filter, setFilter] = useState('')
  const [recording, setRecording] = useState<ShortcutRow | null>(null)
  const [note, setNote] = useState<Note | null>(null)
  const shown = rows.filter((row) => matchesFilter(row, bindings, filter))

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

  const resetAll = (): void => {
    setRecording(null)
    setNote(null)
    shortcutStore.resetAll()
  }

  return (
    <SettingsPage
      title="Keymap"
      description="Click a shortcut's keys, then press the new key. Esc can be chosen too; click the keys again to cancel."
      toolbar={
        <div className="relative flex-1">
          <Glyph glyph="search" className="pointer-events-none absolute top-1/2 left-2 -translate-y-1/2" />
          <Input className="pl-7" placeholder="Filter by name or key" value={filter} onChange={(event) => setFilter(event.target.value)} />
        </div>
      }
      footer={
        <>
          <span className={cn('min-w-0 flex-1 truncate text-[12px]', note?.warning ? 'text-warning' : 'text-secondary-text')}>{note?.text}</span>
          <Button variant="outline" onClick={resetAll}>
            Reset all
          </Button>
        </>
      }
    >
      {shown.length === 0 && <p className="text-muted-foreground">No shortcut matches</p>}
      {GROUPS.map((group) => {
        const groupRows = shown.filter((row) => row.group === group)
        if (groupRows.length === 0) {
          return null
        }
        return (
          <SettingGroup key={group} title={group}>
            {groupRows.map((row) => (
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
          </SettingGroup>
        )
      })}
    </SettingsPage>
  )
}

function matchesFilter(row: ShortcutRow, bindings: Bindings, filter: string): boolean {
  const wanted = filter.trim().toLowerCase()
  if (wanted === '') {
    return true
  }
  return row.label.toLowerCase().includes(wanted) || chordsText(chordsOf(row.id, bindings)).toLowerCase().includes(wanted)
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
    <div className="-mx-2 flex items-center gap-2 rounded-md px-2 py-0.5 hover:bg-accent">
      <span className="min-w-0 flex-1 truncate" title={row.label}>
        {row.label}
      </span>
      <Button variant="outline" size="xs" className={cn('w-36 bg-card', recording && 'focused')} onClick={onRecord} title="Click, then press the new key">
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

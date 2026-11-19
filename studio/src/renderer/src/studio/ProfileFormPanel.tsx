import { useState } from 'react'
import { IdeIcon } from '@/components/IdeIcon'
import { Button } from '@/components/ui/button'
import { Input } from '@/components/ui/input'
import { ScrollArea } from '@/components/ui/scroll-area'
import { cn } from '@/lib/utils'
import type { ProfileField, ProfileFormState } from '../bridge/types'

interface ProfileFormPanelProps {
  state: ProfileFormState | null
  /** Called with the typed text, or null to go back to what the pack has. */
  onChange(name: string, value: string | null): void
  onResetAll(): void
  className?: string
}

/**
 * The settings of the chosen profile as a form beside its scene. What is typed here only changes the preview, so the
 * pack's files stay as they are; the header offers to put every value back.
 */
export function ProfileFormPanel({ state, onChange, onResetAll, className }: ProfileFormPanelProps): React.JSX.Element {
  const changes = state?.fields.filter((field) => field.changed).length ?? 0
  return (
    <section className={cn('flex min-h-0 flex-col', className)}>
      <div className="flex h-[30px] shrink-0 items-center px-4 pt-1">
        <h2 className="min-w-0 flex-1 truncate text-[11px] font-semibold text-muted-foreground">{state?.title ?? 'Settings'}</h2>
        {changes > 0 && (
          <Button variant="link" size="xs" className="h-auto p-0 text-[11.5px]" title="Put every value back to what the pack has" onClick={onResetAll}>
            Reset {changes}
          </Button>
        )}
      </div>
      <ScrollArea className="min-h-0 flex-1">
        <div className="px-4 pb-2">
          {state?.notes.map((note) => (
            <p key={note} className="pb-1 text-[11.5px] text-secondary-text">
              {note}
            </p>
          ))}
          {state?.fields.map((field) => (
            <FieldEditor key={`${field.name}:${String(field.value)}`} field={field} onChange={onChange} />
          ))}
        </div>
      </ScrollArea>
    </section>
  )
}

function FieldEditor({ field, onChange }: { field: ProfileField; onChange(name: string, value: string | null): void }): React.JSX.Element {
  return (
    <div className="py-0.5">
      <div className="flex h-7 items-center gap-2">
        <label className="min-w-0 flex-[4] truncate text-[12px]" title={field.description}>
          {field.name}
        </label>
        <div className="flex min-w-0 flex-[6] items-center gap-1">
          {field.kind === 'boolean' || field.kind === 'enum' ? <ChoiceInput field={field} onChange={onChange} /> : <NumberInput field={field} onChange={onChange} />}
          <Button variant="ghost" size="icon-sm" title="Back to the pack's value" className={cn('size-6 shrink-0', !field.changed && 'invisible')} onClick={() => onChange(field.name, null)}>
            <IdeIcon name="reset" className="size-3.5" />
          </Button>
        </div>
      </div>
    </div>
  )
}

function ChoiceInput({ field, onChange }: { field: ProfileField; onChange(name: string, value: string | null): void }): React.JSX.Element {
  const choices = field.kind === 'boolean' ? ['true', 'false'] : field.choices
  return (
    <select
      className="h-7 w-full min-w-0 rounded-md border border-input bg-transparent px-1.5 text-[12px] outline-none focus-visible:border-ring dark:bg-input/30"
      value={String(field.value)}
      title={field.description}
      onChange={(event) => onChange(field.name, event.target.value)}
    >
      {choices.map((choice) => (
        <option key={choice} value={choice}>
          {choice}
        </option>
      ))}
    </select>
  )
}

function NumberInput({ field, onChange }: { field: ProfileField; onChange(name: string, value: string | null): void }): React.JSX.Element {
  const shown = String(field.value)
  const [draft, setDraft] = useState(shown)
  const problem = problemWith(field, draft)
  const commit = (): void => {
    if (draft !== shown && problem === null) {
      onChange(field.name, draft.trim())
    }
  }
  return (
    <Input
      value={draft}
      inputMode="decimal"
      aria-invalid={problem !== null}
      title={problem ?? field.description}
      className="h-7 px-2 text-[12px] tabular-nums md:text-[12px]"
      onChange={(event) => setDraft(event.target.value)}
      onBlur={commit}
      onKeyDown={(event) => {
        if (event.key === 'Enter') {
          commit()
        } else if (event.key === 'Escape') {
          setDraft(shown)
        }
      }}
    />
  )
}

function problemWith(field: ProfileField, draft: string): string | null {
  const number = Number(draft)
  if (draft.trim() === '' || !Number.isFinite(number)) {
    return 'Not a number'
  }
  if (field.kind === 'integer' && !Number.isInteger(number)) {
    return 'Not a whole number'
  }
  if (field.minimum !== null && number < field.minimum) {
    return `At least ${field.minimum}`
  }
  if (field.maximum !== null && number > field.maximum) {
    return `At most ${field.maximum}`
  }
  return null
}

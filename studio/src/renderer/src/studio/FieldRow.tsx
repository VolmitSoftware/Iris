import { IdeIcon } from '@/components/IdeIcon'
import { Button } from '@/components/ui/button'
import { cn } from '@/lib/utils'
import type { SettingOption } from '../bridge/types'

export interface SettingFieldProps {
  option: SettingOption
  /** What the file sets it to; undefined when it is not set. */
  value: unknown
  locked: boolean
  onSet(literal: string): void
  onUnset(): void
}

/** A setting's name on the left and, on the right, what edits it, with the button that unsets it and why the draft is refused. */
export function FieldRow({ option, value, locked, onUnset, problem, children }: SettingFieldProps & { problem: string | null; children: React.ReactNode }): React.JSX.Element {
  return (
    <div className="py-0.5">
      <div className="flex h-7 items-center gap-2">
        <label className={cn('min-w-0 flex-[4] truncate text-[12px]', option.required && 'font-medium')} title={option.description}>
          {option.name}
        </label>
        <div className="flex min-w-0 flex-[6] items-center gap-1">
          {children}
          <Button variant="ghost" size="icon-sm" title="Unset" className={cn('size-6 shrink-0', value === undefined && 'invisible')} disabled={locked} onClick={onUnset}>
            <IdeIcon name="reset" className="size-3.5" />
          </Button>
        </div>
      </div>
      {problem !== null && <p className="pt-0.5 text-right text-[11px] text-destructive">{problem}</p>}
    </div>
  )
}

export function defaultLabel(option: SettingOption): string {
  if (option.defaultValue === null || option.defaultValue === undefined) {
    return option.required ? 'Required' : 'Not set'
  }
  const text = typeof option.defaultValue === 'string' ? option.defaultValue : JSON.stringify(option.defaultValue)
  return `Default ${text}`
}

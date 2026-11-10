import { useState } from 'react'
import { Input } from '@/components/ui/input'
import { cn } from '@/lib/utils'
import { completed, matching, wordBeingTyped } from '../editor/suggestions'

const NO_CHOICE = -1

type InputProps = Omit<React.ComponentProps<'input'>, 'value' | 'onChange'>

interface SuggestionInputProps extends InputProps {
  value: string
  onValue(value: string): void
  /** Whether the text is a list typed as {@code a, b}, which completes the item being typed. */
  isList: boolean
  /** Reads what the text may be completed with, when the field is first used; null for a field typed freely. */
  load: (() => Promise<string[]>) | null
}

/** A text field that offers what it may be typed as while it is typed; Arrow keys and Enter or Tab choose, a click chooses too. */
export function SuggestionInput({ value, onValue, isList, load, onKeyDown, onFocus, onBlur, className, ...props }: SuggestionInputProps): React.JSX.Element {
  const [choices, setChoices] = useState<string[] | null>(null)
  const [focused, setFocused] = useState(false)
  const [highlighted, setHighlighted] = useState(NO_CHOICE)
  const offered = focused && choices !== null ? matching(choices, wordBeingTyped(value, isList).typed) : []

  const pick = (choice: string): void => {
    onValue(completed(value, choice, isList))
    setHighlighted(NO_CHOICE)
  }

  return (
    <div className="relative min-w-0 flex-1">
      <Input
        {...props}
        value={value}
        className={className}
        onChange={(event) => {
          onValue(event.target.value)
          setHighlighted(NO_CHOICE)
        }}
        onFocus={(event) => {
          setFocused(true)
          if (load !== null && choices === null) {
            load()
              .then(setChoices)
              .catch(() => setChoices([]))
          }
          onFocus?.(event)
        }}
        onBlur={(event) => {
          setFocused(false)
          setHighlighted(NO_CHOICE)
          onBlur?.(event)
        }}
        onKeyDown={(event) => {
          if (offered.length > 0 && (event.key === 'ArrowDown' || event.key === 'ArrowUp')) {
            event.preventDefault()
            const step = event.key === 'ArrowDown' ? 1 : -1
            setHighlighted((current) => ((current + step + offered.length + 2) % (offered.length + 1)) - 1)
            return
          }
          if (highlighted >= 0 && highlighted < offered.length && (event.key === 'Enter' || event.key === 'Tab')) {
            event.preventDefault()
            pick(offered[highlighted])
            return
          }
          onKeyDown?.(event)
        }}
      />
      {offered.length > 0 && (
        <ul className="absolute top-full right-0 left-0 z-30 mt-0.5 overflow-hidden rounded-md border bg-popover py-0.5 text-popover-foreground shadow-md">
          {offered.map((choice, index) => (
            <li key={choice}>
              <button
                type="button"
                tabIndex={-1}
                className={cn('block w-full truncate px-2 py-1 text-left font-mono text-[11.5px]', index === highlighted ? 'bg-accent text-accent-foreground' : 'hover:bg-accent/60')}
                onMouseDown={(event) => {
                  event.preventDefault()
                  pick(choice)
                }}
              >
                {choice}
              </button>
            </li>
          ))}
        </ul>
      )}
    </div>
  )
}

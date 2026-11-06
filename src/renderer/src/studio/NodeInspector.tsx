import { RotateCcw } from 'lucide-react'
import { useState } from 'react'
import { Button } from '@/components/ui/button'
import { Input } from '@/components/ui/input'
import { ScrollArea } from '@/components/ui/scroll-area'
import { cn } from '@/lib/utils'
import type { EditorState, GraphEdit, GraphNode, NodeSettings, SettingOption, ValueKind } from '../bridge/types'
import { valueAt } from '../editor/jsonValues'

const NOT_SET = ''

interface NodeInspectorProps {
  node: GraphNode
  state: EditorState
  /** The file parsed, to read what each setting is set to. */
  values: unknown
  onEdit(edit: GraphEdit): void
}

/** The settings of the chosen node, a field per option of its schema type; an empty field leaves the option unset. */
export function NodeInspector({ node, state, values, onEdit }: NodeInspectorProps): React.JSX.Element {
  const locked = state.locked !== null
  return (
    <section className="flex min-h-0 flex-[7] flex-col">
      <header className="px-4 pt-2.5 pb-1.5">
        <h2 className="truncate text-[13px] font-semibold">{node.title}</h2>
        <p className="truncate text-[11.5px] text-secondary-text">{node.roles.length > 0 ? node.roles.join(', ') : node.kind}</p>
      </header>
      <ScrollArea className="min-h-0 flex-1 border-t">
        <div className="pb-3">
          {node.settings.map((section) => (
            <SettingsSection key={JSON.stringify(section.path) + section.type} section={section} state={state} values={values} locked={locked} onEdit={onEdit} />
          ))}
        </div>
      </ScrollArea>
    </section>
  )
}

function SettingsSection({
  section,
  state,
  values,
  locked,
  onEdit
}: {
  section: NodeSettings
  state: EditorState
  values: unknown
  locked: boolean
  onEdit(edit: GraphEdit): void
}): React.JSX.Element {
  const type = state.types[section.type]
  const options = type?.options.filter((option) => !section.shownAsNodes.includes(option.name)) ?? []
  return (
    <section className="px-4 pt-3">
      <h3 className="pb-1 text-[11px] font-semibold text-muted-foreground" title={type?.description}>
        {section.title}
      </h3>
      {options.map((option) => {
        const value = valueAt(values, [...section.path, option.name])
        return (
          <SettingField
            key={`${option.name}=${JSON.stringify(value)}`}
            option={option}
            value={value}
            locked={locked}
            onSet={(literal) => onEdit({ op: 'set', object: section.path, key: option.name, literal })}
            onUnset={() => onEdit({ op: 'unset', object: section.path, key: option.name })}
          />
        )
      })}
    </section>
  )
}

interface SettingFieldProps {
  option: SettingOption
  /** What the file sets it to; undefined when it is not set. */
  value: unknown
  locked: boolean
  onSet(literal: string): void
  onUnset(): void
}

function SettingField(props: SettingFieldProps): React.JSX.Element {
  const kind = props.option.kind.kind
  return kind === 'boolean' || kind === 'enum' ? <ChoiceField {...props} /> : <TypedField {...props} />
}

function FieldRow({ option, value, locked, onUnset, problem, children }: SettingFieldProps & { problem: string | null; children: React.ReactNode }): React.JSX.Element {
  return (
    <div className="py-0.5">
      <div className="flex h-7 items-center gap-2">
        <label className={cn('min-w-0 flex-[4] truncate text-[12px]', option.required && 'font-medium')} title={option.description}>
          {option.name}
        </label>
        <div className="flex min-w-0 flex-[6] items-center gap-1">
          {children}
          <Button variant="ghost" size="icon-sm" title="Unset" className={cn('size-6 shrink-0', value === undefined && 'invisible')} disabled={locked} onClick={onUnset}>
            <RotateCcw className="size-3.5" />
          </Button>
        </div>
      </div>
      {problem !== null && <p className="pt-0.5 text-right text-[11px] text-destructive">{problem}</p>}
    </div>
  )
}

function ChoiceField(props: SettingFieldProps): React.JSX.Element {
  const { option, value, locked, onSet, onUnset } = props
  const choices = option.kind.kind === 'enum' ? option.kind.constants.map((constant) => constant.name) : ['true', 'false']
  const literalOf = (choice: string): string => (option.kind.kind === 'enum' ? JSON.stringify(choice) : choice)
  return (
    <FieldRow {...props} problem={null}>
      <select
        className="h-7 w-full min-w-0 rounded-md border border-input bg-transparent px-1.5 text-[12px] outline-none focus-visible:border-ring dark:bg-input/30"
        value={value === undefined ? NOT_SET : String(value)}
        disabled={locked}
        title={option.description}
        onChange={(event) => (event.target.value === NOT_SET ? onUnset() : onSet(literalOf(event.target.value)))}
      >
        <option value={NOT_SET}>{defaultLabel(option)}</option>
        {choices.map((choice) => (
          <option key={choice} value={choice}>
            {choice}
          </option>
        ))}
      </select>
    </FieldRow>
  )
}

// Numbers, text and anything else are typed in; a draft that does not fit the option says why and is not written.
function TypedField(props: SettingFieldProps): React.JSX.Element {
  const { option, value, locked, onSet, onUnset } = props
  const shown = textOf(option.kind, value)
  const [draft, setDraft] = useState(shown)
  const problem = draft.trim() === NOT_SET ? null : problemWith(option, draft)
  const commit = (): void => {
    if (draft === shown || problem !== null) {
      return
    }
    if (draft.trim() === NOT_SET) {
      onUnset()
      return
    }
    onSet(literalOf(option.kind, draft))
  }
  return (
    <FieldRow {...props} problem={problem}>
      <Input
        value={draft}
        disabled={locked}
        placeholder={defaultLabel(option)}
        aria-invalid={problem !== null}
        title={option.description}
        inputMode={isNumber(option.kind) ? 'decimal' : undefined}
        className={cn('h-7 px-2 text-[12px] md:text-[12px]', isNumber(option.kind) ? 'tabular-nums' : !isText(option.kind) && 'font-mono text-[11.5px] placeholder:font-sans md:text-[11.5px]')}
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
    </FieldRow>
  )
}

function defaultLabel(option: SettingOption): string {
  if (option.defaultValue === null || option.defaultValue === undefined) {
    return option.required ? 'Required' : 'Not set'
  }
  const text = typeof option.defaultValue === 'string' ? option.defaultValue : JSON.stringify(option.defaultValue)
  return `Default ${text}`
}

function isNumber(kind: ValueKind): boolean {
  return kind.kind === 'integer' || kind.kind === 'decimal'
}

function isText(kind: ValueKind): boolean {
  return kind.kind === 'text' || kind.kind === 'reference' || kind.kind === 'resource'
}

function textOf(kind: ValueKind, value: unknown): string {
  if (value === undefined) {
    return NOT_SET
  }
  return isText(kind) && typeof value === 'string' ? value : JSON.stringify(value)
}

function literalOf(kind: ValueKind, draft: string): string {
  return isText(kind) ? JSON.stringify(draft) : draft.trim()
}

function problemWith(option: SettingOption, draft: string): string | null {
  if (isText(option.kind)) {
    return null
  }
  if (!isNumber(option.kind)) {
    return isJson(draft) ? null : 'Not JSON'
  }
  const number = Number(draft)
  if (draft.trim() === '' || !Number.isFinite(number)) {
    return 'Not a number'
  }
  if (option.kind.kind === 'integer' && !Number.isInteger(number)) {
    return 'Not a whole number'
  }
  if (option.minimum !== null && number < option.minimum) {
    return `At least ${option.minimum}`
  }
  if (option.maximum !== null && number > option.maximum) {
    return `At most ${option.maximum}`
  }
  return null
}

function isJson(text: string): boolean {
  try {
    JSON.parse(text)
    return true
  } catch {
    return false
  }
}

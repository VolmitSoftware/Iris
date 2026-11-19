import { IdeIcon } from '@/components/IdeIcon'
import { useMemo, useState } from 'react'
import { Button } from '@/components/ui/button'
import { ScrollArea } from '@/components/ui/scroll-area'
import { cn } from '@/lib/utils'
import type { EditorState, GraphEdit, GraphNode, NodeSettings, SettingOption, ValueKind } from '../bridge/types'
import { canBecomeCurve, curveFrom, isCurve } from '../editor/curves'
import { valueAt } from '../editor/jsonValues'
import { foldedSections, idOfFold, visibleFolds, type SettingsFold } from '../editor/settingFolds'
import { NOT_SET, isStringList, isText, literalOf, textOf } from '../editor/settingText'
import type { SuggestionStore } from '../editor/suggestionStore'
import { rolesOf, suggestionSourceOf, type SuggestionSource } from '../editor/suggestions'
import { CurveField } from './CurveField'
import { FieldRow, defaultLabel, type SettingFieldProps } from './FieldRow'
import { SuggestionInput } from './SuggestionInput'

const FOLD_INDENT_PX = 10

interface NodeInspectorProps {
  node: GraphNode
  state: EditorState
  /** The file parsed, to read what each setting is set to. */
  values: unknown
  suggestions: SuggestionStore
  onEdit(edit: GraphEdit): void
}

/** What the fields of a section share: the file and the edits made to it, and which folds and curves are open. */
interface SectionContext {
  state: EditorState
  values: unknown
  locked: boolean
  open: ReadonlySet<string>
  roles: string[]
  suggestions: SuggestionStore
  onToggle(id: string): void
  onEdit(edit: GraphEdit): void
}

/** The settings of the chosen node, a field per option of its schema type; an empty field leaves the option unset. */
export function NodeInspector({ node, state, values, suggestions, onEdit }: NodeInspectorProps): React.JSX.Element {
  const [open, setOpen] = useState<ReadonlySet<string>>(new Set())
  const roles = useMemo(() => rolesOf(state.roots ?? []), [state.roots])
  const context: SectionContext = {
    state,
    values,
    locked: state.locked !== null,
    open,
    roles,
    suggestions,
    onToggle: (id) =>
      setOpen((current) => {
        const next = new Set(current)
        if (!next.delete(id)) {
          next.add(id)
        }
        return next
      }),
    onEdit
  }
  return (
    <section className="flex min-h-0 flex-[7] flex-col">
      <header className="px-4 pt-2.5 pb-1.5">
        <h2 className="truncate text-[13px] font-semibold">{node.title}</h2>
        <p className="truncate text-[11.5px] text-secondary-text">{node.roles.length > 0 ? node.roles.join(', ') : node.kind}</p>
      </header>
      <ScrollArea className="min-h-0 flex-1 border-t">
        <div className="pb-3">
          {node.settings.map((section) => (
            <SettingsSection key={JSON.stringify(section.path) + section.type} section={section} context={context} />
          ))}
        </div>
      </ScrollArea>
    </section>
  )
}

function SettingsSection({ section, context }: { section: NodeSettings; context: SectionContext }): React.JSX.Element {
  const { state, values, open } = context
  const folds = visibleFolds(foldedSections(section, state.types, values), open)
  return (
    <section className="px-4 pt-3">
      {folds.map((fold) =>
        fold.kind === 'own' ? (
          <div key={idOfFold(fold)}>
            <h3 className="pb-1 text-[11px] font-semibold text-muted-foreground" title={state.types[fold.type]?.description}>
              {fold.title}
            </h3>
            <FoldFields fold={fold} context={context} />
          </div>
        ) : (
          <div key={idOfFold(fold)}>
            <FoldHeader fold={fold} context={context} />
            {open.has(idOfFold(fold)) && <FoldFields fold={fold} context={context} />}
          </div>
        )
      )}
    </section>
  )
}

function FoldHeader({ fold, context }: { fold: SettingsFold; context: SectionContext }): React.JSX.Element {
  const { open, locked, onToggle, onEdit } = context
  const id = idOfFold(fold)
  const unfolded = open.has(id)
  const holder = fold.holder
  return (
    <div className="flex h-7 items-center gap-1" style={{ paddingLeft: (fold.depth - 1) * FOLD_INDENT_PX }}>
      <button type="button" className="flex min-w-0 flex-1 items-center gap-1 text-left" onClick={() => onToggle(id)}>
        <IdeIcon name="chevronRight" className={cn('size-3.5 transition-transform', unfolded && 'rotate-90')} />
        <span className="shrink-0 text-[12px] font-medium">{fold.title}</span>
        {!unfolded && <span className="min-w-0 truncate text-[11px] text-secondary-text">{fold.summary}</span>}
      </button>
      {fold.kind === 'list' && (
        <Button variant="ghost" size="icon-sm" title="Add an item" className="size-6 shrink-0" disabled={locked} onClick={() => onEdit({ op: 'append', list: fold.path, literal: '{}' })}>
          <IdeIcon name="add" className="size-3.5" />
        </Button>
      )}
      {holder !== null && (
        <Button variant="ghost" size="icon-sm" title="Unset" className="size-6 shrink-0" disabled={locked} onClick={() => onEdit({ op: 'unset', object: holder.object, key: holder.key })}>
          <IdeIcon name="reset" className="size-3.5" />
        </Button>
      )}
      {fold.kind === 'item' && (
        <Button variant="ghost" size="icon-sm" title="Delete the item" className="size-6 shrink-0" disabled={locked} onClick={() => onEdit({ op: 'delete', path: fold.path })}>
          <IdeIcon name="delete" className="size-3.5" />
        </Button>
      )}
    </div>
  )
}

function FoldFields({ fold, context }: { fold: SettingsFold; context: SectionContext }): React.JSX.Element {
  const { values, locked, open, roles, suggestions, onToggle, onEdit } = context
  return (
    <div style={{ paddingLeft: fold.depth * FOLD_INDENT_PX }}>
      {fold.options.map((option) => {
        const value = valueAt(values, [...fold.path, option.name])
        const curveId = JSON.stringify([...fold.path, option.name])
        return (
          <SettingField
            key={`${idOfFold(fold)}/${option.name}=${JSON.stringify(value)}`}
            owner={fold.type}
            option={option}
            value={value}
            locked={locked}
            curveOpen={open.has(curveId)}
            onToggleCurve={() => onToggle(curveId)}
            loadSuggestions={(source) => (source.from === 'roles' ? Promise.resolve(roles) : suggestions.load(source))}
            onSet={(literal) => onEdit({ op: 'set', object: fold.path, key: option.name, literal })}
            onUnset={() => onEdit({ op: 'unset', object: fold.path, key: option.name })}
          />
        )
      })}
    </div>
  )
}

interface FieldProps extends SettingFieldProps {
  /** The schema type of the object the option is in. */
  owner: string
  curveOpen: boolean
  onToggleCurve(): void
  loadSuggestions(source: SuggestionSource): Promise<string[]>
}

function SettingField(props: FieldProps): React.JSX.Element {
  const kind = props.option.kind.kind
  if (kind === 'boolean' || kind === 'enum') {
    return <ChoiceField {...props} />
  }
  if (kind === 'any' && isCurve(props.value)) {
    return <CurveField {...props} open={props.curveOpen} onToggle={props.onToggleCurve} />
  }
  return <TypedField {...props} />
}

function ChoiceField(props: SettingFieldProps): React.JSX.Element {
  const { option, value, locked, onSet, onUnset } = props
  const choices = option.kind.kind === 'enum' ? option.kind.constants.map((constant) => constant.name) : ['true', 'false']
  const literalOfChoice = (choice: string): string => (option.kind.kind === 'enum' ? JSON.stringify(choice) : choice)
  return (
    <FieldRow {...props} problem={null}>
      <select
        className="h-7 w-full min-w-0 rounded-md border border-input bg-transparent px-1.5 text-[12px] outline-none focus-visible:border-ring dark:bg-input/30"
        value={value === undefined ? NOT_SET : String(value)}
        disabled={locked}
        title={option.description}
        onChange={(event) => (event.target.value === NOT_SET ? onUnset() : onSet(literalOfChoice(event.target.value)))}
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
// A number or range of an option that takes any JSON value can be made a curve, which a curve is an option's to take.
function TypedField(props: FieldProps): React.JSX.Element {
  const { option, value, locked, owner, onSet, onUnset, onToggleCurve, loadSuggestions } = props
  const shown = textOf(option.kind, value)
  const [draft, setDraft] = useState(shown)
  const problem = draft.trim() === NOT_SET ? null : problemWith(option, draft)
  const source = suggestionSourceOf(owner, option)
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
      <SuggestionInput
        value={draft}
        onValue={setDraft}
        isList={isStringList(option.kind)}
        load={source === null ? null : () => loadSuggestions(source)}
        disabled={locked}
        placeholder={defaultLabel(option)}
        aria-invalid={problem !== null}
        title={option.description}
        inputMode={isNumber(option.kind) ? 'decimal' : undefined}
        className={cn('h-7 px-2 text-[12px] md:text-[12px]', isNumber(option.kind) ? 'tabular-nums' : !isText(option.kind) && 'font-mono text-[11.5px] placeholder:font-sans md:text-[11.5px]')}
        onBlur={commit}
        onKeyDown={(event) => {
          if (event.key === 'Enter') {
            commit()
          } else if (event.key === 'Escape') {
            setDraft(shown)
          }
        }}
      />
      {option.kind.kind === 'any' && canBecomeCurve(value) && (
        <Button
          variant="ghost"
          size="icon-sm"
          title="Make it a curve"
          className="size-6 shrink-0"
          disabled={locked}
          onClick={() => {
            onSet(JSON.stringify(curveFrom(value)))
            onToggleCurve()
          }}
        >
          <IdeIcon name="views/noise" className="size-3.5" />
        </Button>
      )}
    </FieldRow>
  )
}

function isNumber(kind: ValueKind): boolean {
  return kind.kind === 'integer' || kind.kind === 'decimal'
}

function problemWith(option: SettingOption, draft: string): string | null {
  if (isText(option.kind) || isStringList(option.kind)) {
    return isStringList(option.kind) && draft.trim().startsWith('[') && !isJson(draft) ? 'Not JSON' : null
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

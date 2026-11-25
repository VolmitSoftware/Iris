import { Check, ChevronDown } from 'lucide-react'
import { useRef, useState } from 'react'
import { DropdownMenu, DropdownMenuContent, DropdownMenuItem, DropdownMenuTrigger } from '@/components/ui/dropdown-menu'
import { Popover, PopoverAnchor, PopoverContent } from '@/components/ui/popover'
import { cn } from '@/lib/utils'
import { curveLiteral, isCurve } from '../editor/curves'
import { NODE_ROW } from '../editor/nodeRows'
import { defaultText, isNumber, isStringList, literalOf, NOT_SET, problemWith, textOf } from '../editor/settingText'
import { suggestionSourceOf } from '../editor/suggestions'
import { CurveEditor, CurvePreview } from './CurveEditor'
import { useNodeEditing, type SettingRow } from './nodeEditing'
import { SuggestionInput } from './SuggestionInput'

export const ROW_LABEL_WIDTH = 86
export const ROW_INDENT = 10
const FIELD = 'flex h-5 w-full min-w-0 items-center gap-1.5 rounded-sm border border-input bg-muted px-1.5 text-left'
const EDITABLE_FIELD = 'hover:border-secondary-text'
const CURVE_HINT = 'Click to shape the curve · right-click to make it one number'

interface SettingRowProps {
  row: SettingRow
  /** Whether its value can be changed: the file can be written and the node has settings of its own. */
  editable: boolean
}

/** One setting of a node: its name, its value edited in place, and a control to remove it so it falls back to its default. */
export function NodeSettingRow({ row, editable }: SettingRowProps): React.JSX.Element {
  const { onEdit } = useNodeEditing()
  const { option, fold, value } = row
  const fallback = defaultText(option)
  return (
    <div className="flex items-center px-2" style={{ height: NODE_ROW }}>
      <span className="shrink-0 truncate pr-1 text-secondary-text" style={{ width: ROW_LABEL_WIDTH, paddingLeft: fold.depth * ROW_INDENT }} title={option.description || undefined}>
        {option.name}
      </span>
      <div className="ml-1 flex min-w-0 flex-1 items-center">
        <SettingValue row={row} editable={editable} />
      </div>
      <RowButton
        glyph="×"
        tone="remove"
        hidden={!editable || value === undefined}
        title={`Remove ${option.name}, so it goes back to ${fallback === NOT_SET ? 'empty' : fallback}`}
        onClick={() => onEdit({ op: 'unset', object: fold.path, key: option.name })}
      />
    </div>
  )
}

interface RowButtonProps {
  glyph: '×' | '+'
  tone: 'remove' | 'add'
  hidden?: boolean
  title: string
  onClick(): void
}

/** A menu in a node gives no focus back to what opened it, which would leave a focus ring on the node. */
export function keepFocusOnCanvas(event: Event): void {
  event.preventDefault()
}

/** The small round control at a row's right end: × removes, + adds. */
export function RowButton({ glyph, tone, hidden = false, title, onClick }: RowButtonProps): React.JSX.Element {
  return (
    <button
      type="button"
      data-node-control
      title={title}
      className={cn(
        'ml-1 flex size-4 shrink-0 items-center justify-center rounded-full text-[13px] leading-none',
        tone === 'remove' ? 'text-muted-foreground hover:bg-destructive/20 hover:text-destructive' : 'text-link hover:bg-link/20',
        hidden && 'invisible'
      )}
      onClick={onClick}
    >
      {glyph}
    </button>
  )
}

function SettingValue({ row, editable }: SettingRowProps): React.JSX.Element {
  const kind = row.option.kind.kind
  if (kind === 'boolean') {
    return <CheckValue row={row} editable={editable} />
  }
  if (kind === 'enum') {
    return <ChoiceValue row={row} editable={editable} />
  }
  if (kind === 'any' && isCurve(row.value)) {
    return <CurveValue row={row} editable={editable} />
  }
  return <TypedValue row={row} editable={editable} />
}

/** The value as the field shows it, or the default it falls back to, dimmed, while it is unset. */
function ShownText({ row }: { row: SettingRow }): React.JSX.Element {
  const isSet = row.value !== undefined
  const text = isSet ? textOf(row.option.kind, row.value) : defaultText(row.option)
  return <span className={cn('min-w-0 flex-1 truncate', isSet ? 'text-foreground' : 'text-muted-foreground', isNumber(row.option.kind) && 'tabular-nums')}>{text}</span>
}

function CheckValue({ row, editable }: SettingRowProps): React.JSX.Element {
  const { onEdit } = useNodeEditing()
  const checked = (row.value ?? row.option.defaultValue) === true
  return (
    <button
      type="button"
      data-node-control
      role="checkbox"
      aria-checked={checked}
      disabled={!editable}
      title={row.value === undefined ? 'Default' : undefined}
      className={cn('flex size-3.5 items-center justify-center rounded-[4px] border', checked ? 'border-primary bg-primary' : 'border-input bg-muted', editable && 'hover:border-primary', row.value === undefined && 'opacity-60')}
      onClick={() => onEdit({ op: 'set', object: row.fold.path, key: row.option.name, literal: String(!checked) })}
    >
      {checked && <Check className="size-3 text-primary-foreground" />}
    </button>
  )
}

function ChoiceValue({ row, editable }: SettingRowProps): React.JSX.Element {
  const { onEdit } = useNodeEditing()
  const constants = row.option.kind.kind === 'enum' ? row.option.kind.constants : []
  const current = row.value ?? row.option.defaultValue
  return (
    <DropdownMenu modal={false}>
      <DropdownMenuTrigger asChild disabled={!editable}>
        <button type="button" data-node-control className={cn(FIELD, editable && EDITABLE_FIELD)}>
          <ShownText row={row} />
          <ChevronDown className="size-3 text-secondary-text" />
        </button>
      </DropdownMenuTrigger>
      <DropdownMenuContent align="start" onCloseAutoFocus={keepFocusOnCanvas}>
        {constants.map((constant) => (
          <DropdownMenuItem
            key={constant.name}
            title={constant.description || undefined}
            className={cn(constant.name === current && 'font-semibold')}
            onSelect={() => onEdit({ op: 'set', object: row.fold.path, key: row.option.name, literal: JSON.stringify(constant.name) })}
          >
            {constant.name}
          </DropdownMenuItem>
        ))}
      </DropdownMenuContent>
    </DropdownMenu>
  )
}

function CurveValue({ row, editable }: SettingRowProps): React.JSX.Element {
  const { openCurve, onOpenCurve, onEdit, onSettingMenu } = useNodeEditing()
  return (
    <Popover open={openCurve === row.id} onOpenChange={(open) => onOpenCurve(open ? row.id : null)}>
      <PopoverAnchor asChild>
        <button
          type="button"
          data-node-control
          title={CURVE_HINT}
          className={cn(FIELD, editable && EDITABLE_FIELD)}
          onClick={() => onOpenCurve(row.id)}
          onContextMenu={() => onSettingMenu(row)}
        >
          <CurvePreview value={row.value} />
        </button>
      </PopoverAnchor>
      <PopoverContent onOpenAutoFocus={(event) => event.preventDefault()}>
        <CurveEditor value={row.value} locked={!editable} onWrite={(next) => onEdit({ op: 'set', object: row.fold.path, key: row.option.name, literal: curveLiteral(next) })} />
      </PopoverContent>
    </Popover>
  )
}

// Clicked, the value turns into a text field over the same spot until it is saved or given up.
function TypedValue({ row, editable }: SettingRowProps): React.JSX.Element {
  const { onSettingMenu } = useNodeEditing()
  const [typing, setTyping] = useState(false)
  if (typing) {
    return <ValueInput row={row} onDone={() => setTyping(false)} />
  }
  return (
    <button
      type="button"
      data-node-control
      disabled={!editable}
      title={row.value === undefined ? 'Default' : textOf(row.option.kind, row.value)}
      className={cn(FIELD, editable && cn(EDITABLE_FIELD, 'cursor-text'))}
      onClick={() => setTyping(true)}
      onContextMenu={() => onSettingMenu(row)}
    >
      <ShownText row={row} />
    </button>
  )
}

/**
 * The value being typed. Enter saves it, or keeps it open to fix with why it was refused; leaving the field saves it
 * too, or puts back what was saved when it is refused. Escape gives it up, and an emptied value is removed.
 */
function ValueInput({ row, onDone }: { row: SettingRow; onDone(): void }): React.JSX.Element {
  const { onEdit, loadSuggestions } = useNodeEditing()
  const { option, fold, value } = row
  const saved = textOf(option.kind, value)
  const [draft, setDraft] = useState(saved)
  const [shownProblem, setShownProblem] = useState<string | null>(null)
  const finished = useRef(false)
  const source = suggestionSourceOf(fold.type, option)

  // The field leaving the page can blur it once more, which must not save a second time.
  const finish = (): void => {
    finished.current = true
    onDone()
  }

  const commit = (stayOnRefusal: boolean): void => {
    if (finished.current) {
      return
    }
    const problem = problemWith(option, draft)
    if (problem !== null && stayOnRefusal) {
      setShownProblem(problem)
      return
    }
    finish()
    if (problem !== null || draft.trim() === saved.trim()) {
      return
    }
    if (draft.trim() === NOT_SET) {
      onEdit({ op: 'unset', object: fold.path, key: option.name })
      return
    }
    onEdit({ op: 'set', object: fold.path, key: option.name, literal: literalOf(option.kind, draft) })
  }

  return (
    <div className="relative min-w-0 flex-1" data-node-control>
      <SuggestionInput
        value={draft}
        onValue={(next) => {
          setDraft(next)
          setShownProblem(null)
        }}
        isList={isStringList(option.kind)}
        load={source === null ? null : () => loadSuggestions(source)}
        autoFocus
        placeholder={defaultText(option)}
        aria-invalid={shownProblem !== null}
        inputMode={isNumber(option.kind) ? 'decimal' : undefined}
        className="h-5 rounded-sm bg-popover px-1.5 py-0 text-[11.5px] shadow-none focus-visible:ring-2 md:text-[11.5px] dark:bg-popover"
        onFocus={(event) => event.currentTarget.select()}
        onBlur={() => commit(false)}
        onKeyDown={(event) => {
          if (event.key === 'Enter') {
            commit(true)
          } else if (event.key === 'Escape') {
            event.stopPropagation()
            finish()
          }
        }}
      />
      {shownProblem !== null && (
        <p className="absolute top-full left-0 z-30 mt-1 w-max max-w-[260px] rounded-sm border border-destructive bg-popover px-1.5 py-1 text-[11px] whitespace-normal text-foreground shadow-md">
          {shownProblem}
        </p>
      )}
    </div>
  )
}

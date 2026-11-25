import { ChevronRight } from 'lucide-react'
import { DropdownMenu, DropdownMenuContent, DropdownMenuItem, DropdownMenuSub, DropdownMenuSubContent, DropdownMenuSubTrigger, DropdownMenuTrigger } from '@/components/ui/dropdown-menu'
import { cn } from '@/lib/utils'
import type { SettingOption } from '../bridge/types'
import { NODE_HEADING, NODE_ROW, type NodeRow } from '../editor/nodeRows'
import { idOfFold, type SettingsFold } from '../editor/settingFolds'
import { defaultLiteral, defaultText } from '../editor/settingText'
import { useNodeEditing } from './nodeEditing'
import { keepFocusOnCanvas, NodeSettingRow, ROW_INDENT, RowButton } from './NodeSettingRow'

const ADD_SETTING_TEXT = '+ Setting'

interface NodeRowViewProps {
  row: NodeRow
  addable: SettingsFold[]
  editable: boolean
}

export function NodeRowView({ row, addable, editable }: NodeRowViewProps): React.JSX.Element {
  switch (row.kind) {
    case 'heading':
      return <HeadingRow title={row.fold.title} />
    case 'fold':
      return <FoldRow fold={row.fold} open={row.open} editable={editable} />
    case 'setting':
      return <NodeSettingRow row={row} editable={editable} />
    case 'addSetting':
      return <AddSettingRow addable={addable} />
  }
}

function HeadingRow({ title }: { title: string }): React.JSX.Element {
  return (
    <div className="flex items-end px-2 pb-0.5 text-[11px] text-muted-foreground" style={{ height: NODE_HEADING }}>
      {title}
    </div>
  )
}

// ▸ palette  zoom 2 · palette 3   folded;  ▾ palette  open. A list gets a + to add an element, an element a × to remove it.
function FoldRow({ fold, open, editable }: { fold: SettingsFold; open: boolean; editable: boolean }): React.JSX.Element {
  const { onToggleFold, onEdit } = useNodeEditing()
  const holder = fold.holder
  const showsSummary = !open || fold.kind === 'list'
  return (
    <div className="flex items-center px-2" style={{ height: NODE_ROW }}>
      <button
        type="button"
        data-node-control
        className="group flex min-w-0 flex-1 items-center gap-1 text-left"
        style={{ paddingLeft: (fold.depth - 1) * ROW_INDENT }}
        onClick={() => onToggleFold(idOfFold(fold))}
      >
        <ChevronRight className={cn('size-3 text-secondary-text group-hover:text-foreground', open && 'rotate-90')} />
        <span className="shrink-0">{fold.kind === 'item' ? `#${fold.title}` : fold.title}</span>
        {showsSummary && <span className="ml-1 min-w-0 truncate text-muted-foreground">{fold.summary}</span>}
      </button>
      {fold.kind === 'list' && <RowButton glyph="+" tone="add" hidden={!editable} title="Add one more, like the last" onClick={() => onEdit({ op: 'addItem', list: fold.path })} />}
      {fold.kind === 'item' && <RowButton glyph="×" tone="remove" hidden={!editable} title={`Remove #${fold.title}`} onClick={() => onEdit({ op: 'delete', path: fold.path })} />}
      {fold.kind === 'group' && holder !== null && (
        <RowButton glyph="×" tone="remove" hidden={!editable} title={`Remove ${fold.title}`} onClick={() => onEdit({ op: 'unset', object: holder.object, key: holder.key })} />
      )}
    </div>
  )
}

/** Offers the settings left at their default, a submenu per section when more than one has some. */
function AddSettingRow({ addable }: { addable: SettingsFold[] }): React.JSX.Element {
  return (
    <div className="flex items-center px-2" style={{ height: NODE_ROW }}>
      <DropdownMenu modal={false}>
        <DropdownMenuTrigger asChild>
          <button type="button" data-node-control title="Set one of the settings left at its default" className="text-link hover:underline">
            {ADD_SETTING_TEXT}
          </button>
        </DropdownMenuTrigger>
        <DropdownMenuContent align="start" className="min-w-[180px]" onCloseAutoFocus={keepFocusOnCanvas}>
          {addable.length === 1 ? (
            <UnsetItems fold={addable[0]} />
          ) : (
            addable.map((fold) => (
              <DropdownMenuSub key={idOfFold(fold)}>
                <DropdownMenuSubTrigger>{sectionLabel(fold)}</DropdownMenuSubTrigger>
                <DropdownMenuSubContent className="min-w-[180px]">
                  <UnsetItems fold={fold} />
                </DropdownMenuSubContent>
              </DropdownMenuSub>
            ))
          )}
        </DropdownMenuContent>
      </DropdownMenu>
    </div>
  )
}

function UnsetItems({ fold }: { fold: SettingsFold }): React.JSX.Element {
  const { onEdit } = useNodeEditing()
  const add = (option: SettingOption): void => onEdit({ op: 'set', object: fold.path, key: option.name, literal: defaultLiteral(option) })
  return (
    <>
      {fold.unset.map((option) => (
        <DropdownMenuItem key={option.name} title={option.description || undefined} onSelect={() => add(option)}>
          {option.name}
          <span className="ml-auto max-w-[120px] truncate pl-4 text-muted-foreground">{defaultText(option)}</span>
        </DropdownMenuItem>
      ))}
    </>
  )
}

function sectionLabel(fold: SettingsFold): string {
  return fold.kind === 'item' ? `${String(fold.path.at(-2))} #${fold.title}` : fold.title
}

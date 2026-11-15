import { ContextMenuContent, ContextMenuItem, ContextMenuSeparator, ContextMenuShortcut, ContextMenuSub, ContextMenuSubContent, ContextMenuSubTrigger } from '@/components/ui/context-menu'
import type { GraphEdit, GraphNode } from '../bridge/types'
import { canBecomeCurve, curveFrom, isCurve, roundedNumber, samplesOf } from '../editor/curves'
import { idOf } from '../editor/nodeLayout'
import type { SettingRow } from './nodeEditing'
import { useKeyText } from './useBindings'

const ADD_PREFIX = 'Add '

/** What a right-click is on: a node, and the setting under the pointer if any; or the background, with the file's first node. */
export type MenuTarget = { on: 'node'; node: GraphNode; setting: SettingRow | null } | { on: 'background'; root: GraphNode | null }

interface NodeMenuProps {
  target: MenuTarget
  locked: boolean
  onEdit(edit: GraphEdit): void
  onOpenCurve(rowId: string): void
  onArrange(): void
  onShowAll(): void
  onResetZoom(): void
}

export function NodeMenu(props: NodeMenuProps): React.JSX.Element {
  const { target } = props
  return <ContextMenuContent className="min-w-[200px]">{target.on === 'node' ? <NodeItems {...props} node={target.node} setting={target.setting} /> : <BackgroundItems {...props} root={target.root} />}</ContextMenuContent>
}

function NodeItems({ node, setting, locked, onEdit, onOpenCurve }: NodeMenuProps & { node: GraphNode; setting: SettingRow | null }): React.JSX.Element {
  const keyText = useKeyText()
  const isRoot = node.path.length === 0
  return (
    <>
      {setting !== null && <CurveItems setting={setting} locked={locked} onEdit={onEdit} onOpenCurve={onOpenCurve} />}
      <AdditionItems node={node} locked={locked} onEdit={onEdit} />
      {!isRoot && (
        <>
          {node.additions.length > 0 && <ContextMenuSeparator />}
          <ContextMenuItem disabled={locked || !node.duplicable} onSelect={() => onEdit({ op: 'duplicate', path: node.path })}>
            Duplicate
            <ContextMenuShortcut>{keyText('duplicateNode')}</ContextMenuShortcut>
          </ContextMenuItem>
          <ContextMenuItem variant="destructive" disabled={locked || !node.duplicable} onSelect={() => onEdit({ op: 'delete', path: node.path })}>
            {node.children.length === 0 ? 'Delete' : 'Delete with what grows from it'}
            <ContextMenuShortcut>{keyText('deleteNode')}</ContextMenuShortcut>
          </ContextMenuItem>
        </>
      )}
    </>
  )
}

// A value that runs along a part can be turned into a curve and back, at the top of its node's menu.
function CurveItems({ setting, locked, onEdit, onOpenCurve }: { setting: SettingRow; locked: boolean; onEdit(edit: GraphEdit): void; onOpenCurve(rowId: string): void }): React.JSX.Element | null {
  const { option, fold, value } = setting
  const current = value ?? option.defaultValue
  if (option.kind.kind !== 'any' || (!isCurve(current) && !canBecomeCurve(current))) {
    return null
  }
  const set = (literal: string): void => onEdit({ op: 'set', object: fold.path, key: option.name, literal })
  const makeCurve = (): void => {
    set(JSON.stringify(curveFrom(current)))
    onOpenCurve(setting.id)
  }
  return (
    <>
      {isCurve(current) ? (
        <ContextMenuItem disabled={locked} onSelect={() => set(roundedNumber(samplesOf(current, 1)![0]))}>
          Make it one number
        </ContextMenuItem>
      ) : (
        <ContextMenuItem disabled={locked} onSelect={makeCurve}>
          Make it a curve
        </ContextMenuItem>
      )}
      <ContextMenuSeparator />
    </>
  )
}

/**
 * What can be added under the node: the parts it can grow in one submenu, anything else, like the object's materials
 * and decorations, as an item each.
 */
function AdditionItems({ node, locked, onEdit }: { node: GraphNode; locked: boolean; onEdit(edit: GraphEdit): void }): React.JSX.Element {
  const byList = new Map<string, GraphNode['additions']>()
  for (const addition of node.additions) {
    byList.set(idOf(addition.list), [...(byList.get(idOf(addition.list)) ?? []), addition])
  }
  const partsTitle = node.path.length === 0 ? 'Add part' : 'Add part growing from this'
  return (
    <>
      {[...byList.entries()].map(([list, additions]) =>
        additions.length > 1 ? (
          <ContextMenuSub key={list}>
            <ContextMenuSubTrigger disabled={locked}>{partsTitle}</ContextMenuSubTrigger>
            <ContextMenuSubContent>
              {additions.map((addition) => (
                <ContextMenuItem key={addition.label} onSelect={() => onEdit({ op: 'append', list: addition.list, literal: addition.literal })}>
                  {addition.label}
                </ContextMenuItem>
              ))}
            </ContextMenuSubContent>
          </ContextMenuSub>
        ) : (
          <ContextMenuItem key={list} disabled={locked} onSelect={() => onEdit({ op: 'append', list: additions[0].list, literal: additions[0].literal })}>
            {ADD_PREFIX + additions[0].label.toLowerCase()}
          </ContextMenuItem>
        )
      )}
    </>
  )
}

function BackgroundItems({ root, locked, onEdit, onArrange, onShowAll, onResetZoom }: NodeMenuProps & { root: GraphNode | null }): React.JSX.Element {
  const keyText = useKeyText()
  return (
    <>
      {root !== null && root.additions.length > 0 && (
        <>
          <AdditionItems node={root} locked={locked} onEdit={onEdit} />
          <ContextMenuSeparator />
        </>
      )}
      <ContextMenuItem onSelect={onArrange}>Arrange nodes</ContextMenuItem>
      <ContextMenuItem onSelect={onShowAll}>
        Show every node
        <ContextMenuShortcut>{keyText('showAllNodes')}</ContextMenuShortcut>
      </ContextMenuItem>
      <ContextMenuItem onSelect={onResetZoom}>
        Reset zoom
        <ContextMenuShortcut>{keyText('resetZoom')}</ContextMenuShortcut>
      </ContextMenuItem>
    </>
  )
}

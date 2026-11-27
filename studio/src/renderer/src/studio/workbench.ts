/** What a tab shows: a view, and for a view with entries the entry, or null until the view settles on one. */
export interface TabSpec {
  viewId: string
  entry: string | null
}

/** A tab of a pane. A preview tab is the one a click in a list reuses; it becomes a kept tab once it is used. */
export interface Tab extends TabSpec {
  id: string
  preview: boolean
  /** What the tab is called once its view named it, such as its entry's name. */
  title: string | null
}

export interface Pane {
  kind: 'pane'
  id: string
  tabs: Tab[]
  activeId: string
}

export type SplitDirection = 'row' | 'column'

/** Panes side by side (a row) or above each other (a column), each with its share of the room, the shares adding up to 1. */
export interface Split {
  kind: 'split'
  id: string
  direction: SplitDirection
  children: LayoutNode[]
  sizes: number[]
}

export type LayoutNode = Pane | Split

export type Edge = 'left' | 'right' | 'top' | 'bottom'

/** The editor area: its panes, the one last clicked, and every tab from the most recently used. */
export interface Workbench {
  root: LayoutNode
  focusedPaneId: string
  used: string[]
  serial: number
}

/** IntelliJ's default: a pane closes the tab used longest ago when more than this many are open. */
export const TAB_LIMIT = 10
const SMALLEST_SHARE = 0.08

export function workbenchWith(spec: TabSpec): Workbench {
  const tab: Tab = { ...spec, id: 'tab-1', preview: false, title: null }
  const pane: Pane = { kind: 'pane', id: 'pane-2', tabs: [tab], activeId: tab.id }
  return { root: pane, focusedPaneId: pane.id, used: [tab.id], serial: 2 }
}

export function panesOf(node: LayoutNode): Pane[] {
  return node.kind === 'pane' ? [node] : node.children.flatMap(panesOf)
}

export function paneById(workbench: Workbench, paneId: string): Pane | null {
  return panesOf(workbench.root).find((pane) => pane.id === paneId) ?? null
}

export function paneOfTab(workbench: Workbench, tabId: string): Pane | null {
  return panesOf(workbench.root).find((pane) => pane.tabs.some((tab) => tab.id === tabId)) ?? null
}

export function tabById(workbench: Workbench, tabId: string): Tab | null {
  return paneOfTab(workbench, tabId)?.tabs.find((tab) => tab.id === tabId) ?? null
}

export function focusedPane(workbench: Workbench): Pane {
  return paneById(workbench, workbench.focusedPaneId) ?? panesOf(workbench.root)[0]
}

export function activeTab(pane: Pane): Tab {
  return pane.tabs.find((tab) => tab.id === pane.activeId) ?? pane.tabs[0]
}

export function focusedTab(workbench: Workbench): Tab {
  return activeTab(focusedPane(workbench))
}

export function tabCount(workbench: Workbench): number {
  return panesOf(workbench.root).reduce((count, pane) => count + pane.tabs.length, 0)
}

export function focusPane(workbench: Workbench, paneId: string): Workbench {
  const pane = paneById(workbench, paneId)
  if (pane === null || workbench.focusedPaneId === paneId) {
    return workbench
  }
  return { ...workbench, focusedPaneId: paneId, used: usedFirst(workbench.used, pane.activeId) }
}

/** Shows a tab in its pane and makes that pane the focused one. */
export function activateTab(workbench: Workbench, tabId: string): Workbench {
  const pane = paneOfTab(workbench, tabId)
  if (pane === null) {
    return workbench
  }
  const root = pane.activeId === tabId ? workbench.root : replacePane(workbench.root, { ...pane, activeId: tabId })
  return { ...workbench, root, focusedPaneId: pane.id, used: usedFirst(workbench.used, tabId) }
}

/** Shows a tab without counting it as used, for stepping through the recent tabs before settling on one. */
export function peekTab(workbench: Workbench, tabId: string): Workbench {
  const pane = paneOfTab(workbench, tabId)
  if (pane === null) {
    return workbench
  }
  return { ...workbench, root: replacePane(workbench.root, { ...pane, activeId: tabId }), focusedPaneId: pane.id }
}

/**
 * Opens a view's entry in the focused pane: the tab already showing it, or else the pane's preview tab reused for it
 * when {@code preview}, or else a new tab beside the active one.
 */
export function openTab(workbench: Workbench, spec: TabSpec, preview: boolean): Workbench {
  const pane = focusedPane(workbench)
  const showing = pane.tabs.find((tab) => tab.viewId === spec.viewId && tab.entry === spec.entry && spec.entry !== null)
  if (showing !== undefined) {
    return activateTab(workbench, showing.id)
  }
  const previewTab = pane.tabs.find((tab) => tab.preview)
  if (preview && previewTab !== undefined) {
    // Another view in the preview tab starts afresh, as a tab of its own would; another entry keeps the view as it is.
    const sameView = previewTab.viewId === spec.viewId
    const serial = sameView ? workbench.serial : workbench.serial + 1
    const replaced: Tab = { ...previewTab, ...spec, id: sameView ? previewTab.id : tabIdOf(serial), title: null }
    const tabs = pane.tabs.map((tab) => (tab.id === previewTab.id ? replaced : tab))
    const used = workbench.used.filter((id) => id !== previewTab.id || sameView)
    return activateTab({ ...workbench, serial, used, root: replacePane(workbench.root, { ...pane, tabs }) }, replaced.id)
  }
  const serial = workbench.serial + 1
  const tab: Tab = { ...spec, id: tabIdOf(serial), preview, title: null }
  const at = pane.tabs.findIndex((candidate) => candidate.id === pane.activeId) + 1
  const tabs = [...pane.tabs.slice(0, at), tab, ...pane.tabs.slice(at)]
  const opened = activateTab({ ...workbench, serial, root: replacePane(workbench.root, { ...pane, tabs }) }, tab.id)
  return withinTabLimit(opened, pane.id)
}

/** Shows a view in the focused pane: its most recently used tab there, or else a new one. */
export function showView(workbench: Workbench, viewId: string, preview: boolean): Workbench {
  const pane = focusedPane(workbench)
  const known = workbench.used.find((id) => pane.tabs.some((tab) => tab.id === id && tab.viewId === viewId))
  const showing = known ?? pane.tabs.find((tab) => tab.viewId === viewId)?.id
  if (showing !== undefined) {
    return activateTab(workbench, showing)
  }
  return openTab(workbench, { viewId, entry: null }, preview)
}

/** Keeps a preview tab, so the next click in a list opens another tab instead of reusing it. */
export function keepTab(workbench: Workbench, tabId: string): Workbench {
  return updateTab(workbench, tabId, (tab) => (tab.preview ? { ...tab, preview: false } : tab))
}

/** Records the entry a tab's view settled on, for a tab opened before it had one. */
export function settleEntry(workbench: Workbench, tabId: string, entry: string): Workbench {
  return updateTab(workbench, tabId, (tab) => (tab.entry === null ? { ...tab, entry } : tab))
}

export function titleTab(workbench: Workbench, tabId: string, title: string | null): Workbench {
  return updateTab(workbench, tabId, (tab) => (tab.title === title ? tab : { ...tab, title }))
}

/** Closes a tab; a pane left without tabs goes, and the last tab of all stays open. */
export function closeTab(workbench: Workbench, tabId: string): Workbench {
  const pane = paneOfTab(workbench, tabId)
  if (pane === null || tabCount(workbench) === 1) {
    return workbench
  }
  const used = workbench.used.filter((id) => id !== tabId)
  if (pane.tabs.length === 1) {
    const root = removePane(workbench.root, pane.id)
    const focused = workbench.focusedPaneId === pane.id ? (paneOfTab({ ...workbench, root }, used[0] ?? '') ?? panesOf(root)[0]) : focusedPane({ ...workbench, root })
    return { ...workbench, root, used, focusedPaneId: focused.id }
  }
  const tabs = pane.tabs.filter((tab) => tab.id !== tabId)
  const activeId = pane.activeId === tabId ? (used.find((id) => tabs.some((tab) => tab.id === id)) ?? tabs[0].id) : pane.activeId
  return { ...workbench, root: replacePane(workbench.root, { ...pane, tabs, activeId }), used }
}

export function closeOtherTabs(workbench: Workbench, tabId: string): Workbench {
  const pane = paneOfTab(workbench, tabId)
  if (pane === null) {
    return workbench
  }
  const closing = new Set(pane.tabs.filter((tab) => tab.id !== tabId).map((tab) => tab.id))
  const root = replacePane(workbench.root, { ...pane, tabs: pane.tabs.filter((tab) => tab.id === tabId), activeId: tabId })
  return activateTab({ ...workbench, root, used: workbench.used.filter((id) => !closing.has(id)) }, tabId)
}

/** The tab {@code step} places along from the focused pane's active one, wrapping around. */
export function stepTab(workbench: Workbench, step: number): Workbench {
  const pane = focusedPane(workbench)
  const at = pane.tabs.findIndex((tab) => tab.id === pane.activeId)
  const next = pane.tabs[(((at + step) % pane.tabs.length) + pane.tabs.length) % pane.tabs.length]
  return activateTab(workbench, next.id)
}

/** Moves a tab into a pane at {@code index}, or to its end; a pane left without tabs goes. */
export function moveTab(workbench: Workbench, tabId: string, paneId: string, index?: number): Workbench {
  const from = paneOfTab(workbench, tabId)
  const to = paneById(workbench, paneId)
  if (from === null || to === null) {
    return workbench
  }
  const tab = from.tabs.find((candidate) => candidate.id === tabId)!
  if (from.id === to.id) {
    const others = from.tabs.filter((candidate) => candidate.id !== tabId)
    const at = Math.max(0, Math.min(index ?? others.length, others.length))
    const tabs = [...others.slice(0, at), tab, ...others.slice(at)]
    return activateTab({ ...workbench, root: replacePane(workbench.root, { ...from, tabs }) }, tabId)
  }
  const detached = detachTab(workbench, from, tabId)
  const target = paneById(detached, paneId)!
  const at = Math.min(index ?? target.tabs.length, target.tabs.length)
  const duplicate = target.tabs.find((candidate) => candidate.viewId === tab.viewId && candidate.entry === tab.entry && tab.entry !== null)
  const tabs = duplicate === undefined ? [...target.tabs.slice(0, at), tab, ...target.tabs.slice(at)] : target.tabs
  const moved = { ...detached, root: replacePane(detached.root, { ...target, tabs }), used: duplicate === undefined ? detached.used : detached.used.filter((id) => id !== tabId) }
  return withinTabLimit(activateTab(moved, duplicate?.id ?? tabId), paneId)
}

/**
 * Opens a tab in a new pane at an edge of a pane: the tab itself, moved there, or a copy of it. Moving a pane's only
 * tab to its own edge would leave it where it is, so that does nothing.
 */
export function splitTab(workbench: Workbench, tabId: string, paneId: string, edge: Edge, copy: boolean): Workbench {
  const from = paneOfTab(workbench, tabId)
  const target = paneById(workbench, paneId)
  if (from === null || target === null || (!copy && from.id === target.id && from.tabs.length === 1)) {
    return workbench
  }
  const tab = from.tabs.find((candidate) => candidate.id === tabId)!
  const serial = workbench.serial + 2
  const placed: Tab = copy ? { ...tab, id: tabIdOf(serial - 1), preview: false } : tab
  const pane: Pane = { kind: 'pane', id: paneIdOf(serial), tabs: [placed], activeId: placed.id }
  const source = copy ? workbench : detachTab(workbench, from, tabId)
  const root = insertBeside(source.root, paneId, pane, edge, splitIdOf(serial))
  return activateTab({ ...source, root, serial: serial + 1 }, placed.id)
}

/** Sets the shares of a split's children, keeping each from shrinking away. */
export function resizeSplit(workbench: Workbench, splitId: string, sizes: number[]): Workbench {
  const clamped = sharesOf(sizes.map((size) => Math.max(SMALLEST_SHARE, size)))
  return { ...workbench, root: mapNodes(workbench.root, (node) => (node.kind === 'split' && node.id === splitId ? { ...node, sizes: clamped } : node)) }
}

function detachTab(workbench: Workbench, pane: Pane, tabId: string): Workbench {
  if (pane.tabs.length === 1) {
    return { ...workbench, root: removePane(workbench.root, pane.id) }
  }
  const tabs = pane.tabs.filter((tab) => tab.id !== tabId)
  const activeId = pane.activeId === tabId ? (workbench.used.find((id) => id !== tabId && tabs.some((tab) => tab.id === id)) ?? tabs[0].id) : pane.activeId
  return { ...workbench, root: replacePane(workbench.root, { ...pane, tabs, activeId }) }
}

function withinTabLimit(workbench: Workbench, paneId: string): Workbench {
  const pane = paneById(workbench, paneId)
  if (pane === null || pane.tabs.length <= TAB_LIMIT) {
    return workbench
  }
  const rank = (tab: Tab): number => {
    const at = workbench.used.indexOf(tab.id)
    return at < 0 ? Number.MAX_SAFE_INTEGER : at
  }
  const oldest = pane.tabs.filter((tab) => tab.id !== pane.activeId).reduce((worst, tab) => (rank(tab) > rank(worst) ? tab : worst))
  return withinTabLimit(closeTab(workbench, oldest.id), paneId)
}

function updateTab(workbench: Workbench, tabId: string, change: (tab: Tab) => Tab): Workbench {
  const pane = paneOfTab(workbench, tabId)
  if (pane === null) {
    return workbench
  }
  const tabs = pane.tabs.map((tab) => (tab.id === tabId ? change(tab) : tab))
  return tabs.every((tab, index) => tab === pane.tabs[index]) ? workbench : { ...workbench, root: replacePane(workbench.root, { ...pane, tabs }) }
}

function replacePane(node: LayoutNode, pane: Pane): LayoutNode {
  return mapNodes(node, (candidate) => (candidate.kind === 'pane' && candidate.id === pane.id ? pane : candidate))
}

function mapNodes(node: LayoutNode, change: (node: LayoutNode) => LayoutNode): LayoutNode {
  if (node.kind === 'pane') {
    return change(node)
  }
  const children = node.children.map((child) => mapNodes(child, change))
  return change(children.every((child, index) => child === node.children[index]) ? node : { ...node, children })
}

function removePane(node: LayoutNode, paneId: string): LayoutNode {
  if (node.kind === 'pane') {
    return node
  }
  const at = node.children.findIndex((child) => child.kind === 'pane' && child.id === paneId)
  if (at < 0) {
    const children = node.children.map((child) => removePane(child, paneId))
    return children.every((child, index) => child === node.children[index]) ? node : flattened({ ...node, children })
  }
  const children = node.children.filter((_, index) => index !== at)
  const sizes = node.sizes.filter((_, index) => index !== at)
  const heir = at > 0 ? at - 1 : 0
  sizes[heir] += node.sizes[at]
  return children.length === 1 ? children[0] : flattened({ ...node, children, sizes })
}

function insertBeside(node: LayoutNode, paneId: string, pane: Pane, edge: Edge, splitId: string): LayoutNode {
  const direction: SplitDirection = edge === 'left' || edge === 'right' ? 'row' : 'column'
  const before = edge === 'left' || edge === 'top'
  if (node.kind === 'pane') {
    if (node.id !== paneId) {
      return node
    }
    return { kind: 'split', id: splitId, direction, children: before ? [pane, node] : [node, pane], sizes: [0.5, 0.5] }
  }
  const at = node.children.findIndex((child) => child.kind === 'pane' && child.id === paneId)
  if (at >= 0 && node.direction === direction) {
    const half = node.sizes[at] / 2
    const index = before ? at : at + 1
    const children = [...node.children.slice(0, index), pane, ...node.children.slice(index)]
    const sizes = node.sizes.map((size, position) => (position === at ? half : size))
    sizes.splice(index, 0, half)
    return { ...node, children, sizes }
  }
  const children = node.children.map((child) => insertBeside(child, paneId, pane, edge, splitId))
  return children.every((child, index) => child === node.children[index]) ? node : { ...node, children }
}

// A split inside a split of the same direction joins its parent, each child keeping its share of the room.
function flattened(split: Split): Split {
  const children: LayoutNode[] = []
  const sizes: number[] = []
  split.children.forEach((child, index) => {
    if (child.kind === 'split' && child.direction === split.direction) {
      child.children.forEach((grandchild, position) => {
        children.push(grandchild)
        sizes.push(child.sizes[position] * split.sizes[index])
      })
    } else {
      children.push(child)
      sizes.push(split.sizes[index])
    }
  })
  return { ...split, children, sizes: sharesOf(sizes) }
}

function sharesOf(sizes: number[]): number[] {
  const total = sizes.reduce((sum, size) => sum + size, 0)
  return sizes.map((size) => size / total)
}

function usedFirst(used: string[], tabId: string): string[] {
  return used[0] === tabId ? used : [tabId, ...used.filter((id) => id !== tabId)]
}

function tabIdOf(serial: number): string {
  return `tab-${serial}`
}

function paneIdOf(serial: number): string {
  return `pane-${serial}`
}

function splitIdOf(serial: number): string {
  return `split-${serial}`
}

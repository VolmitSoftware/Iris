import { describe, expect, it } from 'vitest'
import {
  TAB_LIMIT,
  activateTab,
  closeOtherTabs,
  closeTab,
  focusedPane,
  focusedTab,
  keepTab,
  moveTab,
  openTab,
  panesOf,
  peekTab,
  resizeSplit,
  settleEntry,
  showView,
  splitTab,
  stepTab,
  tabCount,
  workbenchWith,
  type Split,
  type Workbench
} from './workbench'

const start = (): Workbench => workbenchWith({ viewId: 'biomes', entry: null })
const titles = (workbench: Workbench): string[][] => panesOf(workbench.root).map((pane) => pane.tabs.map((tab) => `${tab.viewId}:${tab.entry ?? ''}`))

describe('opening tabs', () => {
  it('opens a view in a new tab beside the active one', () => {
    const opened = showView(start(), 'rivers', false)
    expect(titles(opened)).toEqual([['biomes:', 'rivers:']])
    expect(focusedTab(opened).viewId).toBe('rivers')
  })

  it('shows the tab a view already has instead of opening another', () => {
    const back = showView(showView(start(), 'rivers', false), 'biomes', false)
    expect(tabCount(back)).toBe(2)
    expect(focusedTab(back).viewId).toBe('biomes')
  })

  it('reuses the preview tab for the next entry clicked', () => {
    const first = openTab(start(), { viewId: 'noise', entry: 'a' }, true)
    const second = openTab(first, { viewId: 'noise', entry: 'b' }, true)
    expect(titles(second)).toEqual([['biomes:', 'noise:b']])
    expect(focusedTab(second).id).toBe(focusedTab(first).id)
  })

  it('gives the preview tab a new id when another view takes it over', () => {
    const noise = openTab(start(), { viewId: 'noise', entry: 'a' }, true)
    const maps = openTab(noise, { viewId: 'imageMaps', entry: null }, true)
    expect(titles(maps)).toEqual([['biomes:', 'imageMaps:']])
    expect(focusedTab(maps).id).not.toBe(focusedTab(noise).id)
    expect(maps.used).not.toContain(focusedTab(noise).id)
  })

  it('opens a new preview tab once the old one is kept', () => {
    const first = openTab(start(), { viewId: 'noise', entry: 'a' }, true)
    const kept = keepTab(first, focusedTab(first).id)
    const second = openTab(kept, { viewId: 'noise', entry: 'b' }, true)
    expect(titles(second)).toEqual([['biomes:', 'noise:a', 'noise:b']])
    expect(focusedTab(second).preview).toBe(true)
  })

  it('shows the tab an entry is already open in', () => {
    const first = keepTab(openTab(start(), { viewId: 'noise', entry: 'a' }, true), 'tab-3')
    const again = openTab(openTab(first, { viewId: 'noise', entry: 'b' }, true), { viewId: 'noise', entry: 'a' }, true)
    expect(focusedTab(again).entry).toBe('a')
    expect(tabCount(again)).toBe(3)
  })

  it('records the entry a view settled on only for a tab without one', () => {
    const opened = showView(start(), 'noise', true)
    const settled = settleEntry(opened, focusedTab(opened).id, 'first')
    expect(focusedTab(settled).entry).toBe('first')
    expect(focusedTab(settleEntry(settled, focusedTab(settled).id, 'other')).entry).toBe('first')
  })

  it('closes the tab used longest ago past the limit', () => {
    let workbench = start()
    for (let index = 0; index < TAB_LIMIT; index++) {
      workbench = openTab(workbench, { viewId: 'noise', entry: String(index) }, false)
    }
    expect(tabCount(workbench)).toBe(TAB_LIMIT)
    expect(focusedPane(workbench).tabs.some((tab) => tab.viewId === 'biomes')).toBe(false)
  })
})

describe('closing tabs', () => {
  it('keeps the last tab of all open', () => {
    expect(closeTab(start(), 'tab-1')).toEqual(start())
  })

  it('shows the tab used before the closed one', () => {
    const three = showView(showView(start(), 'rivers', false), 'height', false)
    const back = closeTab(activateTab(three, 'tab-1'), 'tab-1')
    expect(focusedTab(back).viewId).toBe('height')
  })

  it('removes a pane once its last tab closes', () => {
    const split = splitTab(showView(start(), 'rivers', false), 'tab-3', 'pane-2', 'right', false)
    expect(panesOf(split.root)).toHaveLength(2)
    const closed = closeTab(split, 'tab-3')
    expect(panesOf(closed.root)).toHaveLength(1)
    expect(closed.root.kind).toBe('pane')
    expect(focusedPane(closed).id).toBe('pane-2')
  })

  it('closes every other tab of the pane', () => {
    const three = showView(showView(start(), 'rivers', false), 'height', false)
    expect(titles(closeOtherTabs(three, 'tab-1'))).toEqual([['biomes:']])
  })
})

describe('stepping through tabs', () => {
  it('wraps around the pane', () => {
    const two = showView(start(), 'rivers', false)
    expect(focusedTab(stepTab(two, 1)).viewId).toBe('biomes')
    expect(focusedTab(stepTab(two, -1)).viewId).toBe('biomes')
  })

  it('peeks without counting the tab as used', () => {
    const two = showView(start(), 'rivers', false)
    const peeked = peekTab(two, 'tab-1')
    expect(focusedTab(peeked).viewId).toBe('biomes')
    expect(peeked.used[0]).toBe(two.used[0])
  })
})

describe('splitting', () => {
  it('moves a tab into a new pane on the right', () => {
    const split = splitTab(showView(start(), 'rivers', false), 'tab-3', 'pane-2', 'right', false)
    expect(titles(split)).toEqual([['biomes:'], ['rivers:']])
    expect((split.root as Split).direction).toBe('row')
    expect(focusedTab(split).viewId).toBe('rivers')
  })

  it('copies a tab so the same entry shows in both panes', () => {
    const split = splitTab(start(), 'tab-1', 'pane-2', 'bottom', true)
    expect(titles(split)).toEqual([['biomes:'], ['biomes:']])
    expect((split.root as Split).direction).toBe('column')
  })

  it('does nothing for a pane moving its only tab to its own edge', () => {
    expect(splitTab(start(), 'tab-1', 'pane-2', 'left', false)).toEqual(start())
  })

  it('puts a third pane in the same row with half of its neighbour', () => {
    const two = splitTab(start(), 'tab-1', 'pane-2', 'right', true)
    const three = splitTab(two, focusedTab(two).id, focusedPane(two).id, 'right', true)
    const root = three.root as Split
    expect(root.children).toHaveLength(3)
    expect(root.sizes).toEqual([0.5, 0.25, 0.25])
  })

  it('nests a column inside a row', () => {
    const two = splitTab(start(), 'tab-1', 'pane-2', 'right', true)
    const grid = splitTab(two, focusedTab(two).id, focusedPane(two).id, 'bottom', true)
    const root = grid.root as Split
    expect(root.direction).toBe('row')
    expect(root.children[1].kind).toBe('split')
    expect(panesOf(grid.root)).toHaveLength(3)
  })

  it('joins a row again once the column in it is down to one pane', () => {
    const two = splitTab(start(), 'tab-1', 'pane-2', 'right', true)
    const grid = splitTab(two, focusedTab(two).id, focusedPane(two).id, 'bottom', true)
    const closed = closeTab(grid, focusedTab(grid).id)
    const root = closed.root as Split
    expect(root.children.every((child) => child.kind === 'pane')).toBe(true)
    expect(root.sizes.reduce((sum, size) => sum + size, 0)).toBeCloseTo(1)
  })

  it('moves a tab to another pane, dropping the pane it left when empty', () => {
    const split = splitTab(showView(start(), 'rivers', false), 'tab-3', 'pane-2', 'right', false)
    const back = moveTab(split, 'tab-3', 'pane-2')
    expect(titles(back)).toEqual([['biomes:', 'rivers:']])
  })

  it('reorders a tab within its pane', () => {
    const three = showView(showView(start(), 'rivers', false), 'height', false)
    expect(titles(moveTab(three, 'tab-1', 'pane-2', 2))).toEqual([['rivers:', 'height:', 'biomes:']])
    expect(titles(moveTab(three, 'tab-4', 'pane-2', 0))).toEqual([['height:', 'biomes:', 'rivers:']])
  })

  it('keeps each share of a resized split above the smallest', () => {
    const two = splitTab(start(), 'tab-1', 'pane-2', 'right', true)
    const resized = resizeSplit(two, (two.root as Split).id, [0.99, 0.01])
    expect((resized.root as Split).sizes[1]).toBeGreaterThan(0.05)
  })
})


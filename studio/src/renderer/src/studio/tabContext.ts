import { createContext, useContext, useEffect } from 'react'

/** The tab a view draws in: what it shows, and how the view opens other entries from its list. */
export interface TabHandle {
  id: string
  viewId: string
  /** The entry the tab shows, or null while its view has not settled on one. */
  entry: string | null
  /** Shown in its pane, as opposed to hidden behind another tab. */
  visible: boolean
  /** Shown in the pane last clicked, the one the stripe, the keys and the side panels follow. */
  focused: boolean
  /** Opens an entry from the view's list: in the pane's preview tab, or in a tab of its own when {@code keep}. */
  choose(entry: string, keep?: boolean): void
  /** Keeps this tab once it is used, so the next click in the list opens another. */
  keep(): void
  /** Records the entry the view showed for a tab opened without one, and what the tab is called. */
  settle(entry: string | null, title: string | null): void
}

const DETACHED: TabHandle = {
  id: '',
  viewId: '',
  entry: null,
  visible: true,
  focused: true,
  choose: () => undefined,
  keep: () => undefined,
  settle: () => undefined
}

export const TabContext = createContext<TabHandle>(DETACHED)

/** Lets a tab's view take the keys meant for the focused view, such as the map's; returns how to stop. */
export type CommandRegistry = (tabId: string, run: (action: string) => void) => () => void

export const TabCommands = createContext<CommandRegistry>(() => () => undefined)

/** Takes the keys meant for the focused view while this tab is the focused pane's shown one. */
export function useTabCommands(tab: TabHandle, run: (action: string) => void): void {
  const register = useContext(TabCommands)
  useEffect(() => register(tab.id, run), [register, tab.id, run])
}

export function useTab(): TabHandle {
  return useContext(TabContext)
}

/** Names the tab after the entry its view shows, and records that entry for a tab opened without one. */
export function useSettledEntry(tab: TabHandle, entry: string | null, title: string | null): void {
  const { settle } = tab
  useEffect(() => settle(entry, title), [settle, entry, title])
}

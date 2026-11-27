import { contextBridge, ipcRenderer, type IpcRendererEvent } from 'electron'
import type { StoredShortcuts, StudioHost, ThemeChoice, UiScale } from '../shared/bridge'

const host: StudioHost = {
  listBridges: () => ipcRenderer.invoke('studio:list-bridges'),
  initialLink: () => ipcRenderer.invoke('studio:initial-link'),
  onLink: (listener) => {
    const forward = (_event: IpcRendererEvent, link: string): void => listener(link)
    ipcRenderer.on('studio:link', forward)
    return () => ipcRenderer.removeListener('studio:link', forward)
  },
  setTheme: (theme: ThemeChoice) => ipcRenderer.send('studio:set-theme', theme),
  copyText: (text: string) => ipcRenderer.send('studio:copy-text', text),
  loadShortcuts: () => ipcRenderer.invoke('studio:load-shortcuts'),
  saveShortcuts: (shortcuts: StoredShortcuts) => ipcRenderer.send('studio:save-shortcuts', shortcuts),
  graphics: () => ipcRenderer.invoke('studio:graphics'),
  useDedicatedGpu: (dedicated: boolean) => ipcRenderer.send('studio:use-dedicated-gpu', dedicated),
  uiScale: () => ipcRenderer.invoke('studio:ui-scale'),
  setUiScale: (scale: UiScale) => ipcRenderer.send('studio:set-ui-scale', scale),
  about: () => ipcRenderer.invoke('studio:about')
}

contextBridge.exposeInMainWorld('studio', host)

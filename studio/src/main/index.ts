import { app, BrowserWindow, clipboard, ipcMain, nativeTheme, shell } from 'electron'
import { mkdir, readdir, readFile, writeFile } from 'node:fs/promises'
import { homedir } from 'node:os'
import { dirname, join } from 'node:path'
import type { BridgeConnection, StoredShortcuts, ThemeChoice } from '../shared/bridge'

const CONNECTION_FOLDER = join(homedir(), '.iris', 'studio', 'bridges')
const SETTINGS_FILE = 'studio.json'
const SHORTCUTS_KEY = 'shortcuts'
const SETTINGS_INDENT = 2
const LINK_PREFIX = 'iris-studio://'
const DARK_BACKGROUND = '#1e1f22'
const WINDOW_SIZE = { width: 1440, height: 880, minWidth: 820, minHeight: 520 }

let window: BrowserWindow | null = null
let pendingLink: string | null = findLink(process.argv)

function findLink(argv: string[]): string | null {
  return argv.find((argument) => argument.startsWith(LINK_PREFIX)) ?? null
}

function isRunning(pid: unknown): boolean {
  if (typeof pid !== 'number') {
    return true
  }
  try {
    process.kill(pid, 0)
    return true
  } catch (error) {
    return (error as NodeJS.ErrnoException).code === 'EPERM'
  }
}

/** The bridges announced on this machine whose server still runs, newest first. */
async function listBridges(): Promise<BridgeConnection[]> {
  let files: string[]
  try {
    files = (await readdir(CONNECTION_FOLDER)).filter((name) => name.endsWith('.json'))
  } catch {
    return []
  }
  const bridges = await Promise.all(files.map(readConnection))
  return bridges
    .filter((bridge): bridge is BridgeConnection => bridge !== null)
    .sort((first, second) => (second.startedMillis ?? 0) - (first.startedMillis ?? 0))
}

async function readConnection(name: string): Promise<BridgeConnection | null> {
  try {
    const file = JSON.parse(await readFile(join(CONNECTION_FOLDER, name), 'utf8'))
    if (!isRunning(file.pid) || typeof file.port !== 'number' || typeof file.token !== 'string') {
      return null
    }
    return { world: String(file.world ?? ''), port: file.port, token: file.token, startedMillis: file.startedMillis }
  } catch {
    return null
  }
}

function settingsPath(): string {
  return join(app.getPath('userData'), SETTINGS_FILE)
}

async function readSettings(): Promise<Record<string, unknown>> {
  try {
    const settings: unknown = JSON.parse(await readFile(settingsPath(), 'utf8'))
    return typeof settings === 'object' && settings !== null && !Array.isArray(settings) ? (settings as Record<string, unknown>) : {}
  } catch {
    return {}
  }
}

async function saveShortcuts(shortcuts: StoredShortcuts): Promise<void> {
  try {
    const settings = { ...(await readSettings()), [SHORTCUTS_KEY]: shortcuts }
    await mkdir(dirname(settingsPath()), { recursive: true })
    await writeFile(settingsPath(), JSON.stringify(settings, null, SETTINGS_INDENT))
  } catch (failure) {
    console.error(`Could not save the studio shortcuts to ${settingsPath()}`, failure)
  }
}

function createWindow(): void {
  window = new BrowserWindow({
    ...WINDOW_SIZE,
    title: 'Iris Studio',
    backgroundColor: DARK_BACKGROUND,
    autoHideMenuBar: true,
    show: false,
    webPreferences: {
      preload: join(import.meta.dirname, '../preload/index.cjs'),
      sandbox: true,
      contextIsolation: true
    }
  })
  window.once('ready-to-show', () => window?.show())
  window.webContents.setWindowOpenHandler(({ url }) => {
    void shell.openExternal(url)
    return { action: 'deny' }
  })
  if (process.env.ELECTRON_RENDERER_URL) {
    void window.loadURL(process.env.ELECTRON_RENDERER_URL)
  } else {
    void window.loadFile(join(import.meta.dirname, '../renderer/index.html'))
  }
}

function deliverLink(link: string): void {
  if (window === null) {
    pendingLink = link
    return
  }
  window.webContents.send('studio:link', link)
  if (window.isMinimized()) {
    window.restore()
  }
  window.focus()
}

if (!app.requestSingleInstanceLock()) {
  app.quit()
} else {
  app.setAsDefaultProtocolClient('iris-studio')
  app.on('second-instance', (_event, argv) => {
    const link = findLink(argv)
    if (link !== null) {
      deliverLink(link)
    } else {
      window?.focus()
    }
  })
  app.on('open-url', (event, url) => {
    event.preventDefault()
    deliverLink(url)
  })

  ipcMain.handle('studio:list-bridges', listBridges)
  ipcMain.handle('studio:initial-link', () => {
    const link = pendingLink
    pendingLink = null
    return link
  })
  ipcMain.on('studio:set-theme', (_event, theme: ThemeChoice) => {
    nativeTheme.themeSource = theme
  })
  ipcMain.handle('studio:load-shortcuts', async () => (await readSettings())[SHORTCUTS_KEY] ?? {})
  ipcMain.on('studio:save-shortcuts', (_event, shortcuts: StoredShortcuts) => void saveShortcuts(shortcuts))
  ipcMain.on('studio:copy-text', (_event, text: string) => clipboard.writeText(text))

  void app.whenReady().then(createWindow)
  app.on('window-all-closed', () => app.quit())
}

import { useEffect, useState, type ReactNode } from 'react'
import { Info, Keyboard, MonitorCog, Palette } from 'lucide-react'
import { Dialog, DialogContent, DialogTitle } from '@/components/ui/dialog'
import { ToggleGroup, ToggleGroupItem } from '@/components/ui/toggle-group'
import { cn } from '@/lib/utils'
import { UI_SCALES, type AboutInfo, type GraphicsInfo, type ThemeChoice, type UiScale } from '../../../shared/bridge'
import type { ViewInfo } from '../bridge/types'
import intellijNotice from '../icons/intellij/NOTICE.txt?raw'
import { KeymapPage } from './KeymapSettings'
import { SettingGroup, SettingRow, SettingsPage } from './SettingsLayout'
import type { Theme } from './theme'

type PageId = 'appearance' | 'keymap' | 'graphics' | 'about'

interface PageLink {
  id: PageId
  label: string
  icon: ReactNode
}

const STUDIO_PAGES: PageLink[] = [
  { id: 'appearance', label: 'Appearance', icon: <Palette /> },
  { id: 'keymap', label: 'Keymap', icon: <Keyboard /> },
  { id: 'graphics', label: 'Graphics', icon: <MonitorCog /> }
]
const ABOUT_PAGE: PageLink = { id: 'about', label: 'About', icon: <Info /> }

const THEME_LABELS: Record<ThemeChoice, string> = { system: 'System', light: 'Light', dark: 'Dark' }
const PERCENT = 100
const APACHE_LICENSE_URL = 'https://www.apache.org/licenses/LICENSE-2.0'
const LUCIDE_LICENSE_URL = 'https://lucide.dev/license'

interface SettingsDialogProps {
  open: boolean
  onOpenChange(open: boolean): void
  views: ViewInfo[]
  theme: Theme
}

/** The studio's own settings, a page each, saved as they change. */
export function SettingsDialog({ open, onOpenChange, views, theme }: SettingsDialogProps): React.JSX.Element {
  const [page, setPage] = useState<PageId>('appearance')
  return (
    <Dialog open={open} onOpenChange={onOpenChange}>
      <DialogContent
        aria-describedby={undefined}
        onOpenAutoFocus={(event) => {
          event.preventDefault()
          ;(event.currentTarget as HTMLElement).focus()
        }}
        className="flex h-[min(600px,85vh)] gap-0 overflow-hidden p-0 sm:max-w-[860px]">
        <nav className="flex w-52 shrink-0 flex-col gap-0.5 border-r bg-background p-2">
          <DialogTitle className="px-2 pt-1.5 pb-2.5 text-[13px] font-semibold">Settings</DialogTitle>
          {STUDIO_PAGES.map((link) => (
            <PageButton key={link.id} link={link} chosen={page === link.id} onChoose={setPage} />
          ))}
          <div className="flex-1" />
          <PageButton link={ABOUT_PAGE} chosen={page === ABOUT_PAGE.id} onChoose={setPage} />
        </nav>
        {page === 'appearance' && <AppearancePage theme={theme} />}
        {page === 'keymap' && <KeymapPage views={views} />}
        {page === 'graphics' && <GraphicsPage />}
        {page === 'about' && <AboutPage />}
      </DialogContent>
    </Dialog>
  )
}

function PageButton({ link, chosen, onChoose }: { link: PageLink; chosen: boolean; onChoose(page: PageId): void }): React.JSX.Element {
  return (
    <button
      className={cn(
        'flex h-7 w-full items-center gap-2 rounded-md px-2 text-left outline-none focus-visible:focused [&_svg]:size-4',
        chosen ? 'bg-selection' : 'hover:bg-accent'
      )}
      aria-current={chosen ? 'page' : undefined}
      onClick={() => onChoose(link.id)}
    >
      {link.icon}
      {link.label}
    </button>
  )
}

function AppearancePage({ theme }: { theme: Theme }): React.JSX.Element {
  const [scale, chooseScale] = useUiScale()
  return (
    <SettingsPage title="Appearance" description="How the studio looks.">
      <SettingGroup>
        <SettingRow label="Theme" detail="System follows the desktop's light or dark setting">
          <Choice value={theme.choice} options={Object.keys(THEME_LABELS) as ThemeChoice[]} label={(choice) => THEME_LABELS[choice]} onChange={theme.choose} />
        </SettingRow>
        <SettingRow label="UI scale" detail="The size of text and controls">
          {scale !== null && <Choice value={scale} options={[...UI_SCALES]} label={(choice) => `${Math.round(choice * PERCENT)}%`} onChange={chooseScale} />}
        </SettingRow>
      </SettingGroup>
    </SettingsPage>
  )
}

function useUiScale(): [UiScale | null, (scale: UiScale) => void] {
  const [scale, setScale] = useState<UiScale | null>(null)
  useEffect(() => {
    void window.studio.uiScale().then(setScale)
  }, [])
  const choose = (next: UiScale): void => {
    setScale(next)
    window.studio.setUiScale(next)
  }
  return [scale, choose]
}

interface ChoiceProps<T> {
  value: T
  options: T[]
  label(option: T): string
  onChange(option: T): void
}

/** One of a few options, as segments with the chosen one filled. */
function Choice<T extends string | number>({ value, options, label, onChange }: ChoiceProps<T>): React.JSX.Element {
  return (
    <ToggleGroup
      type="single"
      variant="outline"
      className="w-64"
      value={String(value)}
      onValueChange={(chosen) => {
        const option = options.find((candidate) => String(candidate) === chosen)
        if (option !== undefined) {
          onChange(option)
        }
      }}
    >
      {options.map((option) => (
        <ToggleGroupItem key={String(option)} value={String(option)}>
          {label(option)}
        </ToggleGroupItem>
      ))}
    </ToggleGroup>
  )
}

function GraphicsPage(): React.JSX.Element {
  const [graphics, setGraphics] = useState<GraphicsInfo | null>(null)
  useEffect(() => {
    void window.studio.graphics().then(setGraphics)
  }, [])
  return (
    <SettingsPage title="Graphics" description="The GPU the studio draws its maps and 3D views on.">
      <SettingGroup>
        {graphics !== null && !graphics.canUseDedicatedGpu && (
          <SettingRow label="GPU" detail="Choosing one needs an NVIDIA GPU with its Vulkan driver">
            <span className="text-secondary-text">System default</span>
          </SettingRow>
        )}
        {graphics !== null && graphics.canUseDedicatedGpu && (
          <SettingRow label="GPU" detail="Changing it restarts the studio">
            <Choice
              value={graphics.usesDedicatedGpu ? 'dedicated' : 'default'}
              options={['default', 'dedicated']}
              label={(choice) => (choice === 'dedicated' ? 'dGPU' : 'System default')}
              onChange={(choice) => window.studio.useDedicatedGpu(choice === 'dedicated')}
            />
          </SettingRow>
        )}
      </SettingGroup>
    </SettingsPage>
  )
}

function AboutPage(): React.JSX.Element {
  const [about, setAbout] = useState<AboutInfo | null>(null)
  useEffect(() => {
    void window.studio.about().then(setAbout)
  }, [])
  return (
    <SettingsPage title="About">
      <SettingGroup title="Iris Studio">
        <SettingRow label="Version">
          <span className="text-secondary-text tabular-nums">{about?.version}</span>
        </SettingRow>
        <SettingRow label="Electron">
          <span className="text-secondary-text tabular-nums">{about?.electron}</span>
        </SettingRow>
        <SettingRow label="Chromium">
          <span className="text-secondary-text tabular-nums">{about?.chromium}</span>
        </SettingRow>
      </SettingGroup>
      <SettingGroup title="Licences">
        <SettingRow label="IntelliJ icons" detail={intellijNotice.split('\n').slice(0, 2).join(' · ')}>
          <LicenseLink href={APACHE_LICENSE_URL}>Apache License 2.0</LicenseLink>
        </SettingRow>
        <SettingRow label="Lucide icons">
          <LicenseLink href={LUCIDE_LICENSE_URL}>ISC License</LicenseLink>
        </SettingRow>
      </SettingGroup>
    </SettingsPage>
  )
}

function LicenseLink({ href, children }: { href: string; children: ReactNode }): React.JSX.Element {
  return (
    <a className="rounded-sm text-link outline-none hover:underline focus-visible:focused" href={href} target="_blank" rel="noreferrer">
      {children}
    </a>
  )
}

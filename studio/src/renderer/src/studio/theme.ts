import { useEffect, useState } from 'react'
import type { ThemeChoice } from '../../../shared/bridge'

const STORAGE_KEY = 'iris-studio.theme'
const DARK_QUERY = '(prefers-color-scheme: dark)'
const ORDER: ThemeChoice[] = ['system', 'light', 'dark']

function storedChoice(): ThemeChoice {
  const stored = localStorage.getItem(STORAGE_KEY)
  return stored === 'light' || stored === 'dark' ? stored : 'system'
}

function apply(choice: ThemeChoice): void {
  const dark = choice === 'dark' || (choice === 'system' && matchMedia(DARK_QUERY).matches)
  document.documentElement.classList.toggle('dark', dark)
}

/** The light or dark look, following the system unless chosen; the choice outlives the window. */
export function useTheme(): { choice: ThemeChoice; next: () => void } {
  const [choice, setChoice] = useState<ThemeChoice>(storedChoice)

  useEffect(() => {
    apply(choice)
    window.studio.setTheme(choice)
    localStorage.setItem(STORAGE_KEY, choice)
    const media = matchMedia(DARK_QUERY)
    const follow = (): void => apply(choice)
    media.addEventListener('change', follow)
    return () => media.removeEventListener('change', follow)
  }, [choice])

  return { choice, next: () => setChoice(ORDER[(ORDER.indexOf(choice) + 1) % ORDER.length]) }
}

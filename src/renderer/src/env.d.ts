import type { StudioHost } from '../../shared/bridge'

declare global {
  interface Window {
    studio: StudioHost
  }
}

export {}

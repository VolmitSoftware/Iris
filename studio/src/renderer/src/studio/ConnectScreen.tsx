import { useEffect, useRef, useState } from 'react'
import { IdeIcon } from '@/components/IdeIcon'
import { Button } from '@/components/ui/button'
import { Input } from '@/components/ui/input'
import type { BridgeConnection } from '../../../shared/bridge'

const BRIDGE_POLL_MILLIS = 2000

interface ConnectScreenProps {
  connecting: boolean
  error: string | null
  onConnect(connection: BridgeConnection): void
  onLink(link: string): void
}

/**
 * Lists the worlds servers on this machine opened to the studio, and takes a pasted link for any other. A single
 * world is opened right away, once.
 */
export function ConnectScreen({ connecting, error, onConnect, onLink }: ConnectScreenProps): React.JSX.Element {
  const [bridges, setBridges] = useState<BridgeConnection[]>([])
  const [link, setLink] = useState('')
  const openedOnItsOwn = useRef(false)

  useEffect(() => {
    let active = true
    const refresh = (): void => {
      void window.studio.listBridges().then((found) => {
        if (!active) {
          return
        }
        setBridges(found)
        if (found.length === 1 && !openedOnItsOwn.current) {
          openedOnItsOwn.current = true
          onConnect(found[0])
        }
      })
    }
    refresh()
    const timer = window.setInterval(refresh, BRIDGE_POLL_MILLIS)
    return () => {
      active = false
      window.clearInterval(timer)
    }
  }, [onConnect])

  return (
    <div className="flex h-full items-center justify-center bg-background">
      <div className="w-[440px] rounded-xl border border-overlay-border bg-card p-6 shadow-lg">
        <h1 className="text-[15px] font-semibold">Open a world</h1>
        <p className="mt-1 text-secondary-text">
          Run <code className="rounded bg-secondary px-1 font-mono text-[12px]">/iris studio map</code> in game or in the
          server console.
        </p>

        <div className="mt-4 flex flex-col gap-1">
          {bridges.length === 0 ? (
            <div className="flex items-center gap-2 rounded-md px-3 py-2 text-muted-foreground">
              <IdeIcon name="refresh" className="animate-spin" /> Waiting for a server on this machine
            </div>
          ) : (
            bridges.map((bridge) => (
              <button
                key={bridge.port}
                disabled={connecting}
                onClick={() => onConnect(bridge)}
                className="flex items-center gap-3 rounded-md px-3 py-2 text-left hover:bg-accent disabled:opacity-60"
              >
                <IdeIcon name="web" />
                <span className="flex-1 truncate font-medium">{bridge.world}</span>
                <span className="text-[11px] text-muted-foreground">port {bridge.port}</span>
              </button>
            ))
          )}
        </div>

        <form
          className="mt-4 flex gap-2"
          onSubmit={(event) => {
            event.preventDefault()
            onLink(link)
          }}
        >
          <Input value={link} onChange={(event) => setLink(event.target.value)} placeholder="iris-studio://…" className="h-8" />
          <Button type="submit" size="sm" disabled={connecting || link.trim() === ''}>
            {connecting ? <IdeIcon name="refresh" className="animate-spin" /> : 'Connect'}
          </Button>
        </form>
        {error !== null && <p className="mt-2 text-[12px] text-destructive">{error}</p>}
      </div>
    </div>
  )
}

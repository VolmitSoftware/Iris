import { useCallback, useEffect, useState } from 'react'
import { parseStudioLink, type BridgeConnection } from '../../shared/bridge'
import { BridgeClient } from './bridge/BridgeClient'
import type { WorldInfo } from './bridge/types'
import { ConnectScreen } from './studio/ConnectScreen'
import { MapStudio } from './studio/MapStudio'
import { useTheme } from './studio/theme'
import { shortcutStore } from './studio/useBindings'

interface Session {
  client: BridgeClient
  world: WorldInfo
}

/** The studio: a connect screen until a bridge answers, then the map of its world. */
export function App(): React.JSX.Element {
  const theme = useTheme()
  const [session, setSession] = useState<Session | null>(null)
  const [error, setError] = useState<string | null>(null)
  const [connecting, setConnecting] = useState(false)

  const connect = useCallback(async (connection: BridgeConnection) => {
    setConnecting(true)
    setError(null)
    const client = new BridgeClient(connection)
    try {
      setSession({ client, world: await client.world() })
    } catch (failure) {
      setError(failure instanceof Error ? failure.message : String(failure))
    } finally {
      setConnecting(false)
    }
  }, [])

  const connectLink = useCallback(
    (link: string) => {
      const connection = parseStudioLink(link)
      if (connection === null) {
        setError('That is not a studio link')
        return
      }
      void connect(connection)
    },
    [connect]
  )

  useEffect(() => {
    void shortcutStore.load()
  }, [])

  useEffect(() => {
    void window.studio.initialLink().then((link) => link !== null && connectLink(link))
    return window.studio.onLink(connectLink)
  }, [connectLink])

  if (session === null) {
    return <ConnectScreen connecting={connecting} error={error} onConnect={connect} onLink={connectLink} />
  }
  return (
    <MapStudio
      key={`${session.client.connection.port}`}
      client={session.client}
      world={session.world}
      theme={theme}
      onDisconnect={() => setSession(null)}
    />
  )
}

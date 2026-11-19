import { BridgeError, STATUS_UNAVAILABLE, type BridgeClient } from '../bridge/BridgeClient'
import type { AssetSource } from './blockModels'
import type { ImageSource } from './textureAtlas'

const RETRY_MILLIS = 2000
const LONGEST_WAIT_MILLIS = 10 * 60 * 1000

/**
 * The Minecraft client's assets through the bridge. The server fetches the client the first time anyone asks, so
 * until it has it every request waits, telling {@code onWaiting} why.
 */
export class BridgeAssets implements AssetSource, ImageSource {
  constructor(
    private readonly client: BridgeClient,
    private readonly onWaiting: (reason: string | null) => void
  ) {}

  async json(path: string): Promise<unknown> {
    const response = await this.fetch(path)
    return response === null ? null : response.json()
  }

  async image(path: string): Promise<ImageBitmap | null> {
    const response = await this.fetch(path)
    return response === null ? null : createImageBitmap(await response.blob())
  }

  private async fetch(path: string): Promise<Response | null> {
    const giveUpAt = Date.now() + LONGEST_WAIT_MILLIS
    for (;;) {
      try {
        const response = await this.client.asset(path)
        this.onWaiting(null)
        return response
      } catch (error) {
        if (!(error instanceof BridgeError) || error.status !== STATUS_UNAVAILABLE || Date.now() > giveUpAt) {
          throw error
        }
        this.onWaiting(error.message)
        await new Promise((resume) => window.setTimeout(resume, RETRY_MILLIS))
      }
    }
  }
}

import { readFile } from 'node:fs/promises'
import path from 'node:path'

export default {
  name: 'iris-noise',
  description: 'Verify expression and interpolated noise produce varied, bounded terrain visible to a player.',
  async run(context) {
    const source = await readFile(path.join(context.server.directory, '.server-source'), 'utf8')
    context.expect(/^isolated=true\s*$/m.test(source), 'Noise generation requires an isolated instance')
    await context.step('create a world with expression and Clover noise', async () => {
      const response = await context.command('/iris create name=noise_probe type=noise-fixture seed=78264193',
        /Successfully created your world|Failed to create/, 180000)
      context.expect(response.includes('Successfully created your world'), response)
    })
    await context.step('client observes bounded terrain with varying heights', async () => {
      let columns = 0
      let minimum = Infinity
      let maximum = -Infinity
      await context.waitUntil(() => {
        columns = 0
        minimum = Infinity
        maximum = -Infinity
        const center = context.bot.entity.position.floored()
        for (let x = -8; x <= 8; x++) {
          for (let z = -8; z <= 8; z++) {
            let surface = null
            for (let y = 60; y <= 100; y++) {
              const position = center.offset(x, y - center.y, z)
              const block = context.bot.blockAt(position)
              if (!block) return false
              if (block.name === 'lime_wool') {
                if (surface !== null || context.bot.blockAt(position.offset(0, 1, 0))?.name !== 'air') return false
                surface = y
              }
            }
            if (surface === null || surface < 64 || surface > 96) return false
            columns++
            minimum = Math.min(minimum, surface)
            maximum = Math.max(maximum, surface)
          }
        }
        return columns === 289 && maximum - minimum >= 3
      }, { label: '289 noise columns with bounded, varied surfaces', timeoutMs: 60000, intervalMs: 250 })
      context.expect(columns === 289 && maximum - minimum >= 3, 'Client verified noise terrain', { columns, minimum, maximum })
      context.report.noiseTerrain = { columns, minimum, maximum }
    })
  }
}

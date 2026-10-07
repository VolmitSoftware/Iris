import { readFile } from 'node:fs/promises'
import path from 'node:path'

export default {
  name: 'iris-decorators',
  description: 'Verify authored stair decorations occupy one block and retain their half state.',
  async run(context) {
    const source = await readFile(path.join(context.server.directory, '.server-source'), 'utf8')
    context.expect(/^isolated=true\s*$/m.test(source), 'Decorator generation requires an isolated instance')
    await context.step('create a world with authored stair decorations', async () => {
      const response = await context.command('/iris create name=decorator_probe type=decorator-fixture seed=78264193',
        /Successfully created your world|Failed to create/, 180000)
      context.expect(response.includes('Successfully created your world'), response)
    })
    await context.step('client sees single bottom-half stair decorations', async () => {
      let checked = 0
      await context.waitUntil(() => {
        checked = 0
        const center = context.bot.entity.position.floored()
        for (let x = -6; x <= 6; x++) {
          for (let z = -6; z <= 6; z++) {
            for (let y = -4; y <= 1; y++) {
              const position = center.offset(x, y, z)
              if (context.bot.blockAt(position)?.name !== 'lime_wool') continue
              const decoration = context.bot.blockAt(position.offset(0, 1, 0))
              const above = context.bot.blockAt(position.offset(0, 2, 0))
              if (!decoration || !above) return false
              if (decoration.name !== 'oak_stairs' || decoration.getProperties().half !== 'bottom'
                || above.name !== 'air') return false
              checked++
            }
          }
        }
        return checked >= 100
      }, { label: 'At least 100 authored stair columns with empty space above', timeoutMs: 60000, intervalMs: 250 })
      context.expect(checked >= 100, 'Client verified generated decorator columns', { checked })
      context.report.decoratorColumns = checked
    })
  }
}

import { readFile } from 'node:fs/promises'
import path from 'node:path'

export default {
  name: 'iris-craftengine-native',
  description: 'Verify deferred native invalidation and loaded neighbor cleanup with real Iris and CraftEngine.',
  async run(context) {
    const source = await readFile(path.join(context.server.directory, '.server-source'), 'utf8')
    context.expect(/^isolated=true\s*$/m.test(source), 'CraftEngine regression requires an isolated instance')
    await context.step('create and enter a real Iris world with CraftEngine content', async () => {
      const response = await context.command('/iriscenative prepare', /CE_READY|CE_FAILED/, 180000)
      context.expect(response.includes('CE_READY'), response)
    })
    await context.step('native writes remove pending block, chest, and flatten furniture records', async () => {
      const response = await context.command('/iriscenative pending', /CE_PENDING|CE_FAILED/, 60000)
      context.expect(response.includes('CE_PENDING intact=1 air=1 wall=1 invalidated=1 flatten=1 chest=1 furniture=1'), response)
      await context.waitUntil(() => {
        const origin = context.bot.entity.position.floored()
        const cut = context.bot.blockAt(origin.set(4, 180, 8))
        const wall = context.bot.blockAt(origin.set(6, 180, 8))
        const invalidated = context.bot.blockAt(origin.set(8, 180, 8))
        const chest = context.bot.blockAt(origin.set(14, 180, 8))
        return cut?.name === 'air' && wall?.name === 'cobblestone'
          && invalidated?.name === 'air' && chest?.name === 'cobblestone'
      }, { label: 'Client observes retained native air and walls', timeoutMs: 30000, intervalMs: 100 })
    })
    await context.step('regeneration removes target furniture and preserves neighbor ownership', async () => {
      const ready = await context.command('/iriscenative neighbor', /CE_NEIGHBOR_READY|CE_FAILED/, 30000)
      context.expect(ready.includes('CE_NEIGHBOR_READY chunk=2,0 furniture=3 target=1'), ready)
      const regenerated = await context.command('/iris developer regen radius=0', /Regen complete:|Regen failed:/, 90000)
      context.expect(regenerated.includes('Regen complete:'), regenerated)
      const response = await context.command('/iriscenative loaded', /CE_LOADED|CE_FAILED/, 30000)
      context.expect(response.includes('CE_LOADED removed=1 untouched=1 replacement=1 wall=1 target=1'), response)
    })
    await context.step('imported snow features replace generated furniture records', async () => {
      const response = await context.command('/iriscenative features', /CE_FEATURES|CE_FAILED/, 180000)
      context.expect(response.includes('CE_FEATURES snow=256 pending=0 furniture=0'), response)
      await context.waitUntil(() => {
        const position = context.bot.entity.position.floored()
        for (let x = 0; x < 16; x++) {
          for (let z = 0; z < 16; z++) {
            if (context.bot.blockAt(position.set(x, 97, z))?.name !== 'snow') return false
          }
        }
        return true
      }, { label: 'Client observes 256 imported snow layers', timeoutMs: 30000, intervalMs: 100 })
    })
  }
}

import { readFile } from 'node:fs/promises'
import path from 'node:path'

const world = 'native-terrain-support'
const air = new Set(['air', 'cave_air', 'void_air'])

export default {
  name: 'iris-native-terrain-support',
  description: 'Verify native terrain fitting preserves buried caves and excludes trees from lowered ground.',
  async run(context) {
    const bot = context.bot
    const source = await readFile(path.join(context.server.directory, '.server-source'), 'utf8')
    context.expect(/^isolated=true\s*$/m.test(source), 'Requires an isolated instance')
    const position = bot.entity.position.clone()
    const at = (x, y, z) => bot.blockAt(position.clone().set(x, y, z))
    const teleport = async (x, y, z) => {
      await context.command(`/minecraft:tp @s ${x + 0.5} ${y} ${z + 0.5}`, /Teleported/i, 60000)
      await context.waitUntil(() => Math.abs(bot.entity.position.x - x - 0.5) < 1
        && Math.abs(bot.entity.position.z - z - 0.5) < 1 && at(x, y, z),
      { label: 'Coordinate chunk received', timeoutMs: 90000, intervalMs: 100 })
    }
    await context.step('enter the seeded overworld', async () => {
      if (context.options.command !== 'reuse') {
        await context.command(`/iris create name=${world} type=overworld seed=1337`,
          /Successfully created your world/i, 240000)
      }
      await context.command(`/iris tp ${world}`)
      await context.waitUntil(async () => {
        const snapshot = await context.observe()
        const player = snapshot.players.find(entry => entry.username === bot.username)
        return snapshot.worlds.some(entry => entry.id === player?.world && entry.name.endsWith(world))
      }, { label: 'Entered seeded overworld', timeoutMs: 90000, intervalMs: 100 })
      await context.command('/minecraft:gamemode spectator @s', /Spectator Mode/i)
      await context.command('/minecraft:seed', /Seed:.*1337/)
    })
    await context.step('preserve caves beneath the ruined portal terrain skirt', async () => {
      await teleport(995, 44, -3611)
      await context.waitUntil(() => at(990, 43, -3626) && at(992, 43, -3627),
        { label: 'Cave columns received', timeoutMs: 90000, intervalMs: 100 })
      for (const [x, z, bottom, top] of [[990, -3626, 33, 54], [992, -3627, 34, 55]]) {
        for (let y = bottom; y <= top; y++) {
          const block = at(x, y, z)
          context.expect(block && air.has(block.name), 'Native terrain fitting filled a buried cave',
            { x, y, z, material: block?.name })
        }
        await context.command(`/execute if block ${x} 43 ${z} minecraft:cave_air run say CAVE_PRESERVED_${x}`,
          new RegExp(`CAVE_PRESERVED_${x}`))
      }
    })
    await context.step('exclude the pine whose support is removed by village grading', async () => {
      await teleport(-2601, 174, -7063)
      await context.waitUntil(() => at(-2601, 167, -7063),
        { label: 'Pine column received', timeoutMs: 90000, intervalMs: 100 })
      const block = at(-2601, 167, -7063)
      context.expect(air.has(block.name), 'Tree remains above terrain removed by native grading',
        { x: -2601, y: 167, z: -7063, material: block.name })
      await context.command('/execute if block -2601 167 -7063 minecraft:air run say PINE_EXCLUDED',
        /PINE_EXCLUDED/)
    })
  }
}

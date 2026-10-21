import { createHash } from 'node:crypto'
import { readFile, writeFile } from 'node:fs/promises'
import path from 'node:path'

const worlds = [
  { name: 'seed-selection-default', seed: '1337', argument: '' },
  { name: 'seed-selection-positive', seed: '78264193', argument: ' seed=78264193' },
  { name: 'seed-selection-negative', seed: '-734829104958217364', argument: ' seed=-734829104958217364' },
  { name: 'seed-selection-zero', seed: '0', argument: ' seed=0' }
]

export default {
  name: 'iris-seed-selection',
  description: 'Verify omitted, positive, negative, and zero creation seeds against visible terrain and a cold restart.',
  async run(context) {
    const source = await readFile(path.join(context.server.directory, '.server-source'), 'utf8')
    context.expect(/^isolated=true\s*$/m.test(source), 'Seed selection requires an isolated instance')
    const wait = (predicate, label) => context.waitUntil(predicate, { label, timeoutMs: 60000, intervalMs: 100 })
    const restart = context.options.command === 'restart-check'
    const stateFile = path.join(context.server.directory, '.seed-selection.json')
    const saved = restart ? JSON.parse(await readFile(stateFile, 'utf8')) : undefined
    const jarHash = createHash('sha256').update(await readFile(path.join(context.server.directory, 'plugins', 'Iris.jar'))).digest('hex')
    const processId = (await context.observe()).processId
    if (restart) {
      context.expect(saved.processId !== processId, 'Restart-check requires a cold server restart')
      context.expect(saved.jarHash === jarHash, 'Restart-check requires the same Iris jar')
    }
    const outputs = {}
    for (const world of worlds) {
      await context.step(`${restart ? 'Reload' : 'Create'} ${world.name} with seed ${world.seed}`, async () => {
        if (!restart) {
          await context.command(`/iris create name=${world.name} type=cohesion${world.argument}`,
            /Successfully created your world/i, 180000)
        }
        await context.command(`/iris tp ${world.name}`)
        await wait(async () => {
          const observation = await context.observe()
          const player = observation.players.find(entry => entry.username === context.bot.username)
          const current = observation.worlds.find(entry => entry.id === player?.world)
          return current?.key === `iris:${world.name}` || current?.name.endsWith(world.name)
        }, `Enter ${world.name}`)
        await context.command('/minecraft:gamemode spectator @s')
        await wait(() => context.bot.game.gameMode === 'spectator', 'Spectator mode')
        await context.command('/minecraft:seed', new RegExp(`Seed:\\s*\\[?${world.seed}\\]?(?:\\s|$)`))
        const x = 1024
        const z = -1024
        await context.command(`/minecraft:tp @s ${x + 8.5} 48 ${z + 8.5}`, /Teleported/i, 30000)
        await wait(() => Math.abs(context.bot.entity.position.x - x - 8.5) < 1
          && Math.abs(context.bot.entity.position.z - z - 8.5) < 1, 'Sample teleport')
        const position = context.bot.entity.position.clone()
        await wait(() => context.bot.blockAt(position.clone().set(x, 0, z))
          && context.bot.blockAt(position.clone().set(x + 15, 63, z + 15)), 'Complete sample chunk')
        const digest = createHash('sha256')
        let stone = 0
        for (let dx = 0; dx < 16; dx++) {
          for (let dz = 0; dz < 16; dz++) {
            for (let y = 0; y < 64; y++) {
              const block = context.bot.blockAt(position.set(x + dx, y, z + dz))
              context.expect(block !== null, 'Missing sampled block', { x: x + dx, y, z: z + dz })
              digest.update(`${block.stateId},`)
              if (block.name === 'stone') stone++
            }
          }
        }
        context.expect(stone > 1000, 'Fixture terrain was not generated', { world: world.name, stone })
        const blocks = digest.digest('hex')
        outputs[world.name] = { seed: world.seed, blocks }
        if (restart) {
          context.expect(saved.worlds[world.name].seed === world.seed, 'Seed expectation changed after restart')
          context.expect(saved.worlds[world.name].blocks === blocks, 'Persisted terrain changed after restart',
            { world: world.name, before: saved.worlds[world.name].blocks, after: blocks })
        }
      })
    }
    const signatures = new Set(Object.values(outputs).map(output => output.blocks))
    context.expect(signatures.size === worlds.length, 'Different requested seeds generated identical sample chunks', outputs)
    context.report.seedSelection = { restart, jarHash, worlds: outputs }
    if (!restart) {
      await writeFile(stateFile, JSON.stringify({ processId, jarHash, worlds: outputs }))
    }
  }
}

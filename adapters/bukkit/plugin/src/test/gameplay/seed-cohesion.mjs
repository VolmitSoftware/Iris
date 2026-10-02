import { createHash } from 'node:crypto'
import { readFile, writeFile } from 'node:fs/promises'
import path from 'node:path'

const chunks = [[-64, -64], [64, -64], [-64, 64], [64, 64]]

export default {
  name: 'iris-seed-cohesion',
  description: 'Compare client-visible decorated chunks in two same-seed worlds explored in opposite orders.',
  async run(context) {
    const source = await readFile(path.join(context.server.directory, '.server-source'), 'utf8')
    context.expect(/^isolated=true\s*$/m.test(source), 'Seed cohesion requires an isolated instance')
    const wait = (predicate, label) => context.waitUntil(predicate, { label, timeoutMs: 60000, intervalMs: 100 })
    const currentWorld = async () => {
      const observation = await context.observe()
      const player = observation.players.find(entry => entry.username === context.bot.username)
      return observation.worlds.find(entry => entry.id === player?.world)
    }
    const enter = async name => {
      await context.command(`/iris tp ${name}`)
      await wait(async () => (await currentWorld())?.key === `iris:${name}`
        || (await currentWorld())?.name.endsWith(name), `Enter ${name}`)
      await context.command('/minecraft:gamemode spectator @s')
      await wait(() => context.bot.game.gameMode === 'spectator', 'Spectator mode')
      await context.command('/minecraft:seed', /Seed:.*1337/)
    }
    const capture = async coordinates => {
      const hashes = {}
      const decorators = {}
      let decorationCount = 0
      for (const [cx, cz] of coordinates) {
        const x = cx * 16
        const z = cz * 16
        await context.command(`/minecraft:tp @s ${x + 8.5} 48 ${z + 8.5}`, /Teleported/i, 30000)
        await wait(() => Math.abs(context.bot.entity.position.x - x - 8.5) < 1
          && Math.abs(context.bot.entity.position.z - z - 8.5) < 1, 'Chunk sample teleport')
        const position = context.bot.entity.position.clone()
        await wait(() => context.bot.blockAt(position.clone().set(x, 1, z))
          && context.bot.blockAt(position.clone().set(x + 15, 63, z + 15)), 'Complete sample chunk')
        const digest = createHash('sha256')
        let decoration = 0
        let terrain = 0
        for (let dx = 0; dx < 16; dx++) {
          for (let dz = 0; dz < 16; dz++) {
            for (let y = 0; y < 64; y++) {
              const block = context.bot.blockAt(position.set(x + dx, y, z + dz))
              context.expect(block !== null, 'Missing sampled block', { x: x + dx, y, z: z + dz })
              digest.update(`${block.stateId},`)
              if (block.name === 'granite' || block.name === 'andesite') decoration++
              if (block.name === 'stone') terrain++
            }
          }
        }
        context.expect(terrain > 1000, 'Fixture terrain was not generated', { cx, cz, terrain })
        decorationCount += decoration
        decorators[`${cx},${cz}`] = decoration
        hashes[`${cx},${cz}`] = digest.digest('hex')
      }
      context.expect(decorationCount > 0, 'Fixture decorators were not generated in sampled chunks', { decorators })
      context.report.decoratorCoverage ??= []
      context.report.decoratorCoverage.push(decorators)
      return hashes
    }
    const baseline = 'seed-cohesion-forward'
    const reverse = 'seed-cohesion-reverse'
    const stateFile = path.join(context.server.directory, '.seed-cohesion.json')
    const jarHash = createHash('sha256').update(await readFile(path.join(context.server.directory, 'plugins', 'Iris.jar'))).digest('hex')
    const restart = context.options.command === 'restart-check'
    const saved = restart ? JSON.parse(await readFile(stateFile, 'utf8')) : undefined
    if (restart) {
      context.expect(jarHash === saved.jarHash, 'Restart-check requires the same Iris jar', { before: saved.jarHash, after: jarHash })
      context.expect((await context.observe()).processId !== saved.processId, 'Restart-check requires a cold server restart')
    }
    const outputs = []
    for (const [name, coordinates] of [[baseline, chunks], [reverse, [...chunks].reverse()]]) {
      await context.step(`${restart ? 'Reload' : 'Create'} and sample ${name}`, async () => {
        if (!restart) {
          await context.command(`/iris create name=${name} type=cohesion seed=1337`, /Successfully created your world/i, 180000)
        }
        await enter(name)
        const output = await capture(coordinates)
        if (restart) {
          for (const [coordinate, hash] of Object.entries(output)) {
            context.expect(saved.hashes[coordinate] === hash, 'Persisted chunk changed after restart', { name, coordinate, before: saved.hashes[coordinate], after: hash })
          }
          Object.assign(output, await capture(coordinates.map(([cx, cz]) => [cx * 3, cz * 3])))
        }
        outputs.push(output)
      })
    }
    await context.step('Compare all sampled blocks', async () => {
      for (const [coordinate, hash] of Object.entries(outputs[0])) {
        context.expect(outputs[1][coordinate] === hash, 'Same-seed worlds differ',
          { coordinate, forward: hash, reverse: outputs[1][coordinate] })
      }
      context.report.seedCohesion = { seed: 1337, jarHash, blocksPerChunk: 16384, restart, hashes: outputs[0] }
      if (!restart) {
        await writeFile(stateFile, JSON.stringify({ processId: (await context.observe()).processId, jarHash, hashes: outputs[0] }))
      }
    })
  }
}

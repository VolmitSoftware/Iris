import { createHash } from 'node:crypto'
import { readFile, realpath } from 'node:fs/promises'
import path from 'node:path'

export default {
  name: 'iris-object-scaling',
  description: 'Verify fractional mushroom scaling retains its cap and uses its stem for organic supports.',
  async run(context) {
    const directory = await realpath(context.server.directory)
    const source = await readFile(path.join(directory, '.server-source'), 'utf8')
    context.expect(/^isolated=true\s*$/m.test(source), 'Object scaling requires an isolated instance')
    const pack = await realpath(path.join(directory, 'plugins', 'Iris', 'packs', 'object-scaling-fixture'))
    context.expect(pack.startsWith(`${directory}${path.sep}`), 'Object scaling requires a local fixture pack')
    const object = await readFile(path.join(pack, 'objects', 'smolshroom1.iob'))
    const objectHash = createHash('sha256').update(object).digest('hex')
    const biome = JSON.parse(await readFile(path.join(pack, 'biomes', 'flat.json'), 'utf8'))
    const surfaceY = biome.generators[0].min
    const originalStemY = surfaceY + biome.objects[0].translate.y
    const expectedCapY = originalStemY + 1
    const maximumExtension = biome.objects[0].stiltSettings.organicMaxScan
    context.expect(surfaceY === 32 && expectedCapY === 39 && maximumExtension === 8,
      'Expected the bounded organic support fixture', { surfaceY, expectedCapY, maximumExtension })
    const world = context.options.command ?? 'object_scaling_probe'
    context.expect(/^[a-z0-9_]+$/.test(world), 'Fixture world name must be a simple identifier')
    await context.step('create the fractional mushroom fixture world', async () => {
      const response = await context.command(`/iris create name=${world} type=object-scaling-fixture seed=1337`,
        /Successfully created your world|World ready|Failed to create|Exception raised/i, 180000)
      context.expect(/Successfully created your world|World ready/i.test(response), response)
      await context.command(`/iris tp ${world}`)
      await context.waitUntil(async () => {
        const observation = await context.observe()
        const player = observation.players.find(entry => entry.username === context.bot.username)
        const current = observation.worlds.find(entry => entry.id === player?.world)
        return current?.key === `iris:${world}` || current?.name.endsWith(world)
      }, { label: 'Enter the scaling fixture world', timeoutMs: 60000, intervalMs: 100 })
      await context.command('/minecraft:gamemode spectator @s')
      await context.waitUntil(() => context.bot.game.gameMode === 'spectator',
        { label: 'Spectator mode', timeoutMs: 30000, intervalMs: 100 })
      await context.command('/minecraft:tp @s 0.5 48 0.5', /Teleported/i, 30000)
    })
    const columns = []
    await context.step('client sees one cap and continuous stem supports', async () => {
      const position = context.bot.entity.position.clone()
      await context.waitUntil(() => {
        if (Math.abs(context.bot.entity.position.x - 0.5) > 1
          || Math.abs(context.bot.entity.position.z - 0.5) > 1) return false
        for (const x of [-16, 0, 16, 31]) {
          for (const z of [-16, 0, 16, 31]) {
            for (const y of [0, 32, 63]) {
              if (!context.bot.blockAt(position.set(x, y, z))) return false
            }
          }
        }
        return true
      }, { label: 'Complete mushroom fixture chunks', timeoutMs: 60000, intervalMs: 100 })
      for (let x = -16; x < 32; x++) {
        for (let z = -16; z < 32; z++) {
          const caps = []
          const stems = []
          for (let y = 1; y < 63; y++) {
            const block = context.bot.blockAt(position.set(x, y, z))
            context.expect(block !== null, 'Every sampled mushroom block must be loaded', { x, y, z })
            if (block.name === 'red_mushroom_block') caps.push(y)
            if (block.name === 'mushroom_stem') stems.push(y)
          }
          if (caps.length === 0) continue
          context.expect(caps.length === 1, 'Mushroom cap was copied into its stem or support', { x, z, caps })
          const capY = caps[0]
          context.expect(capY === expectedCapY, 'Fractional scaling changed the expected mushroom height',
            { x, z, capY, expectedCapY })
          const bottomY = stems[0]
          const stemBlocks = stems.length
          context.expect(bottomY >= originalStemY - maximumExtension && bottomY <= surfaceY + 1,
            'Mushroom support must reach the surface within its configured extension limit',
            { x, z, capY, bottomY, surfaceY, maximumExtension, stems })
          context.expect(stemBlocks === capY - bottomY
            && stems.every((y, index) => y === bottomY + index),
          'Scaled mushroom must have a continuous stem from its cap to the ground', { x, z, capY, stems })
          const supportY = bottomY - 1
          const support = context.bot.blockAt(position.set(x, supportY, z))?.name
          const above = context.bot.blockAt(position.set(x, capY + 1, z))?.name
          context.expect(support === (supportY === surfaceY ? 'lime_wool' : 'stone'),
            'Mushroom stem must terminate on the fixture terrain', { x, z, capY, supportY, support })
          context.expect(above === 'air', 'Scaled mushroom has unexpected blocks above its cap', { x, z, capY, above })
          columns.push({ x, z, surfaceY, capY, bottomY, stemBlocks,
            buriedStemBlocks: Math.max(0, surfaceY - bottomY + 1) })
        }
      }
      context.expect(columns.length >= 8, 'Expected at least eight scaled mushroom columns', { columns })
    })
    await context.step('server confirms the sampled stem and cap materials', async () => {
      for (const [index, column] of columns.slice(0, 8).entries()) {
        const { x, z, surfaceY, capY, bottomY } = column
        const marker = `IRIS_SCALING_OK_${index}`
        await context.command(`/minecraft:execute if block ${x} ${surfaceY + 1} ${z} minecraft:mushroom_stem`
          + ` if block ${x} ${bottomY} ${z} minecraft:mushroom_stem`
          + ` if block ${x} ${capY} ${z} minecraft:red_mushroom_block run minecraft:say ${marker}`,
        new RegExp(marker), 10000)
      }
    })
    context.report.objectScaling = { world, scale: 0.75, object: 'smolshroom1', objectHash,
      surfaceY, maximumExtension, checkedColumns: columns.length,
      serverConfirmedColumns: Math.min(8, columns.length), columns }
  }
}

import { readFile, stat } from 'node:fs/promises'
import { createHash } from 'node:crypto'
import path from 'node:path'

export default {
  name: 'iris-studio-open',
  description: 'Measure repeated Studio entry and verify visible terrain, spectator mode, and world cleanup.',
  async run(context) {
    const source = await readFile(path.join(context.server.directory, '.server-source'), 'utf8')
    context.expect(/^isolated=true\s*$/m.test(source), 'Studio entry requires an isolated instance')
    const match = /^([a-z0-9_-]+):([1-4])$/.exec(context.options.command ?? 'overworld:2')
    context.expect(match, 'Expected pack:cycles with one to four cycles')
    context.report.studioOpen = []
    for (let cycle = 0; cycle < Number(match[2]); cycle++) {
      await context.step(`open and close Studio ${cycle + 1}`, async () => {
        const before = new Set((await context.observe()).worlds.map(world => world.id))
        const start = Date.now()
        let world
        let center
        await context.command(`/iris studio open ${match[1]} seed=1337`)
        await context.waitUntil(async () => {
          const observation = await context.observe()
          const player = observation.players.find(entry => entry.username === context.bot.username)
          world = observation.worlds.find(entry => entry.id === player?.world)
          if (!world || before.has(world.id) || !/iris-[0-9a-f-]{36}$/.test(world.name)) return false
          center = context.bot.entity.position.floored()
          return world.loadedChunks > 0 && context.bot.blockAt(center) !== null && context.bot.game.gameMode === 'spectator'
        }, { label: 'Studio arrival with terrain and spectator mode', timeoutMs: 180000, intervalMs: 100 })
        const arrivalMs = Date.now() - start
        context.expect(Number.isInteger(context.bot.game.minY) && Number.isInteger(context.bot.game.height),
          'Studio dimension must report its vertical range')
        await context.waitUntil(() => {
          for (let x = -1; x <= 1; x++) {
            for (let z = -1; z <= 1; z++) {
              for (let y = context.bot.game.minY; y < context.bot.game.minY + context.bot.game.height; y += 16) {
                if (context.bot.blockAt(center.offset(x, y - center.y, z)) === null) return false
              }
            }
          }
          return true
        }, { label: 'Complete neighboring Studio columns', timeoutMs: 60000, intervalMs: 100 })
        const digest = createHash('sha256')
        let solid = 0
        for (let x = -1; x <= 1; x++) {
          for (let z = -1; z <= 1; z++) {
            for (let y = context.bot.game.minY; y < context.bot.game.minY + context.bot.game.height; y++) {
              const block = context.bot.blockAt(center.offset(x, y - center.y, z))
              context.expect(block !== null, 'Studio entry column must be available')
              digest.update(`${block.name}\n`)
              if (!['air', 'cave_air', 'void_air', 'water', 'lava'].includes(block.name)) solid++
            }
          }
        }
        context.expect(solid > 0, 'Studio entry must contain solid terrain')
        context.report.studioOpen.push({ cycle, arrivalMs, world: world.name, x: center.x, z: center.z,
          terrainHash: digest.digest('hex'), solid })
        const closed = await context.command('/iris studio close', /^Studio closed\.$|Studio close failed:/, 180000)
        context.expect(closed === 'Studio closed.', closed)
        await context.waitUntil(async () => !(await context.observe()).worlds.some(entry => entry.id === world.id),
          { label: 'Studio world unload', timeoutMs: 90000, intervalMs: 100 })
        const transientName = /iris-[0-9a-f-]{36}$/.exec(world.name)[0]
        const properties = await readFile(path.join(context.server.directory, 'server.properties'), 'utf8')
        const level = /^level-name=(.+)$/m.exec(properties)?.[1].trim() ?? 'world'
        const directory = path.join(context.server.directory, level, 'dimensions', 'iris', transientName)
        await context.waitUntil(async () => {
          try { await stat(directory); return false } catch (error) {
            if (error.code === 'ENOENT') return true
            throw error
          }
        }, { label: 'Studio directory cleanup', timeoutMs: 90000, intervalMs: 100 })
      })
    }
  }
}

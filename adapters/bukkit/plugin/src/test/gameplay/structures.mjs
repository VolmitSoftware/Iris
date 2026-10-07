import { readFile } from 'node:fs/promises'
import { createHash } from 'node:crypto'
import path from 'node:path'

export default {
  name: 'iris-structures',
  description: 'Verify surface foundations, buried rooms, and waterlogged object blocks in generated chunks.',
  async run(context) {
    const source = await readFile(path.join(context.server.directory, '.server-source'), 'utf8')
    context.expect(/^isolated=true\s*$/m.test(source), 'Structure generation requires an isolated instance')
    const world = context.options.command ?? 'structure_probe'
    context.expect(/^[a-z0-9_]+$/.test(world), 'Fixture world name must be a simple identifier')
    await context.step('create the structure fixture world', async () => {
      const response = await context.command(`/iris create name=${world} type=structure-fixture seed=78264193`,
        /World ready|Failed to create/, 180000)
      context.expect(response.includes('World ready'), response)
    })
    await context.step('observe structures and objects above and below terrain', async () => {
      await context.waitUntil(() => {
        if (Math.abs(context.bot.entity.position.x) > 32 || Math.abs(context.bot.entity.position.z) > 32) return false
        for (let x = -16; x < 32; x += 16) {
          for (let z = -16; z < 32; z += 16) {
            for (let y = 16; y <= 80; y += 16) {
              if (!context.bot.blockAt(context.bot.entity.position.clone().set(x, y, z))) return false
            }
          }
        }
        return true
      }, { label: 'Complete structure fixture chunks', timeoutMs: 60000, intervalMs: 100 })
      const digest = createHash('sha256')
      const counts = { emerald_block: 0, diamond_block: 0, blue_concrete: 0, gold_block: 0, oak_slab: 0 }
      for (let x = -16; x < 32; x++) {
        for (let z = -16; z < 32; z++) {
          for (let y = 16; y <= 80; y++) {
            const block = context.bot.blockAt(context.bot.entity.position.clone().set(x, y, z))
            context.expect(block !== null, 'Every sampled structure block must be loaded')
            digest.update(`${block.stateId}\n`)
            if (Object.hasOwn(counts, block.name)) counts[block.name]++
            if (block.name === 'emerald_block') context.expect(y > 64, 'Surface platform must be above terrain')
            if (block.name === 'blue_concrete') context.expect(y < 40, 'Buried room must remain underground')
            if (block.name === 'oak_slab') context.expect(block.getProperties().waterlogged === false,
              'Object slabs placed on dry ground must clear waterlogging')
          }
        }
      }
      for (const [block, count] of Object.entries(counts)) {
        context.expect(count > 0, `Generated fixture must contain ${block}`, { count })
      }
      context.report.structures = { counts, hash: digest.digest('hex') }
    })
  }
}

import { readFile, writeFile } from 'node:fs/promises'
import path from 'node:path'
import { setup, openStudio, closeStudio, sampleTerrain, readJson, writeJson,
  logPosition, logSince } from './studio-support.mjs'

async function hotload(context, fixture, active, command) {
  const before = await readJson(fixture.manifestPath(active.key))
  const response = await context.command(command, /^Studio hotloaded\.$|^Studio hotload failed:/, 90000)
  context.expect(response === 'Studio hotloaded.', 'The manual Studio command did not complete successfully', { command, response })
  const after = await readJson(fixture.manifestPath(active.key))
  context.expect(after.pendingActivationId === null, 'The manual hotload completed with a pending activation')
  context.report.iris.manualHotloads.push({ command, response,
    before: before.activeActivationId, after: after.activeActivationId })
  return after
}

export default {
  name: 'iris-studio-manual-hotload',
  description: 'Refresh an open Studio through hotload and its refresh alias, verify terrain, and reject commands without a Studio.',
  async run(context) {
    const fixture = await setup(context)
    const biomeFile = path.join(fixture.pack, 'biomes', 'flat.json')
    const original = await readFile(biomeFile, 'utf8')
    const logOffset = await logPosition(fixture)
    const processId = (await context.observe()).processId
    context.report.iris.manualHotloads = []
    let active
    try {
      await context.step('Reject hotload without an open Studio', async () => {
        await context.command('/iris studio hotload', /^No open studio projects\.$/, 10000)
      })
      await context.step('Open Studio and hotload an unchanged pack', async () => {
        active = await openStudio(context, fixture)
        await sampleTerrain(context, fixture, 0, fixture.state.material)
        await hotload(context, fixture, active, '/iris studio hotload')
        await sampleTerrain(context, fixture, 4096, fixture.state.material)
      })
      await context.step('Refresh an edited pack and inspect newly generated terrain', async () => {
        const before = await readJson(fixture.manifestPath(active.key))
        const biome = await readJson(biomeFile)
        biome.name = 'Manual hotload terrain'
        biome.layers[0].palette[0].block = 'minecraft:gold_block'
        await writeJson(biomeFile, biome)
        await hotload(context, fixture, active, '/iris studio refresh')
        const after = await fixture.wait(async () => {
          const manifest = await readJson(fixture.manifestPath(active.key))
          return manifest.activeActivationId !== before.activeActivationId && manifest.pendingActivationId === null ? manifest : false
        }, 'The edited Studio pack did not become active', 90000)
        context.report.iris.activations.push({ before: before.activeActivationId,
          after: after.activeActivationId, material: 'gold_block' })
        await sampleTerrain(context, fixture, 16384, 'gold_block')
        await sampleTerrain(context, fixture, 0, fixture.state.material)
      })
      await context.step('Close Studio and reject another refresh', async () => {
        await closeStudio(context, fixture, active)
        active = undefined
        await context.command('/iris studio refresh', /^No open studio projects\.$/, 10000)
        await context.command('/minecraft:seed', /Seed:/i, 10000)
      })
      context.expect((await context.observe()).processId === processId, 'The server restarted during manual Studio hotload')
      const log = await logSince(fixture, logOffset)
      context.expect(!/Studio generation update failed|Studio hotload failed|Unable to (capture terrain|save the terrain boundary)|ClosedChannelException|Chunk system error|server has stopped responding|could not acquire its engine runtime|Failed to list files/i.test(log),
        'Manual Studio hotload emitted a generation, snapshot, storage, or watchdog failure')
    } finally {
      try {
        if (active) await closeStudio(context, fixture, active)
      } finally {
        await writeFile(biomeFile, original)
      }
    }
  }
}

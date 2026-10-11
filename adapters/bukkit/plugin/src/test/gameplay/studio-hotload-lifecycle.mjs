import { readFile, writeFile } from 'node:fs/promises'
import path from 'node:path'
import { setup, openStudio, closeStudio, sampleTerrain, readJson, writeJson,
  logPosition, logSince } from './studio-support.mjs'

export default {
  name: 'iris-studio-hotload-lifecycle',
  description: 'Generate cold chunks during Studio edits and close after saving an edit.',
  async run(context) {
    const fixture = await setup(context)
    const biomeFile = path.join(fixture.pack, 'biomes', 'flat.json')
    const original = await readFile(biomeFile, 'utf8')
    const logOffset = await logPosition(fixture)
    let active
    let x = 16384
    try {
      active = await openStudio(context, fixture)
      await sampleTerrain(context, fixture, 0, fixture.state.material)
      for (let cycle = 0; cycle < 3; cycle++) {
        await context.step(`Load cold chunks during hotload ${cycle + 1}`, async () => {
          const manifestFile = fixture.manifestPath(active.key)
          const before = await readJson(manifestFile)
          const biome = await readJson(biomeFile)
          const material = cycle % 2 === 0 ? 'gold_block' : 'diamond_block'
          biome.name = `Lifecycle terrain ${cycle + 1}`
          biome.layers[0].palette[0].block = `minecraft:${material}`
          const startedAt = Date.now()
          await writeJson(biomeFile, biome)
          x += 512
          await context.command(`/minecraft:tp @s ${x + 0.5} 101 0.5`, /Teleported/i, 15000)
          const after = await fixture.wait(async () => {
            const manifest = await readJson(manifestFile)
            return manifest.activeActivationId !== before.activeActivationId ? manifest : false
          }, 'Hotload did not complete while the player requested fresh chunks', 90000)
          const activationDurationMs = Date.now() - startedAt
          await fixture.wait(() => Math.abs(context.bot.entity.position.x - x - 0.5) < 1,
            'Cold chunk teleport did not arrive after hotload', 30000)
          context.report.iris.activations.push({ before: before.activeActivationId,
            after: after.activeActivationId, material, coldChunkRequests: 1, activationDurationMs })
          x += 2048
          await sampleTerrain(context, fixture, x, material)
        })
      }
      await context.step('Close Studio while a saved edit is activating', async () => {
        const biome = await readJson(biomeFile)
        biome.name = 'Lifecycle close terrain'
        await writeJson(biomeFile, biome)
        const pending = await fixture.wait(async () => {
          const manifest = await readJson(fixture.manifestPath(active.key))
          return manifest.pendingActivationId !== null ? manifest : false
        }, 'The saved close edit did not enter activation', 90000)
        context.report.iris.closePendingActivationId = pending.pendingActivationId
        await closeStudio(context, fixture, active)
        active = undefined
        await context.command('/minecraft:seed', /Seed:/i, 10000)
      })
      await context.step('Reopen the edited pack and retain usable generation', async () => {
        active = await openStudio(context, fixture)
        await sampleTerrain(context, fixture, 0, 'gold_block')
        await closeStudio(context, fixture, active)
        active = undefined
      })
      const log = await logSince(fixture, logOffset)
      context.expect(!/Studio generation update failed|Unable to (capture terrain|save the terrain boundary)|ClosedChannelException|Chunk system error|server has stopped responding|could not acquire its engine runtime|Failed to list files/i.test(log),
        'Studio lifecycle emitted a generation, snapshot, storage, or watchdog failure')
    } finally {
      if (active) await closeStudio(context, fixture, active)
      await writeFile(biomeFile, original)
    }
  }
}

import { readFile, writeFile, cp } from 'node:fs/promises'
import { fileURLToPath } from 'node:url'
import path from 'node:path'
import { packKey, worldKey, seed, parsePhase, setup, exists, writeJson, readJson,
  openStudio, closeStudio, sampleTerrain, logPosition, logSince } from './studio-support.mjs'

async function prepare(context, fixture) {
  context.expect(!(await exists(fixture.marker)) && !(await exists(fixture.pack)), 'Prepare requires a new disposable instance and an unused fixture pack')
  const settingsFile = path.join(fixture.irisRoot, 'iris.json')
  const settings = await readJson(settingsFile)
  settings.studio = { ...settings.studio, openVSCode: false, entitySpawning: false }
  await writeJson(settingsFile, settings)
  await context.command(`/iris studio create name=${packKey}`, new RegExp(`Created studio project '${packKey}'`), 180000)
  for (const relative of [`dimensions/${packKey}.json`, 'regions/starter.json', 'biomes/starter.json', `${packKey}.code-workspace`]) {
    context.expect(await exists(path.join(fixture.pack, relative)), `Studio create omitted ${relative}`)
  }
  const source = fileURLToPath(new URL('./native-terrain-pack/', import.meta.url))
  for (const directory of ['regions', 'biomes', 'generators']) {
    await cp(path.join(source, directory), path.join(fixture.pack, directory), { recursive: true })
  }
  const dimension = await readJson(path.join(source, 'dimensions', 'native-terrain.json'))
  dimension.name = 'Bot Studio Acceptance'
  await writeJson(path.join(fixture.pack, 'dimensions', `${packKey}.json`), dimension)
  const state = { schemaVersion: 1, instance: fixture.root, pack: packKey, preparedProcess: (await context.observe()).processId,
    material: 'lime_wool', productionCreated: false, studioCompleted: false }
  await writeJson(fixture.marker, state)
  context.report.iris.next = 'Restart the isolated server before the studio phase so its pack registry is loaded.'
}

async function editBiome(context, fixture, world, material, title) {
  const file = path.join(fixture.pack, 'biomes', 'flat.json')
  const manifestFile = fixture.manifestPath(world.key)
  const before = await readJson(manifestFile)
  const biome = await readJson(file)
  biome.name = title
  biome.layers[0].palette[0].block = `minecraft:${material}`
  await writeJson(file, biome)
  const after = await fixture.wait(async () => {
    const manifest = await readJson(manifestFile)
    return manifest.activeActivationId !== before.activeActivationId ? manifest : false
  }, 'The saved pack edit did not activate through the Studio watcher', 90000)
  context.report.iris.activations.push({ world: world.id, before: before.activeActivationId, after: after.activeActivationId, material })
}

async function studio(context, fixture, cycles) {
  context.expect((await context.observe()).processId !== fixture.state.preparedProcess, 'Restart the server after prepare before opening the new pack')
  let active
  const biomeFile = path.join(fixture.pack, 'biomes', 'flat.json')
  let retainedBytes
  try {
    for (let cycle = 0; cycle < cycles; cycle++) {
      await context.step(`Open Studio and inspect the accepted pack, cycle ${cycle + 1}`, async () => {
        active = await openStudio(context, fixture)
        await fixture.wait(() => context.bot.game.gameMode === 'spectator', 'Ordinary Studio did not enter spectator mode')
        await sampleTerrain(context, fixture, 0, fixture.state.material)
      })
      await context.step(`Apply a real pack edit and inspect new terrain, cycle ${cycle + 1}`, async () => {
        const material = fixture.state.material === 'lime_wool' ? 'magenta_wool' : 'lime_wool'
        await editBiome(context, fixture, active, material, `Acceptance Cycle ${cycle + 1}`)
        await sampleTerrain(context, fixture, 4096, material)
        await sampleTerrain(context, fixture, 0, fixture.state.material)
        fixture.state.material = material
        await writeJson(fixture.marker, fixture.state)
      })
      await context.step(`Reject a malformed edit while retaining live generation, cycle ${cycle + 1}`, async () => {
        retainedBytes = await readFile(biomeFile, 'utf8')
        const before = await readJson(fixture.manifestPath(active.key))
        const logOffset = await logPosition(fixture)
        await writeFile(biomeFile, '{ "name": "Rejected fixture", "layers": [')
        const rejection = await fixture.wait(async () => {
          const log = await logSince(fixture, logOffset)
          return /Studio generation update failed/.test(log) ? log : false
        }, 'The malformed edit did not produce a Studio rejection', 90000)
        context.report.iris.rejections ??= []
        context.report.iris.rejections.push({ world: active.id, evidence: rejection.split('\n').filter(line => /Studio generation update failed/.test(line)) })
        context.expect((await readJson(fixture.manifestPath(active.key))).activeActivationId === before.activeActivationId,
          'A rejected edit changed the active generation')
        await sampleTerrain(context, fixture, 8192, fixture.state.material)
        await writeFile(biomeFile, retainedBytes)
        retainedBytes = undefined
        await editBiome(context, fixture, active, fixture.state.material, `Recovered Cycle ${cycle + 1}`)
        await sampleTerrain(context, fixture, 12288, fixture.state.material)
      })
      await context.step(`Close and remove the transient world, cycle ${cycle + 1}`, async () => {
        await closeStudio(context, fixture, active)
        active = undefined
      })
    }
    await context.step('Reopen the last accepted pack and verify persistence', async () => {
      active = await openStudio(context, fixture)
      await sampleTerrain(context, fixture, 0, fixture.state.material)
      await closeStudio(context, fixture, active)
      active = undefined
    })
    fixture.state.studioCompleted = true
    await writeJson(fixture.marker, fixture.state)
  } finally {
    if (retainedBytes !== undefined) await writeFile(biomeFile, retainedBytes)
    if (active) await closeStudio(context, fixture, active)
  }
}

async function production(context, fixture, create) {
  context.expect(fixture.state.studioCompleted, 'Complete the Studio acceptance phase before creating a production world')
  const key = `iris:${worldKey}`
  if (create) {
    context.expect(!(await exists(fixture.worldRoot(key))), 'The production fixture world already exists')
    await context.command(`/iris create name=${worldKey} type=${packKey} seed=${seed}`, /Successfully created your world/i, 180000)
    fixture.state.productionCreated = true
    fixture.state.productionProcess = (await context.observe()).processId
    fixture.state.productionMaterial = fixture.state.material
    await writeJson(fixture.marker, fixture.state)
  }
  context.expect(fixture.state.productionCreated, 'Create the production fixture before world-check')
  if (!create) context.expect((await context.observe()).processId !== fixture.state.productionProcess, 'Run world-check after a real server restart')
  const previous = await fixture.currentWorld()
  const world = await fixture.arrival(() => context.command(`/iris tp ${worldKey}`),
    candidate => candidate.name === worldKey || candidate.name === `iris_${worldKey}` || candidate.name.endsWith(`_iris_${worldKey}`), 'The bot did not enter the created Iris world')
  try {
    await context.command('/minecraft:gamemode spectator @s')
    await fixture.wait(() => context.bot.game.gameMode === 'spectator', 'Spectator mode did not reach the bot')
    await context.command('/minecraft:seed', new RegExp(`Seed:.*${seed}`))
    await sampleTerrain(context, fixture, create ? 0 : 4096, fixture.state.productionMaterial)
    {
      const file = path.join(fixture.pack, 'biomes', 'flat.json')
      const original = await readFile(file, 'utf8')
      try {
        const edited = JSON.parse(original)
        edited.layers[0].palette[0].block = 'minecraft:gold_block'
        await writeJson(file, edited)
        await sampleTerrain(context, fixture, 8192, fixture.state.productionMaterial)
      } finally { await writeFile(file, original) }
    }
    const manifest = await readJson(fixture.manifestPath(key))
    context.expect(manifest.epochs.length > 0 && manifest.pendingActivationId === null, 'Production world has no active immutable generation epoch')
    context.report.iris.productionManifest = manifest
  } finally {
    await fixture.arrival(() => context.command(`/minecraft:execute in minecraft:overworld run tp ${context.bot.username} 0 110 0`),
      candidate => candidate.id !== world.id, 'The bot did not leave the production world')
  }
  context.expect(previous.id !== world.id || !create, 'Creation did not establish a separate world')
}

async function replacement(context, fixture, check) {
  context.expect(fixture.state.productionCreated, 'Complete production-world creation before replacing the disposable vanilla world')
  const key = 'minecraft:overworld'
  if (!check) {
    context.expect(!fixture.state.replacement && !(await exists(fixture.manifestPath(key))), 'Replacement requires the original vanilla fixture world')
    context.expect(await exists(path.join(fixture.worldRoot(key), 'data', 'minecraft', 'world_gen_settings.dat')), 'The exact vanilla dimension has no saved generation settings')
    const overworld = (await context.observe()).worlds.find(world => world.name === fixture.level)
    context.expect(overworld, 'Observer did not identify the vanilla Overworld')
    fixture.state.replacement = { processId: (await context.observe()).processId, previousId: overworld.id, staged: false }
    await writeJson(fixture.marker, fixture.state)
    await context.command(`/iris replace ${key} type=${packKey} seed=${seed}`, /Staged Iris replacement for minecraft:overworld.*Restart once to publish/i, 180000)
    fixture.state.replacement.staged = true
    fixture.state.replacement.material = fixture.state.material
    await writeJson(fixture.marker, fixture.state)
    context.report.iris.next = 'Restart the isolated server, then run replace-check.'
    return
  }
  context.expect(fixture.state.replacement?.staged, 'No successful replacement stage was recorded')
  context.expect((await context.observe()).processId !== fixture.state.replacement.processId, 'Replacement must be checked after a real server restart')
  await fixture.arrival(() => context.command(`/minecraft:execute in ${key} run tp ${context.bot.username} 0 110 0`),
    candidate => candidate.name === fixture.level, 'The bot did not enter the replaced canonical Overworld')
  await context.command('/minecraft:gamemode spectator @s')
  await fixture.wait(() => context.bot.game.gameMode === 'spectator', 'Spectator mode did not reach the bot')
  await context.command('/minecraft:seed', new RegExp(`Seed:.*${seed}`))
  await sampleTerrain(context, fixture, 0, fixture.state.replacement.material)
  await sampleTerrain(context, fixture, 4096, fixture.state.replacement.material)
  const manifest = await readJson(fixture.manifestPath(key))
  context.expect(manifest.epochs.length > 0 && manifest.pendingActivationId === null, 'Replacement did not publish an immutable Iris generation')
  context.report.iris.replacementManifest = manifest
}

export default {
  name: 'iris-studio-acceptance',
  description: 'Exercise public Studio creation, repeated pack edits, rejection retention, lifecycle, production creation, and cold vanilla replacement.',
  async run(context) {
    const { phase, cycles } = parsePhase(context.options.command)
    const fixture = await setup(context, { prepare: phase === 'prepare' })
    if (phase === 'prepare') return context.step('Create and prepare a disposable Studio pack through its public command', () => prepare(context, fixture))
    if (phase === 'studio') return studio(context, fixture, cycles)
    if (phase === 'world' || phase === 'world-check') return context.step('Verify a production world through public commands and terrain', () => production(context, fixture, phase === 'world'))
    if (phase === 'replace-stage' || phase === 'replace-check') return context.step(phase, () => replacement(context, fixture, phase === 'replace-check'))
    const tools = await import('./studio-tools.mjs')
    return tools.runTools(context, fixture, phase)
  }
}

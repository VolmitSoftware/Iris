import { readFile, writeFile, realpath, stat, open } from 'node:fs/promises'
import path from 'node:path'

export const packKey = 'qa-bot-studio'
export const worldKey = 'qa-bot-world'
export const seed = 78264193

export function parsePhase(value = 'studio:3') {
  const match = /^(prepare|studio|object|jigsaw|world|world-check|replace-stage|replace-check)(?::([1-9]\d?))?$/.exec(value)
  if (!match || (match[2] && match[1] !== 'studio')) throw new Error(`Invalid Iris acceptance phase: ${value}`)
  const cycles = Number(match[2] ?? 3)
  if (cycles > 20) throw new Error('Studio cycles must be between 1 and 20')
  return { phase: match[1], cycles }
}

export async function exists(file) {
  try { await stat(file); return true } catch (error) {
    if (error.code === 'ENOENT') return false
    throw error
  }
}

export async function writeJson(file, value) {
  await writeFile(file, `${JSON.stringify(value, null, 2)}\n`)
}

export async function readJson(file) {
  return JSON.parse(await readFile(file, 'utf8'))
}

export function ownedPath(root, ...segments) {
  const target = path.resolve(root, ...segments)
  if (!target.startsWith(`${path.resolve(root)}${path.sep}`)) throw new Error('Fixture path escapes the isolated instance')
  return target
}

export async function setup(context, { prepare = false } = {}) {
  const directory = context.server.directory
  context.expect(typeof directory === 'string' && path.isAbsolute(directory), 'The harness must supply an absolute instance directory')
  const root = await realpath(directory)
  const source = await readFile(path.join(root, '.server-source'), 'utf8')
  context.expect(/^isolated=true\s*$/m.test(source), 'Iris acceptance requires an isolated instance')
  const properties = await readFile(path.join(root, 'server.properties'), 'utf8')
  context.expect(/^server-ip=127\.0\.0\.1\s*$/m.test(properties), 'Iris acceptance requires a loopback instance')
  const level = /^level-name=(.+)$/m.exec(properties)?.[1].trim() ?? 'world'
  const levelRoot = ownedPath(root, level)
  const irisRoot = await realpath(path.join(root, 'plugins', 'Iris'))
  context.expect(irisRoot.startsWith(`${root}${path.sep}`), 'Iris data must belong to this instance')
  const packs = await realpath(path.join(irisRoot, 'packs'))
  context.expect(packs.startsWith(`${root}${path.sep}`), 'Shared Iris packs are not a test target')
  const marker = path.join(root, '.multiplexor-iris-acceptance.json')
  const state = prepare ? null : await readJson(marker)
  if (state) {
    context.expect(state.schemaVersion === 1 && state.instance === root && state.pack === packKey,
      'The prepared fixture does not belong to this instance')
  }
  const pack = path.join(packs, packKey)
  if (state) context.expect((await realpath(pack)).startsWith(`${root}${path.sep}`), 'The owned fixture pack was redirected outside the instance')
  const worldRoot = key => ownedPath(levelRoot, 'dimensions', ...key.split(':'))
  const manifestPath = key => path.join(worldRoot(key), 'iris', 'generation', 'manifest.json')
  await context.waitUntil(async () => (await context.observe()).players.some(entry => entry.username === context.bot.username),
    { timeoutMs: 15000, label: 'observer registration for the scenario player' })
  const currentWorld = async () => {
    const observation = await context.observe()
    const player = observation.players.find(entry => entry.username === context.bot.username)
    context.expect(player, 'Observer does not contain the scenario player')
    const world = observation.worlds.find(entry => entry.id === player.world)
    context.expect(world, 'Observer does not contain the player world')
    return world
  }
  const wait = (predicate, label, timeoutMs = 30000) => context.waitUntil(predicate, { label, timeoutMs, intervalMs: 100 })
  const arrival = async (trigger, predicate, label) => {
    const previous = await context.observe()
    const startedAt = Date.now()
    let refusal
    const messages = message => {
      if (/Cannot convert |Unknown parameter|Missing required|Jigsaw project was not created|Studio open failed|restart before opening|Could not resolve the requested Iris pack/.test(message)) refusal = message
    }
    context.bot.on('messagestr', messages)
    try {
      await trigger()
      const world = await wait(async () => {
        context.expect(!refusal, `Public Iris command refused: ${refusal}`)
        const observation = await context.observe()
        context.expect(observation.processId === previous.processId, 'Server restarted during Studio arrival')
        if (Date.parse(observation.observedAt) <= startedAt) return false
        const current = await currentWorld()
        return predicate(current) && current.loadedChunks > 0 ? current : false
      }, label, 180000)
      await wait(() => context.bot.blockAt(context.bot.entity.position) !== null, 'Destination chunk did not reach the bot')
      context.report.iris.worldVisits.push({ id: world.id, name: world.name, observedAt: (await context.observe()).observedAt })
      return world
    } finally { context.bot.removeListener('messagestr', messages) }
  }
  context.report.iris = { phase: context.options.command ?? 'studio:3', pack: packKey, worldVisits: [], columns: [], activations: [] }
  return { root, level, levelRoot, irisRoot, pack, marker, state, worldRoot, manifestPath, currentWorld, wait, arrival }
}

export async function openStudio(context, fixture, command = `/iris studio open ${packKey} seed=${seed}`) {
  const before = new Set((await context.observe()).worlds.map(world => world.id))
  const world = await fixture.arrival(() => context.command(command),
    candidate => !before.has(candidate.id) && /iris-[0-9a-f-]{36}$/.test(candidate.name), 'The public Studio command did not create and enter a new transient world')
  const name = /iris-[0-9a-f-]{36}$/.exec(world.name)[0]
  return { ...world, key: `iris:${name}` }
}

export async function closeStudio(context, fixture, world, jigsaw = false) {
  await context.command(jigsaw ? '/iris jigsaw close' : '/iris studio close',
    jigsaw ? /^Jigsaw Studio closed\.$/ : /^Studio closed\.$/, 180000)
  await fixture.wait(async () => !(await context.observe()).worlds.some(entry => entry.id === world.id), 'Studio world remained loaded after close', 180000)
  await fixture.wait(async () => !(await exists(fixture.worldRoot(world.key))), 'Studio world directory survived cleanup', 180000)
  context.expect((await fixture.currentWorld()).id !== world.id, 'Studio close did not evacuate the bot')
}

export async function sampleTerrain(context, fixture, x, material, expectedHeight = 96) {
  await context.command(`/minecraft:tp @s ${x + 0.5} ${expectedHeight + 5} 0.5`, /Teleported/i, 30000)
  await fixture.wait(() => Math.abs(context.bot.entity.position.x - x - 0.5) < 1, 'Terrain sample teleport did not arrive')
  const samples = []
  for (const offset of [0, 1, 2, 3]) {
    const target = context.bot.entity.position.clone().set(x + offset, expectedHeight, 0)
    const block = await fixture.wait(() => context.bot.blockAt(target), 'Terrain column did not reach the bot', 60000)
    const above = await fixture.wait(() => context.bot.blockAt(target.offset(0, 1, 0)), 'Terrain headroom did not reach the bot')
    context.expect(block.name === material && above.name === 'air', 'Generated column differs from the fixture',
      { x: x + offset, y: expectedHeight, expected: material, actual: block.name, above: above.name })
    samples.push({ x: x + offset, y: expectedHeight, block: block.name, above: above.name })
  }
  context.report.iris.columns.push({ world: (await fixture.currentWorld()).id, samples })
}

export async function logPosition(fixture) {
  return (await stat(path.join(fixture.root, 'logs', 'latest.log'))).size
}

export async function logSince(fixture, offset) {
  const handle = await open(path.join(fixture.root, 'logs', 'latest.log'), 'r')
  try {
    const size = (await handle.stat()).size
    const start = Math.max(offset, size - 1024 * 1024)
    const buffer = Buffer.alloc(Math.max(0, size - start))
    await handle.read(buffer, 0, buffer.length, start)
    return buffer.toString('utf8')
  } finally { await handle.close() }
}

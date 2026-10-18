import { readFile, realpath } from 'node:fs/promises'
import path from 'node:path'

const nonGround = new Set(['air', 'cave_air', 'void_air', 'water', 'lava', 'nether_wart_block', 'warped_wart_block'])
const chunkOffsets = [-56, -40, -24, -8, 8, 24, 40, 56]

function isWood(block) {
  return block !== null && /_(wood|hyphae|log|stem)$/.test(block.name)
}

function isGround(block) {
  return block !== null && block.boundingBox === 'block' && !nonGround.has(block.name)
    && !block.name.endsWith('_leaves') && !isWood(block)
}

function positionKey(position) {
  return `${position.x},${position.y},${position.z}`
}

function groundSpread(bot, base) {
  const heights = []
  for (const x of [-3, 0, 3]) {
    for (const z of [-3, 0, 3]) {
      for (let y = base.y + 6; y >= base.y - 8; y--) {
        if (!isGround(bot.blockAt(base.offset(x, y - base.y, z)))) continue
        heights.push(y)
        break
      }
    }
  }
  return heights.length === 9 ? Math.max(...heights) - Math.min(...heights) : null
}

function inspectTrees(context) {
  const bot = context.bot
  const center = bot.entity.position.floored()
  const woodIds = Object.values(bot.registry.blocksByName).filter(isWood).map(block => block.id)
  const positions = bot.findBlocks({ matching: woodIds, point: center, maxDistance: 64, count: 20000 })
  const columns = new Map()
  for (const position of positions) {
    const key = `${position.x},${position.z}`
    if (!columns.has(key)) columns.set(key, [])
    columns.get(key).push(position)
  }
  const trees = []
  for (const column of columns.values()) {
    column.sort((first, second) => first.y - second.y)
    const base = column[0]
    const height = column[column.length - 1].y - base.y + 1
    if (column.length < 8 || height < 8 || column.length / height < 0.65) continue
    if (column.some(position => position.distanceTo(center) >= 60)) continue
    if (![0, 1, 2, 3].every(y => isWood(bot.blockAt(base.offset(0, y, 0))))) continue
    const upperGaps = []
    for (let y = base.y; y < base.y + height; y++) {
      const block = bot.blockAt(base.offset(0, y - base.y, 0))
      if (block === null || !isWood(block)) upperGaps.push({ y, block: block?.name ?? null })
    }
    const grounded = isGround(bot.blockAt(base.offset(0, -1, 0)))
    const spread = groundSpread(bot, base)
    trees.push({ base: { x: base.x, y: base.y, z: base.z }, woodBlocks: column.length,
      height, grounded, terrainSpread: spread, upperGaps })
  }
  return trees
}

export default {
  name: 'iris-tree-grounding',
  description: 'Verify grounded spindle bases, continuous overlapping trunks, and paired pack geometry.',
  async run(context) {
    const directory = await realpath(context.server.directory)
    const source = await readFile(path.join(directory, '.server-source'), 'utf8')
    context.expect(/^isolated=true\s*$/m.test(source), 'Tree generation requires an isolated instance')
    const packs = await realpath(path.join(directory, 'plugins', 'Iris', 'packs'))
    context.expect(packs.startsWith(`${directory}${path.sep}`), 'Tree tests require local pack copies')
    context.report.treeWorlds = []
    context.report.treeGrounding = []
    context.report.treeContinuity = []
    for (const pack of ['overworld', 'underworld']) {
      const dimension = JSON.parse(await readFile(path.join(packs, pack, 'dimensions', `${pack}.json`), 'utf8'))
      context.expect(dimension.focus === 'estranged/spindlewood', 'Pack must focus on spindlewood', { pack })
      await context.step(`create ${pack} spindle world`, async () => {
        const world = `tree_${pack}_${Date.now().toString(36)}`
        const response = await context.command(`/iris create name=${world} type=${pack} seed=78264193`,
          /Successfully created your world|Failed to create/, 240000)
        context.expect(response.includes('Successfully created your world'), response)
        context.report.treeWorlds.push({ pack, name: world, seed: 78264193, biome: dimension.focus })
        if (context.bot.game.gameMode !== 'spectator') {
          await context.command('/minecraft:gamemode spectator', /spectator/i, 30000)
        }
        await context.waitUntil(() => context.bot.game.gameMode === 'spectator',
          { label: 'Spectator mode for terrain inspection', timeoutMs: 30000, intervalMs: 100 })
      })
      await context.step(`${pack} trees connect to flat and sloped terrain`, async () => {
        const origin = context.bot.entity.position.floored()
        const inspected = new Map()
        for (const offset of [0, 80, 160]) {
          await context.command(`/minecraft:tp @s ${origin.x + offset + 0.5} ${origin.y + 12} ${origin.z + 0.5}`,
            /Teleported/i, 30000)
          await context.waitUntil(() => {
            const center = context.bot.entity.position.floored()
            if (Math.abs(center.x - origin.x - offset) > 1) return false
            return chunkOffsets.every(x => chunkOffsets.every(z => context.bot.blockAt(center.offset(x, 0, z)) !== null))
          }, { label: 'Complete terrain neighborhood', timeoutMs: 90000, intervalMs: 250 })
          const trees = inspectTrees(context)
          for (const tree of trees) {
            context.expect(tree.grounded, 'Generated spindle trunk base has no terrain connection', { pack, ...tree })
            inspected.set(positionKey(tree.base), tree)
          }
          if (inspected.size >= 8 && [...inspected.values()].some(tree => tree.terrainSpread >= 2)) break
        }
        const trees = [...inspected.values()]
        const sloped = trees.filter(tree => tree.terrainSpread >= 2)
        context.expect(trees.length >= 8, 'Expected at least eight generated spindle trunk bases', { pack, trees })
        context.expect(sloped.length > 0, 'Expected a grounded tree on uneven terrain', { pack, trees })
        context.report.treeGrounding.push({ pack, trees: trees.length, sloped: sloped.length, samples: trees })
      })
      await context.step(`${pack} overlapping canopy preserves the seeded main trunk`, async () => {
        await context.command('/minecraft:tp @s 0.5 88 0.5', /Teleported/i, 30000)
        await context.waitUntil(() => {
          const center = context.bot.entity.position.floored()
          return Math.abs(center.x) < 2 && Math.abs(center.z) < 2
            && context.bot.blockAt(center.offset(-7 - center.x, 69 - center.y, 6 - center.z)) !== null
        }, { label: 'Seeded trunk neighborhood', timeoutMs: 90000, intervalMs: 250 })
        const center = context.bot.entity.position.floored()
        const blocks = []
        for (let y = 69; y <= 83; y++) {
          const block = context.bot.blockAt(center.offset(-7 - center.x, y - center.y, 6 - center.z))
          context.expect(isWood(block), 'Overlapping canopy interrupted the seeded main trunk',
            { pack, x: -7, y, z: 6, block: block?.name ?? null })
          blocks.push({ y, block: block.name })
        }
        context.report.treeContinuity.push({ pack, x: -7, z: 6, blocks })
      })
    }
    await context.step('paired packs retain matching sampled tree geometry', async () => {
      const geometry = context.report.treeGrounding.map(result => result.samples.map(tree => ({
        base: tree.base, woodBlocks: tree.woodBlocks, height: tree.height, terrainSpread: tree.terrainSpread
      })).sort((first, second) => positionKey(first.base).localeCompare(positionKey(second.base))))
      context.expect(JSON.stringify(geometry[0]) === JSON.stringify(geometry[1]),
        'Paired spindle packs produced different sampled geometry', { geometry })
    })
  }
}

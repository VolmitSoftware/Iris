import { readFile, writeFile } from 'node:fs/promises'
import path from 'node:path'

const simplifyNbt = value => {
  if (Array.isArray(value)) return value.map(simplifyNbt)
  if (value === null || typeof value !== 'object') return value
  if ('type' in value && 'value' in value) return simplifyNbt(value.value)
  return Object.fromEntries(Object.entries(value).map(([key, entry]) => [key, simplifyNbt(entry)]))
}

const completeClientTicks = context => {
  const client = context.bot._client
  context.expect(context.bot.supportFeature('sendsClientTickEndPacket'), 'Fixture client supports tick delimiters')
  let tickTimer
  const finishTick = () => {
    clearTimeout(tickTimer)
    client.write('tick_end', {})
    tickTimer = setTimeout(finishTick, 50)
  }
  context.bot.on('move', finishTick)
  finishTick()
  context.signal.addEventListener('abort', () => {
    clearTimeout(tickTimer)
    context.bot.removeListener('move', finishTick)
  }, { once: true })
}

export default {
  name: 'iris-treasure-maps',
  description: 'Verify exploration-map targets, bounded misses, retained loot, and normal/copper storage on Iris.',
  async run(context) {
    completeClientTicks(context)
    context.bot.physicsEnabled = false
    context.bot.clearControlStates()
    context.bot.entity.velocity.set(0, 0, 0)
    const source = await readFile(path.join(context.server.directory, '.server-source'), 'utf8')
    context.expect(/^isolated=true\s*$/m.test(source), 'Treasure map fixtures require an isolated instance')
    const command = async (operation, expected, timeoutMs = 30000) => {
      const token = expected.split(' ')[0]
      const response = await context.command(`/irislifecycle ${operation}`,
        new RegExp(`${token}|NATIVE_FAILED|TREASURE_.*_FAILED`), timeoutMs)
      context.expect(response.includes(expected), response)
      return response
    }
    const bounded = (label, action) => context.waitUntil(async () => {
      await action()
      return true
    }, { label, timeoutMs: 10000 })
    let lastActivation = 0
    const openChest = async material => {
      const position = context.bot.entity.position.clone().set(2, 161, 2)
      await context.waitUntil(() => context.bot.blockAt(position)?.name === material, {
        label: `${material} reached the client`, timeoutMs: 10000
      })
      const delay = 150 - (performance.now() - lastActivation)
      if (delay > 0) await context.sleep(delay)
      const opened = context.waitForEvent('windowOpen', undefined, 30000)
      await bounded(`Activate ${material}`, () => context.bot.activateBlock(context.bot.blockAt(position)))
      lastActivation = performance.now()
      const [window] = await opened
      return window
    }
    const chestItems = window => window.slots.slice(0, window.inventoryStart).filter(Boolean)
    const mapId = item => item.componentMap?.get('map_id')?.data
    let missItem
    await context.step('create and enter the Iris treasure map world', async () => {
      const response = await command('treasure-map-create', 'TREASURE_MAP_READY world=iris:treasure_map_probe', 180000)
      const version = response.match(/version=(\S+)/)?.[1]
      context.expect(/^26\.[23](?:[.-]|$)/.test(version ?? ''), 'Fixture requires its matching 26.2 or 26.3 data pack', { version })
      missItem = version.startsWith('26.2') ? 'map' : 'filled_map'
      context.report.treasurePlatform = { version, folia: response.includes('folia=true'), missItem }
      await context.waitUntil(() => context.bot.entity.position.distanceTo(
        context.bot.entity.position.clone().set(0.5, 161, 0.5)
      ) < 2, { label: 'Fixture teleport reached the client', timeoutMs: 30000 })
    })
    await context.step('confirm the missing target exhausts the placement search budget', async () => {
      await command('treasure-map-limit', 'TREASURE_MAP_LIMIT status=SEARCH_LIMIT_REACHED', 120000)
    })
    for (const material of ['chest', 'copper_chest']) {
      for (const destination of ['blocked', 'disabled', 'found', 'mixed']) {
        await context.step(`open ${material} with ${destination} map target and retain all loot`, async () => {
          const evidence = { destination, material, phase: 'prepare' }
          context.report.treasureMaps ??= []
          context.report.treasureMaps.push(evidence)
          const located = destination === 'found' || destination === 'mixed'
          const mapName = located ? 'filled_map' : missItem
          await command(`treasure-map-chest ${destination} ${material.toUpperCase()}`,
            `TREASURE_MAP_CHEST destination=${destination} material=${material.toUpperCase()}`)
          evidence.phase = 'open'
          const window = await openChest(material)
          evidence.windowType = window.type
          evidence.inventoryStart = window.inventoryStart
          let targetId
          let diamondType
          let diamondsBefore
          let marker
          let verified
          try {
            await context.waitUntil(() => {
              const items = chestItems(window)
              return items.some(item => item.name === mapName) && items.some(item => item.name === 'diamond')
            }, { label: `${destination} chest contains map and diamond`, timeoutMs: 30000 })
            const items = chestItems(window)
            const map = items.find(item => item.name === mapName)
            const diamond = items.find(item => item.name === 'diamond')
            targetId = mapId(map)
            context.expect(items.length === 2 && map.count === 1 && diamond.count === 1,
              'Loot table must produce exactly one map and one diamond')
            context.expect((targetId !== undefined) === located, 'Only reachable destinations receive map data', {
              destination, components: map.components
            })
            const response = await command(`treasure-map-inspect ${destination}`,
              `TREASURE_MAP_INSPECT destination=${destination} diamonds=1 located=${located}`, 120000)
            verified = Object.fromEntries([...response.matchAll(/(mapId|targetX|targetZ|centerX|centerZ)=(-?\d+)/g)]
              .map(([, key, value]) => [key, Number(value)]))
            const decorations = simplifyNbt(map.componentMap?.get('map_decorations')?.data)
            marker = decorations?.['+']
            if (located) {
              context.expect(targetId === verified.mapId, 'Client and saved map IDs agree', { targetId, verified })
              context.expect(marker?.type === 'minecraft:red_x'
                && marker.x === verified.targetX && marker.z === verified.targetZ,
              'Map marker points to the verified pyramid', { marker, verified, components: map.components })
            } else {
              context.expect(marker === undefined, 'Missing destinations must not invent a map marker', { decorations })
            }
            diamondType = diamond.type
            diamondsBefore = context.bot.inventory.count(diamondType)
            evidence.phase = 'withdraw'
            evidence.diamondSlot = diamond.slot
            await bounded(`Withdraw diamond from ${material} ${destination}`,
              () => context.bot.clickWindow(diamond.slot, 0, 1))
            evidence.phase = 'withdrawn'
            await context.waitUntil(() => chestItems(window).every(item => item.name !== 'diamond'), {
              label: 'Diamond transferred out of the chest', timeoutMs: 10000
            })
          } finally {
            context.bot.closeWindow(window)
          }
          evidence.phase = 'reopen'
          const reopened = await openChest(material)
          try {
            await context.waitUntil(() => chestItems(reopened).some(item => item.name === mapName), {
              label: 'Saved map remains in the reopened chest', timeoutMs: 10000
            })
            const retained = chestItems(reopened)
            context.expect(retained.length === 1 && retained[0].name === mapName && retained[0].count === 1,
              'Reopening must not regenerate chest loot')
            context.expect(mapId(retained[0]) === targetId, 'Reopening must retain the same map data')
            context.expect(JSON.stringify(simplifyNbt(retained[0].componentMap?.get('map_decorations')?.data)?.['+'])
              === JSON.stringify(marker), 'Reopening must retain the same destination marker')
            await context.waitUntil(() => context.bot.inventory.count(diamondType) === diamondsBefore + 1, {
              label: 'Chest awards exactly one diamond', timeoutMs: 10000
            })
            Object.assign(evidence, { phase: 'complete', mapItem: mapName,
              mapId: targetId ?? null, marker: marker ?? null, verified, reopened: true })
          } finally {
            context.bot.closeWindow(reopened)
          }
        })
      }
    }
    await context.step('verify storage variants, insertion rollback, and locate event results', async () => {
      await command('treasure-extra-check', 'TREASURE_EXTRA_CHECK variants=9 rollback=true locateEvents=true', 120000)
    })
    await context.step('verify plugin loot runs on its owning region', async () => {
      await command('treasure-region-check', 'TREASURE_REGION_LOOT copper=8 normal=1 events=9 owned=true', 120000)
    })
    await context.step('verify native loot table reload support', async () => {
      await command('treasure-reload-before', 'TREASURE_RELOAD material=DIAMOND')
      if (context.report.treasurePlatform.folia) {
        const response = await context.command('/minecraft:reload', /Unknown or incomplete command|Reloading/i, 10000)
        context.expect(response.includes('Unknown or incomplete command'),
          'Folia fixture expects its unavailable data pack reload command', { response })
        context.report.treasureReload = { status: 'unavailable', before: 'diamond', evidence: response }
        return
      }
      const properties = await readFile(path.join(context.server.directory, 'server.properties'), 'utf8')
      const levelName = properties.match(/^level-name=(.+)$/m)?.[1].trim() ?? 'world'
      const tablePath = path.join(context.server.directory, levelName, 'datapacks', 'iris_treasure',
        'data', 'iris_qa', 'loot_table', 'reload.json')
      const original = await readFile(tablePath, 'utf8')
      const replacement = JSON.parse(original)
      context.expect(replacement.pools[0].entries[0].name === 'minecraft:diamond',
        'Reload fixture must start with its diamond table')
      const revision = async () => {
        const response = await command('treasure-resources', 'TREASURE_RESOURCES revision=')
        return Number(response.match(/revision=(\d+)/)?.[1])
      }
      const reload = async () => {
        const before = await revision()
        await context.command('/minecraft:reload', /Reloading/i, 30000)
        await context.waitUntil(async () => await revision() > before, {
          label: 'Server resource reload completed', timeoutMs: 120000, intervalMs: 500
        })
      }
      replacement.pools[0].entries[0].name = 'minecraft:emerald'
      try {
        await writeFile(tablePath, `${JSON.stringify(replacement, null, 2)}\n`)
        await reload()
        await command('treasure-reload-after', 'TREASURE_RELOAD material=EMERALD')
        context.report.treasureReload = { status: 'passed', before: 'diamond', after: 'emerald' }
      } finally {
        await writeFile(tablePath, original)
        if (!context.signal.aborted) {
          await reload()
          await command('treasure-reload-before', 'TREASURE_RELOAD material=DIAMOND')
        }
      }
    })
  }
}

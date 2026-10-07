import { readFile } from 'node:fs/promises'
import path from 'node:path'

const worldName = 'lifecycle_probe'
const worldKey = `iris:${worldName}`
const storageName = `world_iris_${worldName}`

export default {
  name: 'iris-world-lifecycle',
  description: 'Create, enter, evacuate, reload, and remove an Iris world through the player commands.',
  async run(context) {
    const source = await readFile(path.join(context.server.directory, '.server-source'), 'utf8')
    context.expect(/^isolated=true\s*$/m.test(source), 'World lifecycle requires an isolated instance')
    const wait = (predicate, label, timeoutMs = 180000) => context.waitUntil(predicate, {
      label, timeoutMs, intervalMs: 250
    })
    const playerWorld = async () => {
      const observation = await context.observe()
      const player = observation.players.find(entry => entry.username === context.bot.username)
      return observation.worlds.find(entry => entry.id === player?.world)?.name
    }
    const status = async () => {
      const response = await context.command('/irislifecycle lifecycle-status', /IRIS_LIFECYCLE |NATIVE_FAILED/, 10000)
      context.expect(!response.includes('NATIVE_FAILED'), response)
      return response
    }
    const settledStatus = async expected => {
      const response = await status()
      return response.includes(expected) && response.includes('idle=true')
    }
    const assertEvacuated = async label => {
      let destination
      await wait(async () => {
        destination = await playerWorld()
        return Boolean(destination && destination !== storageName)
      }, label, 30000)
      context.expect(destination && destination !== storageName, label, { destination })
    }
    const assertSafeEntry = async () => {
      await wait(async () => (await playerWorld()) === storageName, 'Player entered the lifecycle world')
      await wait(() => {
        const feet = context.bot.entity.position.floored()
        const floor = context.bot.blockAt(feet.offset(0, -1, 0))
        const body = context.bot.blockAt(feet)
        const head = context.bot.blockAt(feet.offset(0, 1, 0))
        return context.bot.entity.onGround && floor?.name === 'lime_wool'
          && body?.boundingBox === 'empty' && head?.boundingBox === 'empty'
      }, 'Client sees solid terrain below unobstructed player entry', 30000)
      const response = await context.command('/irislifecycle lifecycle-entry', /IRIS_LIFECYCLE_ENTRY|NATIVE_FAILED/, 10000)
      context.expect(response.includes(`IRIS_LIFECYCLE_ENTRY world=${worldKey} seed=78264193 floor=lime_wool safe=true`), response)
    }
    await context.step('confirm disposable world is absent', async () => {
      const response = await status()
      context.expect(response.includes('loaded=false registered=false configured=false disk=false quarantines=0 cleanup=deleted idle=true'), response)
      await context.command('/minecraft:gamemode survival @s')
      await wait(() => context.bot.game.gameMode === 'survival', 'Survival mode', 10000)
    })
    await context.step('create through Iris and automatically enter safe terrain', async () => {
      await context.command(`/iris create name=${worldName} type=native-terrain seed=78264193`)
      await wait(async () => await settledStatus('loaded=true registered=true configured=true disk=true'), 'Created world registration and storage')
      await assertSafeEntry()
    })
    await context.step('unload while the player is inside and verify evacuation', async () => {
      context.expect((await playerWorld()) === storageName, 'Player must be inside the world before unload')
      await context.command(`/iris unloadWorld ${worldKey}`)
      await wait(async () => await settledStatus('loaded=false registered=true configured=true disk=true'), 'World unloaded with storage preserved')
      await assertEvacuated('Unload evacuated the player to a loaded world')
    })
    await context.step('reload the saved world and enter it again', async () => {
      await context.command(`/iris loadWorld ${worldName}`)
      await wait(async () => await settledStatus('loaded=true registered=true configured=true disk=true'), 'World reloaded')
      await context.command(`/iris tp ${worldKey}`)
      await assertSafeEntry()
    })
    await context.step('remove the occupied world and verify disk cleanup', async () => {
      await context.command(`/iris remove world=${worldName} delete=true`)
      let finalStatus
      await wait(async () => {
        finalStatus = await status()
        return finalStatus.includes('loaded=false registered=false configured=false disk=false')
          && /cleanup=(deleted|queued)\b/.test(finalStatus) && finalStatus.includes('idle=true')
      }, 'Removal detached registrations and deleted or queued quarantine')
      await assertEvacuated('Removal evacuated the player to a loaded world')
      context.report.worldLifecycle = { world: worldKey, cleanup: /cleanup=(deleted|queued)\b/.exec(finalStatus)[1] }
    })
    if (process.env.IRIS_QA_PACK_URL) {
      await context.step('download and overwrite the fixture pack through Iris', async () => {
        const response = await context.command(
          `/iris download link=${process.env.IRIS_QA_PACK_URL} overwrite=true`,
          /Iris pack 'native-terrain' installed|Iris pack download failed\./,
          120000
        )
        context.expect(response.includes("Iris pack 'native-terrain' installed"), response)
      })
    }

  }
}

import { readFile } from 'node:fs/promises'
import path from 'node:path'
import { jsonFiles } from './studio-tools.mjs'
import { packKey, seed, setup, openStudio, closeStudio, exists, writeJson } from './studio-support.mjs'

const structure = 'acceptance/bot-graph'

async function playerWorld(context, username) {
  const observation = await context.observe()
  const player = observation.players.find(entry => entry.username === username)
  return observation.worlds.find(entry => entry.id === player?.world)
}

export default {
  name: 'iris-editor-ownership',
  description: 'Verify concurrent ordinary visitors, competing administrator rejection, and owner reconnect across two explicit phases.',
  async run(context) {
    const phase = context.options.command ?? 'exercise'
    context.expect(['exercise', 'reconnect'].includes(phase), 'Choose exercise or reconnect')
    const fixture = await setup(context)
    context.expect(fixture.state.studioCompleted, 'Complete Studio acceptance first')
    context.expect(await exists(path.join(fixture.pack, 'structures', `${structure}.json`)), 'Complete the Jigsaw authoring phase first')
    let active
    let jigsaw = false
    let editor
    let promoted = false
    let retainForReconnect = false
    try {
      if (phase === 'reconnect') {
        const owned = fixture.state.ownership
        context.expect(owned && owned.username === context.bot.username, 'Reconnect with the same primary owner after exercise')
        context.expect(owned.processId === (await context.observe()).processId, 'Reconnect requires the same running server')
        await fixture.wait(async () => (await fixture.currentWorld()).id === owned.id, 'Owner did not reconnect to the retained workspace')
        active = owned
        jigsaw = true
        await context.command('/iris jigsaw status', /mode=SPATIAL.*variants=7/, 10000)
        await context.command('/iris jigsaw goto workcell/spatial', /Selected Jigsaw Studio workcell/, 10000)
        await closeStudio(context, fixture, active, true)
        active = undefined
        delete fixture.state.ownership
        await writeJson(fixture.marker, fixture.state)
        context.report.iris.ownership = { reconnected: true, retainedWorld: owned.id, closed: true }
        return
      }
      context.expect(!fixture.state.ownership, 'Finish the pending reconnect phase before another exercise')
      const visitor = await context.connectActor(`IrisView${context.server.port}`)
      editor = await context.connectActor(`IrisEdit${context.server.port}`)
      await context.step('Walk two ordinary visitors concurrently in the same Studio world', async () => {
        active = await openStudio(context, fixture)
        for (const [actor, centerX] of [[visitor, -8.5], [editor, 8.5]]) {
          await context.command(`/minecraft:tp ${actor.bot.username} ${context.bot.username}`)
          await context.command(`/minecraft:gamemode survival ${actor.bot.username}`)
          await context.command(`/minecraft:tp ${actor.bot.username} ${centerX + 4} 97 0.5`)
        }
        await fixture.wait(async () => (await playerWorld(context, visitor.bot.username))?.id === active.id
          && (await playerWorld(context, editor.bot.username))?.id === active.id, 'Both visitors did not enter the same Studio world')
        const circuits = await Promise.all([
          visitor.actions.walkCircle({ center: { x: -8.5, y: 97, z: 0.5 }, radius: 4, laps: 1 }),
          editor.actions.walkCircle({ center: { x: 8.5, y: 97, z: 0.5 }, radius: 4, laps: 1 })
        ])
        context.report.iris.visitors = { usernames: [visitor.bot.username, editor.bot.username], world: active.id, circuits }
        await closeStudio(context, fixture, active)
        const closedId = active.id
        active = undefined
        await fixture.wait(async () => (await playerWorld(context, visitor.bot.username))?.id !== closedId
          && (await playerWorld(context, editor.bot.username))?.id !== closedId, 'Studio close did not evacuate every visitor')
      })
      await context.step('Reject a competing administrator while retaining the owner workspace', async () => {
        await context.command(`/minecraft:op ${editor.bot.username}`, /operator/i, 10000)
        promoted = true
        context.report.iris.editorRole = 'Temporary administrator, explicitly promoted after ordinary-visitor coverage and deopped in cleanup'
        active = await openStudio(context, fixture, `/iris jigsaw open ${packKey} ${structure} seed=${seed}`)
        jigsaw = true
        const resourceFiles = await jsonFiles(path.join(fixture.pack, 'jigsaw-pieces'))
        const variants = await Promise.all(resourceFiles.map(async file => [file, await readFile(file, 'utf8')]))
        const structureFile = path.join(fixture.pack, 'structures', `${structure}.json`)
        const original = await readFile(structureFile, 'utf8')
        await editor.command(`/iris jigsaw open ${packKey} ${structure} seed=${seed}`, /Jigsaw Studio is already owned by another player session\./, 10000)
        context.expect((await fixture.currentWorld()).id === active.id, 'Competing open displaced the owner')
        await context.command(`/minecraft:tp ${editor.bot.username} ${context.bot.username}`)
        await fixture.wait(async () => (await playerWorld(context, editor.bot.username))?.id === active.id, 'Competing editor did not arrive')
        await editor.command('/iris jigsaw variant label UnauthorizedChange', /owned by another player session/i, 10000)
        await editor.command('/iris jigsaw close', /owned by another player session/i, 10000)
        context.expect(await readFile(structureFile, 'utf8') === original, 'Rejected editor changed the structure')
        for (const [file, bytes] of variants) context.expect(await readFile(file, 'utf8') === bytes, 'Rejected editor changed a variant')
        await context.command('/iris jigsaw status', /mode=SPATIAL.*variants=7/, 10000)
        context.expect((await fixture.currentWorld()).id === active.id, 'Rejected close displaced the owner')
        await context.command(`/minecraft:deop ${editor.bot.username}`, /operator/i, 10000)
        promoted = false
        fixture.state.ownership = { id: active.id, key: active.key, name: active.name, username: context.bot.username,
          processId: (await context.observe()).processId }
        await writeJson(fixture.marker, fixture.state)
        context.report.iris.ownership = { competingOpenRejected: true, mutationRejected: true, closeRejected: true, retainedWorld: active.id }
        context.report.iris.next = 'Run this scenario with --command reconnect against the same running server and primary username.'
        retainForReconnect = true
      })
    } finally {
      if (promoted) await context.command(`/minecraft:deop ${editor.bot.username}`, /operator/i, 10000)
      if (active && !retainForReconnect) await closeStudio(context, fixture, active, jigsaw)
    }
  }
}

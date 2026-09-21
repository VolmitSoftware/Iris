import { readFile, readdir } from 'node:fs/promises'
import path from 'node:path'
import { packKey, seed, exists, openStudio, closeStudio } from './studio-support.mjs'

async function objectTools(context, fixture) {
  const objectKey = 'acceptance-marker'
  const objectFile = path.join(fixture.pack, 'objects', `${objectKey}.iob`)
  context.expect(!(await exists(objectFile)), 'Object acceptance requires an unused marker object')
  let active
  try {
    active = await openStudio(context, fixture)
    await context.step('Select and save one real block through the Iris wand commands', async () => {
      await context.command('/minecraft:gamemode creative @s', /creative/i)
      await fixture.wait(() => context.bot.game.gameMode === 'creative', 'Creative mode did not reach the bot')
      context.bot.creative.startFlying()
      await context.command('/minecraft:tp @s 0.5 111 0.5', /Teleported/i)
      await fixture.wait(() => Math.abs(context.bot.entity.position.y - 111) < 0.1, 'Object selection position did not arrive')
      context.bot.creative.startFlying()
      await context.command('/minecraft:setblock 0 110 0 minecraft:diamond_block', /changed/i)
      await fixture.wait(() => context.bot.blockAt(context.bot.entity.position.clone().set(0, 110, 0))?.name === 'diamond_block', 'Selection block did not reach the bot')
      const existing = new Set(context.bot.inventory.items().map(item => item.slot))
      await context.command('/iris object wand')
      const wand = await fixture.wait(() => context.bot.inventory.items().find(item => !existing.has(item.slot)), 'The Iris wand was not received')
      await context.bot.equip(wand, 'hand')
      for (const corner of ['position1', 'position2']) {
        const before = JSON.stringify(context.bot.heldItem)
        await context.command(`/iris object ${corner}`)
        await fixture.wait(() => JSON.stringify(context.bot.heldItem) !== before, `${corner} did not update the wand selection`)
      }
      await context.command(`/iris object save ${objectKey} dimension=${packKey}`, /Successfully object to saved:/, 60000)
      context.expect((await readFile(objectFile)).length > 0, 'Object save produced an empty IOB')
      await context.command(`/iris object analyze ${objectKey}`, /Object Size: 1 \* 1 \* 1/, 10000)
    })
    await context.step('Paste the saved object and undo the actual block change', async () => {
      await context.command('/minecraft:tp @s 16.5 100 0.5', /Teleported/i)
      await fixture.wait(() => Math.abs(context.bot.entity.position.x - 16.5) < 0.1, 'Paste position did not arrive')
      context.bot.creative.startFlying()
      const target = context.bot.entity.position.clone().set(16.5, 96.5, 0.5)
      await fixture.wait(() => context.bot.blockAt(target), 'Paste target chunk did not arrive')
      await context.bot.lookAt(target, true)
      const placed = target.clone().set(16, 97, 0)
      context.expect(context.bot.blockAt(placed)?.name === 'air', 'Paste target was not empty')
      await context.command(`/iris object paste ${objectKey} scale=1`, /Placed/i, 30000)
      await fixture.wait(() => context.bot.blockAt(placed)?.name === 'diamond_block', 'Saved object did not become a real block')
      await context.command('/iris object undo', /Reverted 1.*pastes/i, 30000)
      await fixture.wait(() => context.bot.blockAt(placed)?.name === 'air', 'Undo did not restore the original air block')
      context.report.iris.object = { key: objectKey, saved: true, pasted: true, undone: true }
    })
  } finally {
    if (active) await closeStudio(context, fixture, active)
  }
}

export async function jsonFiles(directory) {
  if (!(await exists(directory))) return []
  const files = []
  for (const entry of await readdir(directory, { withFileTypes: true })) {
    const file = path.join(directory, entry.name)
    if (entry.isDirectory()) files.push(...await jsonFiles(file))
    else if (entry.isFile() && entry.name.endsWith('.json')) files.push(file)
  }
  return files
}

async function jigsawTools(context, fixture) {
  const structure = 'acceptance/bot-graph'
  context.expect(!(await exists(path.join(fixture.pack, 'structures', `${structure}.json`))), 'Jigsaw acceptance requires an unused structure key')
  let active
  try {
    await context.step('Create and enter a Jigsaw project through the public command', async () => {
      active = await openStudio(context, fixture,
        `/iris jigsaw create ${packKey} ${structure} mode=spatial width=3 height=3 depth=3 seed=${seed}`)
      await fixture.wait(() => context.bot.game.gameMode === 'creative', 'Jigsaw Studio did not enter creative mode')
      context.expect(await exists(path.join(fixture.pack, 'structures', `${structure}.json`)), 'Jigsaw creation omitted the structure resource')
      const status = await context.command('/iris jigsaw status', /mode=SPATIAL.*variants=7/, 10000)
      context.report.iris.jigsaw = { structure, createdStatus: status }
      const opened = context.waitForEvent('windowOpen', window => window.inventoryStart === 54, 10000)
      await context.command('/iris jigsaw menu')
      const [window] = await opened
      context.expect(window.slots.slice(0, 54).some(Boolean), 'Jigsaw controls inventory was empty')
      await context.bot.closeWindow(window)
    })
    await context.step('Rename the selected variant and persist its authored metadata', async () => {
      await context.command('/iris jigsaw goto workcell/spatial', /Selected Jigsaw Studio workcell 'workcell\/spatial'\./, 10000)
      const selection = await context.command('/iris jigsaw status', /selected=.*activeVariant=/, 10000)
      const selected = /selected=([^,]+), activeVariant=([^,]+)/.exec(selection)
      context.expect(selected && selected[1] !== 'none' && selected[2] !== 'none', 'Jigsaw did not select an owned variant', { selection })
      const label = 'BotAcceptanceVariant'
      await context.command(`/iris jigsaw variant label ${label}`, /Renamed the variant to 'BotAcceptanceVariant'\./, 60000)
      const files = await jsonFiles(path.join(fixture.pack, 'jigsaw-pieces'))
      const saved = await fixture.wait(async () => {
        for (const file of files) {
          const content = await readFile(file, 'utf8')
          if (content.includes(label)) return { file, content }
        }
        return false
      }, 'Variant rename did not persist through the public authoring command', 60000)
      context.report.iris.jigsaw.variantFile = path.relative(fixture.pack, saved.file)
      context.report.iris.jigsaw.label = label
      await closeStudio(context, fixture, active, true)
      active = undefined
      const bytes = await readFile(saved.file, 'utf8')
      active = await openStudio(context, fixture, `/iris jigsaw open ${packKey} ${structure} seed=${seed}`)
      context.expect(await readFile(saved.file, 'utf8') === bytes, 'Jigsaw reopen rewrote authored variant metadata')
      await context.command('/iris jigsaw status', /mode=SPATIAL.*variants=7/, 10000)
      const ready = await context.command('/iris jigsaw preview assemble seed=1337', /Showing a [1-9]\d*-piece particle preview for 10 seconds\./, 60000)
      context.report.iris.jigsaw.previewResponse = ready
    })
  } finally {
    if (active) await closeStudio(context, fixture, active, true)
  }
}

export async function runTools(context, fixture, phase) {
  context.expect(fixture.state.studioCompleted, 'Complete the Studio acceptance phase before object or Jigsaw authoring')
  if (phase === 'object') return objectTools(context, fixture)
  if (phase === 'jigsaw') return jigsawTools(context, fixture)
  throw new Error(`Unknown Studio tools phase: ${phase}`)
}

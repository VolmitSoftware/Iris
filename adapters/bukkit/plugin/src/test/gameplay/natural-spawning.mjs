export default {
  name: 'iris-natural-spawning',
  description: 'Observe category-limited Iris ambient frogs and cave zombies on Folia.',
  async run(context) {
    const wait = (predicate, label, timeoutMs = 30000) => context.waitUntil(predicate, { label, timeoutMs, intervalMs: 100 })
    const command = async (operation, expected, timeoutMs = 30000) => {
      const response = await context.command(`/irisspawnqa ${operation}`, /SPAWN_QA_[A-Z]+(?:\s|$)/, timeoutMs)
      context.expect(response.includes(expected) && !response.includes('SPAWN_QA_FAILED'), response)
      return response
    }
    const entities = name => Object.values(context.bot.entities).filter(entity => entity.name === name
      && entity.position.x >= 0 && entity.position.x < 16 && entity.position.z >= 0 && entity.position.z < 16)
    await context.step('prepare the synthetic Iris world and dark cave', async () => {
      await command('setup', 'SPAWN_QA_SETUP world=iris:spawn_qa seed=78264193', 180000)
      await wait(() => Math.abs(context.bot.entity.position.y - 67) < 1, 'Fixture world teleport')
    })
    await context.step('fill the passive category through real Iris ambient spawning', async () => {
      await command('frogs', 'SPAWN_QA_FROGS frogs=16 persistent=true removable=true ambient=true', 60000)
      await wait(() => entities('frog').length === 16, 'All sixteen ambient frogs became visible')
      context.report.irisNaturalSpawning = { visibleFrogs: entities('frog').length }
    })
    await context.step('spawn an authored seven-zombie batch with capacity two despite existing frogs', async () => {
      await command('zombies', 'SPAWN_QA_ZOMBIES frogs=16 zombies=2 firstBatch=2 ambient=true', 60000)
      await command('cave', 'SPAWN_QA_VIEW y=-46')
      await wait(() => Math.abs(context.bot.entity.position.y + 46) < 1, 'Underground fixture teleport')
      await wait(() => context.bot.blockAt(context.bot.entity.position.clone().set(8, -47, 8))?.name === 'cave_air', 'Live cave_air block reached the client')
      await wait(() => entities('zombie').length === 2, 'Both ambient cave zombies became visible')
      context.expect(entities('zombie').every(entity => entity.position.y < -42), 'Zombies were not inside the underground chamber')
      context.report.irisNaturalSpawning.visibleZombies = entities('zombie').length
      context.report.irisNaturalSpawning.caveAir = true
    })
    await context.step('repeat fifty ambient attempts without exceeding the hostile capacity', async () => {
      await command('stress', 'SPAWN_QA_STRESS attempts=50 zombies=2', 60000)
      await command('check', 'SPAWN_QA_RESULT frogs=16 zombies=2 firstBatch=2 persistent=true removable=true caveAir=true light=0', 30000)
      context.expect(entities('zombie').length === 2, 'Client observed a hostile capacity overflow')
      context.report.irisNaturalSpawning.saturatedAttempts = 50
      context.report.irisNaturalSpawning.firstHostileBatch = 2
      context.report.irisNaturalSpawning.removeWhenFarAwayAllowed = true
      context.report.irisNaturalSpawning.chunkSaving = true
    })
  }
}

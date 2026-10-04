export default {
  name: 'iris-seeded-entities',
  description: 'Replay initial chunk and marker populations in reversed order, comparing positions and complete equipment data.',
  async run(context) {
    const command = async (operation, expected, timeoutMs) => {
      const response = await context.command(`/irisspawnqa ${operation}`, /SPAWN_QA_[A-Z]+(?:\s|$)/, timeoutMs)
      context.expect(response.includes(expected) && !response.includes('SPAWN_QA_FAILED'), response)
    }
    await context.step('prepare a controlled Iris world', async () => {
      await command('setup', 'SPAWN_QA_SETUP', 180000)
    })
    await context.step('replay initial mobs despite unrelated randomness and live spawn restrictions', async () => {
      await command('seeded', 'SPAWN_QA_SEEDED chunks=2 markers=2 repeats=2 equipment=true attributes=true', 60000)
      await context.waitUntil(() => Object.values(context.bot.entities).some(entity => entity.name === 'zombie' || entity.name === 'husk'), {
        label: 'Generated mobs reached the client', timeoutMs: 10000, intervalMs: 100
      })
      context.report.seededEntities = { chunks: 2, markers: 2, repeats: 2, positions: true, equipmentBytes: true, attributeIds: true }
    })
    await context.step('retry a skipped initial pass and persist completion once', async () => {
      await command('completion', 'SPAWN_QA_COMPLETION retry=true callbacks=1 persisted=true owned=true', 30000)
      context.report.seededEntities.completionRetry = true
    })
  }
}

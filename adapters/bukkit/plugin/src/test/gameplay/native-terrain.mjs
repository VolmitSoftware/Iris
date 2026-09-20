export default {
  name: 'iris-native-terrain',
  description: 'Generate an Iris pack through the shared native adapter and verify terrain, biome, seed, and unload.',
  async run(context) {
    await context.step('create a real Iris terrain world', async () => {
      const response = await context.command('/irislifecycle terrain-create', /NATIVE_TERRAIN seed=|NATIVE_FAILED/, 180000)
      context.expect(response.includes('NATIVE_TERRAIN seed=78264193 columns=16 biome=plains'), response)
    })
    await context.step('read generated terrain again', async () => {
      const response = await context.command('/irislifecycle terrain-check', /NATIVE_TERRAIN seed=|NATIVE_FAILED/, 60000)
      context.expect(response.includes('NATIVE_TERRAIN seed=78264193 columns=16 biome=plains'), response)
    })
    await context.step('save and unload the terrain world', async () => {
      const response = await context.command('/irislifecycle terrain-unload', /NATIVE_TERRAIN_UNLOADED|NATIVE_FAILED/, 90000)
      context.expect(response.includes('NATIVE_TERRAIN_UNLOADED true'), response)
    })
  }
}

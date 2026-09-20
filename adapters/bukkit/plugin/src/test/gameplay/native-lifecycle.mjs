export default {
  name: 'iris-native-lifecycle',
  description: 'Verify native structure metadata, runtime world creation, seeded chunk generation, and asynchronous unload.',
  async run(context) {
    await context.step('native world capability is available', async () => {
      await context.command('/irislifecycle status', /NATIVE_STATUS available=true loaded=false/, 10000)
    })
    await context.step('read vanilla templates, village pools, and connector metadata', async () => {
      const response = await context.command('/irislifecycle structures', /NATIVE_STRUCTURES|NATIVE_FAILED/, 30000)
      context.expect(/NATIVE_STRUCTURES templates=\d+ village=true connectors=[1-9]\d*/.test(response), response)
    })
    await context.step('create a world with the requested seed and generate a chunk', async () => {
      const response = await context.command('/irislifecycle create', /NATIVE_CREATED|NATIVE_FAILED/, 90000)
      context.expect(response.includes('NATIVE_CREATED seed=78264193 chunk=true'), response)
    })
    await context.step('unload and detach the created world', async () => {
      const response = await context.command('/irislifecycle unload', /NATIVE_UNLOADED|NATIVE_FAILED/, 90000)
      context.expect(response.includes('NATIVE_UNLOADED true'), response)
      await context.command('/irislifecycle status', /NATIVE_STATUS available=true loaded=false/, 10000)
    })
  }
}

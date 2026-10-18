import { readFile } from 'node:fs/promises'
import path from 'node:path'

export default {
  name: 'iris-craftengine-native-restart',
  description: 'Verify persisted generated furniture ownership after a full server restart.',
  async run(context) {
    const source = await readFile(path.join(context.server.directory, '.server-source'), 'utf8')
    context.expect(/^isolated=true\s*$/m.test(source), 'CraftEngine regression requires an isolated instance')
    await context.step('native writes remove persisted ownership after restart', async () => {
      const response = await context.command('/iriscenative restart', /CE_RESTART|CE_FAILED/, 60000)
      context.expect(response.includes('CE_RESTART removed=1 untouched=1'), response)
    })
  }
}

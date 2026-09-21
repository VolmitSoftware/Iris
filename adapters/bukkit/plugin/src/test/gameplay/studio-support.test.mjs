import test from 'node:test'
import assert from 'node:assert/strict'
import { mkdtemp, mkdir, writeFile, rm, symlink } from 'node:fs/promises'
import os from 'node:os'
import path from 'node:path'
import { parsePhase, ownedPath, setup, packKey } from './studio-support.mjs'

test('bulk bounds and destructive phases require explicit valid selection', () => {
  assert.deepEqual(parsePhase(), { phase: 'studio', cycles: 3 })
  assert.deepEqual(parsePhase('studio:20'), { phase: 'studio', cycles: 20 })
  for (const value of ['studio:0', 'studio:21', 'studio:999999', 'replace-stage:2', 'replace', 'studio;stop', '']) {
    assert.throws(() => parsePhase(value))
  }
})

test('fixture paths reject absolute and parent escapes', () => {
  assert.equal(ownedPath('/test/instance', 'world', 'dimensions'), '/test/instance/world/dimensions')
  for (const value of ['..', '../instance-other', '/test/another', '.']) {
    assert.throws(() => ownedPath('/test/instance', value))
  }
})

async function fixture(run) {
  const base = await mkdtemp(path.join(os.tmpdir(), 'iris-gameplay-'))
  const root = path.join(base, 'instance')
  const packs = path.join(root, 'plugins', 'Iris', 'packs')
  await mkdir(packs, { recursive: true })
  await writeFile(path.join(root, '.server-source'), 'type=paper\nisolated=true\n')
  await writeFile(path.join(root, 'server.properties'), 'server-ip=127.0.0.1\nlevel-name=world\n')
  const context = {
    server: { directory: root }, options: {}, report: {}, bot: { username: 'FixtureBot' },
    expect: (condition, message) => assert.ok(condition, message)
  }
  try { await run({ base, root, packs, context }) } finally { await rm(base, { recursive: true, force: true }) }
}

test('shared packs are refused even if the instance claims isolation', async () => fixture(async ({ base, packs, context }) => {
  const shared = path.join(base, 'shared-packs')
  await mkdir(shared)
  await rm(packs, { recursive: true })
  await symlink(shared, packs)
  await assert.rejects(setup(context, { prepare: true }), /Shared Iris packs/)
}))

test('normal and non-loopback instances cannot enter acceptance', async () => fixture(async ({ root, context }) => {
  await writeFile(path.join(root, '.server-source'), 'type=paper\n')
  await assert.rejects(setup(context, { prepare: true }), /isolated instance/)
  await writeFile(path.join(root, '.server-source'), 'isolated=true\n')
  await writeFile(path.join(root, 'server.properties'), 'server-ip=0.0.0.0\n')
  await assert.rejects(setup(context, { prepare: true }), /loopback instance/)
}))

test('a copied ownership marker cannot authorize another instance', async () => fixture(async ({ root, context }) => {
  await writeFile(path.join(root, '.multiplexor-iris-acceptance.json'), JSON.stringify({
    schemaVersion: 1, pack: packKey, instance: '/another/server'
  }))
  await assert.rejects(setup(context), /does not belong/)
}))

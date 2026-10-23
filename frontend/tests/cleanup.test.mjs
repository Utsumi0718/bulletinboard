import test from 'node:test'
import assert from 'node:assert/strict'
import { createCleanupApi } from '../src/api/cleanup.ts'

test('cleanup lists use bounded page queries', async () => {
  const paths = []
  const client = createCleanupApi({ request: async path => { paths.push(path); return [] } })
  await client.runs(2); await client.targets(3)
  assert.deepEqual(paths, ['/api/admin/topic-cleanup/runs?page=2&size=20', '/api/admin/topic-cleanup/targets?page=3&size=20'])
})
test('dry-run only uses GET and sends no body', async () => {
  const client = createCleanupApi({ request: async (path, options) => { assert.equal(path, '/api/admin/topic-cleanup/dry-run'); assert.equal(options?.method, undefined); assert.equal(options?.json, undefined); return [] } })
  await client.preview()
})
test('resume sends the target ID and reason only', async () => {
  const client = createCleanupApi({ request: async (path, options) => { assert.equal(path, '/api/admin/topic-cleanup/targets/42/resume'); assert.deepEqual(options, { method: 'POST', json: { reason: '原因を修正済み' } }) } })
  await client.resume(42, '原因を修正済み')
})
test('cleanup API preserves failures for the shared error UI', async () => {
  const error = new Error('offline')
  const client = createCleanupApi({ request: async () => { throw error } })
  await assert.rejects(client.attempts(1), candidate => candidate === error)
})

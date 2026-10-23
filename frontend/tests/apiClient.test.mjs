import assert from 'node:assert/strict'
import test from 'node:test'
import { ApiError, createApiClient, toUserFacingError } from '../src/api/client.ts'
import { getCurrentUser } from '../src/api/auth.ts'

function json(body, status = 200) {
  return new Response(JSON.stringify(body), {
    status,
    headers: { 'Content-Type': 'application/json' },
  })
}

function csrf() {
  return json({ headerName: 'X-CSRF-TOKEN', token: 'test-token' })
}

test('GET sends cookies and does not fetch a CSRF token', async () => {
  const calls = []
  const client = createApiClient(async (path, init) => {
    calls.push({ path, init })
    return json({ items: [] })
  })

  assert.deepEqual(await client.request('/api/topics'), { items: [] })
  assert.equal(calls.length, 1)
  assert.equal(calls[0].path, '/api/topics')
  assert.equal(calls[0].init.credentials, 'include')
  assert.equal(calls[0].init.headers.get('Accept'), 'application/json')
})

test('JSON POST obtains CSRF and sends its header with cookies', async () => {
  const calls = []
  const client = createApiClient(async (path, init) => {
    calls.push({ path, init })
    return path === '/api/csrf' ? csrf() : json({ id: 7 }, 201)
  })

  assert.deepEqual(await client.request('/api/topics', { method: 'POST', json: { title: '題' } }), { id: 7 })
  assert.deepEqual(calls.map((call) => call.path), ['/api/csrf', '/api/topics'])
  assert.equal(calls[0].init.credentials, 'include')
  assert.equal(calls[0].init.cache, 'no-store')
  assert.equal(calls[1].init.credentials, 'include')
  assert.equal(calls[1].init.headers.get('X-CSRF-TOKEN'), 'test-token')
  assert.equal(calls[1].init.headers.get('Content-Type'), 'application/json')
  assert.deepEqual(JSON.parse(calls[1].init.body), { title: '題' })
})

test('FormData keeps its browser-generated Content-Type', async () => {
  const calls = []
  const client = createApiClient(async (path, init) => {
    calls.push({ path, init })
    return path === '/api/csrf' ? csrf() : json({ id: 8 }, 201)
  })
  const body = new FormData()
  body.set('title', '題')

  await client.request('/api/topics', { method: 'POST', body })
  assert.equal(calls[1].init.body, body)
  assert.equal(calls[1].init.headers.get('Content-Type'), null)
  assert.equal(calls[1].init.headers.get('X-CSRF-TOKEN'), 'test-token')
})

test('login sends URL-encoded form data without a JSON Content-Type', async () => {
  const calls = []
  const client = createApiClient(async (path, init) => {
    calls.push({ path, init })
    return path === '/api/csrf' ? csrf() : json({ authenticated: true })
  })
  const body = new URLSearchParams({ email: 'user@example.invalid', password: 'example-password' })

  await client.request('/api/auth/login', { method: 'POST', body })
  assert.equal(calls[1].init.body, body)
  assert.equal(calls[1].init.headers.get('Content-Type'), null)
  assert.equal(calls[1].init.headers.get('X-CSRF-TOKEN'), 'test-token')
  assert.equal(calls[1].init.credentials, 'include')
})

test('CSRF HTTP failure prevents the mutation', async () => {
  const calls = []
  const client = createApiClient(async (path) => {
    calls.push(path)
    return json({ status: 403, message: 'このリクエストは許可されていません。' }, 403)
  })

  await assert.rejects(() => client.request('/api/topics', { method: 'POST', json: {} }), (error) => {
    assert.equal(toUserFacingError(error).category, 'forbidden')
    return true
  })
  assert.deepEqual(calls, ['/api/csrf'])
})

test('invalid CSRF response prevents the mutation', async () => {
  const calls = []
  const client = createApiClient(async (path) => {
    calls.push(path)
    return json({ headerName: 'X-CSRF-TOKEN' })
  })

  await assert.rejects(() => client.request('/api/topics', { method: 'POST' }), (error) => {
    assert.equal(error.kind, 'invalid-response')
    return true
  })
  assert.deepEqual(calls, ['/api/csrf'])
})

test('204 response has no JSON body', async () => {
  const client = createApiClient(async (path) => path === '/api/csrf'
    ? csrf() : new Response(null, { status: 204 }))

  assert.equal(await client.request('/api/auth/logout', { method: 'POST' }), undefined)
})

test('400 response preserves field errors for forms', async () => {
  const client = createApiClient(async () => json({
    status: 400, error: 'Bad Request', message: '入力内容を確認してください。',
    path: '/api/auth/register', fieldErrors: { email: 'メールアドレスを入力してください。' },
  }, 400))

  await assert.rejects(() => client.request('/api/auth/register'), (error) => {
    const display = toUserFacingError(error)
    assert.equal(display.category, 'validation')
    assert.equal(display.status, 400)
    assert.equal(display.fieldErrors.email, 'メールアドレスを入力してください。')
    return true
  })
})

test('401 login response preserves the public reason', async () => {
  const client = createApiClient(async () => json({
    status: 401, error: 'Unauthorized', message: 'このアカウントは凍結されています。',
    path: '/api/auth/login', reason: 'FROZEN',
  }, 401))

  await assert.rejects(() => client.request('/api/auth/login'), (error) => {
    const display = toUserFacingError(error)
    assert.equal(display.category, 'authentication')
    assert.equal(display.reason, 'FROZEN')
    assert.equal(display.message, 'このアカウントは凍結されています。')
    return true
  })
})

test('403 response is classified separately from 401', async () => {
  const client = createApiClient(async () => json({
    status: 403, error: 'Forbidden', message: 'このリクエストは許可されていません。',
    path: '/api/admin/users',
  }, 403))

  await assert.rejects(() => client.request('/api/admin/users'), (error) => {
    assert.equal(toUserFacingError(error).category, 'forbidden')
    assert.equal(error.status, 403)
    return true
  })
})

test('500 response never exposes its body message to the screen', async () => {
  const client = createApiClient(async () => json({
    status: 500, message: 'SELECT * FROM users; secret@example.invalid password=secret',
    fieldErrors: { email: 'secret@example.invalid' }, reason: 'DATABASE_SECRET',
  }, 500))

  await assert.rejects(() => client.request('/api/topics'), (error) => {
    const display = toUserFacingError(error)
    assert.equal(display.category, 'server')
    assert.doesNotMatch(display.message, /SELECT|example\.invalid|password/)
    assert.deepEqual(display.fieldErrors, {})
    assert.equal(display.reason, undefined)
    return true
  })
})

test('network failure becomes a safe connection error', async () => {
  const client = createApiClient(async () => { throw new Error('secret database address') })

  await assert.rejects(() => client.request('/api/topics'), (error) => {
    assert.ok(error instanceof ApiError)
    assert.equal(toUserFacingError(error).category, 'network')
    assert.doesNotMatch(error.message, /secret database/)
    return true
  })
})

test('anonymous /me response produces no user', async () => {
  const client = createApiClient(async () => json({ authenticated: false }))
  assert.equal(await getCurrentUser(client), null)
})

test('authenticated /me response keeps only public identity', async () => {
  const client = createApiClient(async () => json({
    authenticated: true, userId: 12, profileId: 25, username: 'tester', role: 'ROLE_USER',
  }))
  assert.deepEqual(await getCurrentUser(client), {
    userId: 12, profileId: 25, username: 'tester', role: 'ROLE_USER',
  })
})

test('authenticated /me accepts an omitted nullable profileId', async () => {
  const client = createApiClient(async () => json({
    authenticated: true, userId: 12, username: 'tester', role: 'ROLE_USER',
  }))
  assert.deepEqual(await getCurrentUser(client), {
    userId: 12, profileId: null, username: 'tester', role: 'ROLE_USER',
  })
})

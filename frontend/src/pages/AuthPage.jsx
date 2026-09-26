import { useState } from 'react'
import { api, setAccessToken } from '../api'
import { Button, Card, ErrorBox, Input } from '../components/ui'
import { useAction } from '../hooks'

export default function AuthPage() {
  const [mode, setMode] = useState('login')
  const { pending, error, run } = useAction()
  const [form, setForm] = useState({ firstName: '', lastName: '', email: '', password: '' })

  const update = (key) => (e) => setForm({ ...form, [key]: e.target.value })

  const submit = (e) => {
    e.preventDefault()
    run(async () => {
      const data =
        mode === 'login'
          ? await api.post('/auth/login', { username: form.email, password: form.password })
          : await api.post('/auth/register', { ...form, authProvider: 'LOCAL' })
      // login returns { accessToken }, register returns { token, user }
      setAccessToken(data.accessToken ?? data.token)
    })
  }

  return (
    <div className="mx-auto max-w-sm pt-12">
      <Card title={mode === 'login' ? 'Sign in' : 'Create account'}>
        <form onSubmit={submit} className="flex flex-col gap-3">
          {mode === 'register' && (
            <div className="grid grid-cols-2 gap-2">
              <Input label="First name" value={form.firstName} onChange={update('firstName')} required />
              <Input label="Last name" value={form.lastName} onChange={update('lastName')} required />
            </div>
          )}
          <Input label="Email" type="email" value={form.email} onChange={update('email')} required />
          <Input label="Password" type="password" value={form.password} onChange={update('password')} required />
          <ErrorBox error={error} />
          <Button type="submit" disabled={pending}>
            {mode === 'login' ? 'Sign in' : 'Register'}
          </Button>
        </form>
        <p className="mt-3 text-center text-sm text-fg-muted">
          {mode === 'login' ? 'No account?' : 'Already registered?'}{' '}
          <button
            className="font-medium text-fg underline"
            onClick={() => setMode(mode === 'login' ? 'register' : 'login')}
          >
            {mode === 'login' ? 'Register' : 'Sign in'}
          </button>
        </p>
      </Card>
    </div>
  )
}

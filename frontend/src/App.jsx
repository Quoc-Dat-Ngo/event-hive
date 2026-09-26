import { useEffect, useState } from 'react'
import { api, decodeToken, refreshAccessToken, setAccessToken, subscribeToken } from './api'
import { Button, ThemeToggle } from './components/ui'
import { useTheme } from './hooks'
import AuthPage from './pages/AuthPage'
import EventsPage from './pages/EventsPage'
import AccountPage, { CheckoutBanner } from './pages/AccountPage'
import ManagePage from './pages/ManagePage'

// Stripe redirects back with ?checkout=success|cancelled&bookingId=...
function readCheckoutParams() {
  const params = new URLSearchParams(window.location.search)
  const status = params.get('checkout')
  return status ? { status, bookingId: params.get('bookingId') } : null
}

export default function App() {
  const [token, setToken] = useState(null)
  const [restoring, setRestoring] = useState(true)
  const [tab, setTab] = useState('Events')
  const [checkout, setCheckout] = useState(readCheckoutParams)
  const [theme, setTheme] = useTheme()

  useEffect(() => {
    subscribeToken(setToken)
    // Restore the session from the httpOnly refresh-token cookie, if any
    refreshAccessToken().finally(() => setRestoring(false))
  }, [])

  const session = decodeToken(token)

  const dismissCheckout = () => {
    setCheckout(null)
    window.history.replaceState(null, '', window.location.pathname)
  }

  const logout = async (path) => {
    try {
      await api.post(path)
    } finally {
      setAccessToken(null)
      setTab('Events')
    }
  }

  if (restoring) return <p className="p-6 text-sm text-fg-subtle">Loading…</p>

  const canManage = session?.roles.some((r) => r === 'ADMIN' || r === 'EVENT_ORGANISER')
  const tabs = ['Events', 'Account', ...(canManage ? ['Manage'] : [])]

  return (
    <div className="min-h-screen bg-sunken text-fg">
      <header className="border-b border-line bg-surface">
        <div className="mx-auto flex max-w-6xl flex-wrap items-center justify-between gap-3 px-4 py-3">
          <div className="flex items-center gap-6">
            <span className="text-lg font-semibold">EventHive</span>
            {session && (
              <nav className="flex gap-1">
                {tabs.map((t) => (
                  <button
                    key={t}
                    onClick={() => setTab(t)}
                    className={`rounded-md px-3 py-1.5 text-sm ${
                      t === tab ? 'bg-inverse text-on-inverse' : 'text-fg-muted hover:bg-active'
                    }`}
                  >
                    {t}
                  </button>
                ))}
              </nav>
            )}
          </div>
          <div className="flex flex-wrap items-center gap-2 text-sm">
            {session && (
              <>
                <span className="text-fg-muted">
                  {session.email} <span className="text-xs text-fg-faint">({session.roles.join(', ')})</span>
                </span>
                <Button variant="secondary" onClick={() => logout('/auth/logout')}>Log out</Button>
                <Button variant="secondary" onClick={() => logout('/auth/logout-all')}>Log out everywhere</Button>
              </>
            )}
            <ThemeToggle theme={theme} onChange={setTheme} />
          </div>
        </div>
      </header>

      <main className="mx-auto flex max-w-6xl flex-col gap-4 px-4 py-6">
        {checkout && session && <CheckoutBanner {...checkout} session={session} onDismiss={dismissCheckout} />}
        {!session && <AuthPage />}
        {session && tab === 'Events' && <EventsPage session={session} />}
        {session && tab === 'Account' && <AccountPage session={session} />}
        {session && tab === 'Manage' && canManage && <ManagePage session={session} />}
      </main>
    </div>
  )
}

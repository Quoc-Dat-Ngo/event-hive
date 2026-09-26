import { useCallback, useEffect, useState } from 'react'

// Runs `fetcher` on mount and whenever `deps` change; `reload` refetches.
export function useFetch(fetcher, deps) {
  const [data, setData] = useState(null)
  const [error, setError] = useState(null)
  const [loading, setLoading] = useState(true)

  // eslint-disable-next-line react-hooks/exhaustive-deps
  const load = useCallback(fetcher, deps)

  const reload = useCallback(async () => {
    setLoading(true)
    setError(null)
    try {
      setData(await load())
    } catch (e) {
      setError(e)
    } finally {
      setLoading(false)
    }
  }, [load])

  useEffect(() => {
    reload()
  }, [reload])

  return { data, error, loading, reload }
}

// Wraps an async action with pending/error state for forms and buttons.
export function useAction() {
  const [pending, setPending] = useState(false)
  const [error, setError] = useState(null)

  const run = useCallback(async (fn) => {
    setPending(true)
    setError(null)
    try {
      return await fn()
    } catch (e) {
      setError(e)
      return undefined
    } finally {
      setPending(false)
    }
  }, [])

  return { pending, error, run }
}

export function formatDate(iso) {
  return iso ? new Date(iso).toLocaleString() : '—'
}

export function formatMoney(cents, currency = 'AUD') {
  if (cents == null) return '—'
  return new Intl.NumberFormat(undefined, { style: 'currency', currency: currency.toUpperCase() }).format(cents / 100)
}

const THEME_KEY = 'theme'
const darkQuery = window.matchMedia('(prefers-color-scheme: dark)')

function readTheme() {
  try {
    return localStorage.getItem(THEME_KEY) ?? 'system'
  } catch {
    return 'system'
  }
}

// 'system' | 'light' | 'dark'; mirrors the inline script in index.html
export function useTheme() {
  const [theme, setTheme] = useState(readTheme)

  useEffect(() => {
    const apply = () => {
      const dark = theme === 'dark' || (theme === 'system' && darkQuery.matches)
      document.documentElement.classList.toggle('dark', dark)
    }
    apply()
    try {
      localStorage.setItem(THEME_KEY, theme)
    } catch {
      // Storage can be unavailable (e.g. private mode); the theme still applies
    }
    if (theme !== 'system') return undefined
    darkQuery.addEventListener('change', apply)
    return () => darkQuery.removeEventListener('change', apply)
  }, [theme])

  return [theme, setTheme]
}

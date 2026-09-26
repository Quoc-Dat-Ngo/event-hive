
export function Card({ title, actions, children }) {
  return (
    <section className="rounded-lg border border-line bg-surface p-4">
      {(title || actions) && (
        <div className="mb-3 flex items-center justify-between gap-2">
          <h2 className="font-semibold text-fg">{title}</h2>
          {actions}
        </div>
      )}
      {children}
    </section>
  )
}

export function Button({ variant = 'primary', className = '', ...props }) {
  const styles = {
    primary: 'bg-inverse text-on-inverse hover:bg-inverse-hover',
    secondary: 'border border-control bg-surface text-fg hover:bg-hover',
    danger:
      'border border-red-300 bg-surface text-red-700 hover:bg-red-50 dark:border-red-800 dark:text-red-400 dark:hover:bg-red-950',
  }
  return (
    <button
      className={`rounded-md px-3 py-1.5 text-sm font-medium disabled:cursor-not-allowed disabled:opacity-50 ${styles[variant]} ${className}`}
      {...props}
    />
  )
}

export function Field({ label, children }) {
  return (
    <label className="flex flex-col gap-1 text-sm text-fg-soft">
      {label}
      {children}
    </label>
  )
}

const inputClass =
  'rounded-md border border-control bg-surface px-2 py-1.5 text-sm text-fg focus:border-fg focus:outline-none'

export function Input({ label, ...props }) {
  return (
    <Field label={label}>
      <input className={inputClass} {...props} />
    </Field>
  )
}

export function Select({ label, options, placeholder, ...props }) {
  return (
    <Field label={label}>
      <select className={inputClass} {...props}>
        {placeholder && <option value="">{placeholder}</option>}
        {options.map((o) => (
          <option key={o.value} value={o.value}>
            {o.label}
          </option>
        ))}
      </select>
    </Field>
  )
}

export function ErrorBox({ error }) {
  if (!error) return null
  return (
    <p className="rounded-md border border-red-200 bg-red-50 px-3 py-2 text-sm text-red-700 dark:border-red-900 dark:bg-red-950 dark:text-red-300">
      {error.status ? <span className="font-mono">{error.status} · </span> : null}
      {error.message}
    </p>
  )
}

export function Notice({ children }) {
  return (
    <p className="rounded-md border border-green-200 bg-green-50 px-3 py-2 text-sm text-green-800 dark:border-green-900 dark:bg-green-950 dark:text-green-300">
      {children}
    </p>
  )
}

const GREEN = 'bg-green-100 text-green-800 dark:bg-green-950 dark:text-green-300'
const YELLOW = 'bg-yellow-100 text-yellow-800 dark:bg-yellow-950 dark:text-yellow-300'
const RED = 'bg-red-100 text-red-800 dark:bg-red-950 dark:text-red-300'
const BLUE = 'bg-blue-100 text-blue-800 dark:bg-blue-950 dark:text-blue-300'
const NEUTRAL = 'bg-active text-fg-soft'

const BADGE_COLORS = {
  CONFIRMED: GREEN,
  SUCCEEDED: GREEN,
  PUBLISHED: GREEN,
  PENDING: YELLOW,
  DRAFT: YELLOW,
  REFUNDED: BLUE,
  EXPIRED: NEUTRAL,
  COMPLETED: NEUTRAL,
  CANCELLED: RED,
  FAILED: RED,
}

export function Badge({ children }) {
  return (
    <span className={`rounded px-1.5 py-0.5 text-xs font-medium ${BADGE_COLORS[children] ?? NEUTRAL}`}>
      {children}
    </span>
  )
}

export function Json({ value }) {
  return (
    <pre className="overflow-x-auto rounded-md bg-sunken p-2 text-xs text-fg-soft">
      {JSON.stringify(value, null, 2)}
    </pre>
  )
}

const THEME_ORDER = ['system', 'light', 'dark']
const THEME_LABELS = { system: '◐ System', light: '☀ Light', dark: '☾ Dark' }

export function ThemeToggle({ theme, onChange }) {
  const next = THEME_ORDER[(THEME_ORDER.indexOf(theme) + 1) % THEME_ORDER.length]
  return (
    <Button variant="secondary" onClick={() => onChange(next)} title={`Theme: ${theme} (click for ${next})`}>
      {THEME_LABELS[theme]}
    </Button>
  )
}

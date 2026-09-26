import { Fragment, useState } from 'react'
import { api } from '../api'
import { BookingDetail } from './AccountPage'
import {
  Badge,
  Button,
  Card,
  ErrorBox,
  Input,
  Notice,
  Select,
} from '../components/ui'
import { formatDate, formatMoney, useAction, useFetch } from '../hooks'

// Organisers can only manage events; admins see every section.
export default function ManagePage({ session }) {
  const isAdmin = session.roles.includes('ADMIN')
  const sections = isAdmin ? ['Events', 'Venues', 'Seats', 'Users', 'Bookings', 'Payments'] : ['Events']
  const [section, setSection] = useState(sections[0])

  return (
    <div className="flex flex-col gap-4">
      <nav className="flex flex-wrap gap-1">
        {sections.map((s) => (
          <Button key={s} variant={s === section ? 'primary' : 'secondary'} onClick={() => setSection(s)}>
            {s}
          </Button>
        ))}
      </nav>
      {section === 'Events' && <EventForm />}
      {section === 'Venues' && <Venues />}
      {section === 'Seats' && <SeatForm />}
      {section === 'Users' && <Users />}
      {section === 'Bookings' && <AllBookings />}
      {section === 'Payments' && <AllPayments />}
    </div>
  )
}

function useForm(initial) {
  const [form, setForm] = useState(initial)
  const bind = (key) => ({ value: form[key], onChange: (e) => setForm({ ...form, [key]: e.target.value }) })
  return { form, setForm, bind, reset: () => setForm(initial) }
}

// Shared submit handling: run the request, show what came back, reset the form.
function useSubmit(reset, onDone) {
  const action = useAction()
  const [created, setCreated] = useState(null)
  const submit = (fn) => (e) => {
    e.preventDefault()
    setCreated(null)
    action.run(async () => {
      setCreated(await fn())
      reset()
      onDone?.()
    })
  }
  return { ...action, created, submit }
}

function venueOptions(venues) {
  return (venues ?? []).map((v) => ({ value: v.id, label: `${v.name} — ${v.location}` }))
}

function EventForm() {
  const venues = useFetch(() => api.get('/venues'), [])
  const { form, bind, reset } = useForm({
    title: '', purpose: '', performer: '', startsAt: '', endsAt: '', status: 'PUBLISHED', venueId: '',
  })
  const { pending, error, created, submit } = useSubmit(reset)

  return (
    <Card title="New event">
      <form
        className="grid gap-3 sm:grid-cols-2"
        onSubmit={submit(() =>
          api.post('/events', {
            ...form,
            startsAt: new Date(form.startsAt).toISOString(),
            endsAt: new Date(form.endsAt).toISOString(),
          }),
        )}
      >
        <Input label="Title" required minLength={3} {...bind('title')} />
        <Input label="Performer" {...bind('performer')} />
        <div className="sm:col-span-2"><Input label="Purpose" {...bind('purpose')} /></div>
        <Input label="Starts at" type="datetime-local" required {...bind('startsAt')} />
        <Input label="Ends at" type="datetime-local" required {...bind('endsAt')} />
        <Select
          label="Status"
          options={['DRAFT', 'PUBLISHED', 'CANCELLED', 'COMPLETED'].map((s) => ({ value: s, label: s }))}
          {...bind('status')}
        />
        <Select label="Venue" placeholder="Select a venue" required options={venueOptions(venues.data)} {...bind('venueId')} />
        <div className="flex flex-col gap-2 sm:col-span-2">
          <ErrorBox error={error ?? venues.error} />
          {created && <Notice>Created event “{created.title}”.</Notice>}
          <Button type="submit" disabled={pending} className="self-start">Create event</Button>
        </div>
      </form>
    </Card>
  )
}

function Venues() {
  const venues = useFetch(() => api.get('/venues'), [])
  const { form, bind, reset } = useForm({ name: '', location: '', capacity: '' })
  const { pending, error, created, submit } = useSubmit(reset, venues.reload)
  const del = useAction()

  return (
    <div className="grid gap-4 md:grid-cols-[320px_1fr]">
      <Card title="New venue">
        <form
          className="flex flex-col gap-3"
          onSubmit={submit(() => api.post('/venues', { ...form, capacity: Number(form.capacity) }))}
        >
          <Input label="Name" required minLength={3} {...bind('name')} />
          <Input label="Location" required minLength={3} {...bind('location')} />
          <Input label="Capacity" type="number" min="0" required {...bind('capacity')} />
          <ErrorBox error={error} />
          {created && <Notice>Created venue “{created.name}”.</Notice>}
          <Button type="submit" disabled={pending}>Create venue</Button>
        </form>
      </Card>
      <Card title="Venues">
        <ErrorBox error={venues.error ?? del.error} />
        <table className="w-full text-left text-sm">
          <thead className="text-fg-subtle">
            <tr><th className="py-1">Name</th><th>Location</th><th>Capacity</th><th /></tr>
          </thead>
          <tbody>
            {venues.data?.map((v) => (
              <tr key={v.id} className="border-t border-line-soft">
                <td className="py-1">{v.name}</td>
                <td>{v.location}</td>
                <td>{v.capacity}</td>
                <td className="text-right">
                  <Button
                    variant="danger"
                    disabled={del.pending}
                    onClick={() => confirm(`Delete ${v.name}?`) && del.run(() => api.del(`/venues/${v.id}`).then(venues.reload))}
                  >
                    Delete
                  </Button>
                </td>
              </tr>
            ))}
          </tbody>
        </table>
      </Card>
    </div>
  )
}

function SeatForm() {
  const venues = useFetch(() => api.get('/venues'), [])
  const { form, setForm, bind } = useForm({ venueId: '', seatRow: '', number: '' })
  // Keep the venue selected so several seats can be added in a row
  const { pending, error, created, submit } = useSubmit(() => setForm((f) => ({ ...f, seatRow: '', number: '' })))

  return (
    <Card title="New seat">
      <form
        className="flex max-w-sm flex-col gap-3"
        onSubmit={submit(() =>
          api.post('/seats', { ...form, seatRow: form.seatRow.toUpperCase(), number: Number(form.number) }),
        )}
      >
        <Select label="Venue" placeholder="Select a venue" required options={venueOptions(venues.data)} {...bind('venueId')} />
        <div className="grid grid-cols-2 gap-2">
          <Input label="Row" required maxLength={2} pattern="[A-Za-z]{1,2}" {...bind('seatRow')} />
          <Input label="Number" type="number" min="1" required {...bind('number')} />
        </div>
        <ErrorBox error={error ?? venues.error} />
        {created && <Notice>Created seat {created.seatRow}{created.number}.</Notice>}
        <Button type="submit" disabled={pending}>Create seat</Button>
      </form>
    </Card>
  )
}

function Users() {
  const [search, setSearch] = useState('')
  const users = useFetch(
    () => api.get(`/users?pageSize=100${search ? `&search=${encodeURIComponent(search)}` : ''}`),
    [search],
  )
  const { form, bind, reset } = useForm({ firstName: '', lastName: '', email: '', password: '', role: 'EVENT_ORGANISER' })
  const { pending, error, created, submit } = useSubmit(reset, users.reload)
  const del = useAction()

  return (
    <div className="grid gap-4 md:grid-cols-[320px_1fr]">
      <Card title="New user">
        <form
          className="flex flex-col gap-3"
          onSubmit={submit(() => api.post('/users', { ...form, authProvider: 'LOCAL' }))}
        >
          <Input label="First name" required minLength={3} {...bind('firstName')} />
          <Input label="Last name" required minLength={3} {...bind('lastName')} />
          <Input label="Email" type="email" required {...bind('email')} />
          <Input label="Password" type="password" required minLength={6} {...bind('password')} />
          <Select
            label="Role"
            options={['USER', 'EVENT_ORGANISER', 'ADMIN'].map((r) => ({ value: r, label: r }))}
            {...bind('role')}
          />
          <ErrorBox error={error} />
          {created && <Notice>Created {created.email}.</Notice>}
          <Button type="submit" disabled={pending}>Create user</Button>
        </form>
      </Card>
      <Card title="Users">
        <div className="mb-3 max-w-xs">
          <Input placeholder="Search…" value={search} onChange={(e) => setSearch(e.target.value)} />
        </div>
        <ErrorBox error={users.error ?? del.error} />
        <table className="w-full text-left text-sm">
          <thead className="text-fg-subtle">
            <tr><th className="py-1">Name</th><th>Email</th><th>Role</th><th /></tr>
          </thead>
          <tbody>
            {users.data?.map((u) => (
              <tr key={u.id} className="border-t border-line-soft">
                <td className="py-1">{u.firstName} {u.lastName}</td>
                <td>{u.email}</td>
                <td><Badge>{u.role}</Badge></td>
                <td className="text-right">
                  <Button
                    variant="danger"
                    disabled={del.pending}
                    onClick={() => confirm(`Delete ${u.email}?`) && del.run(() => api.del(`/users/${u.id}`).then(users.reload))}
                  >
                    Delete
                  </Button>
                </td>
              </tr>
            ))}
          </tbody>
        </table>
      </Card>
    </div>
  )
}

const BOOKING_STATUSES = ['PENDING', 'CONFIRMED', 'EXPIRED', 'CANCELLED']

function AllBookings() {
  const bookings = useFetch(() => api.get('/bookings'), [])
  const [openId, setOpenId] = useState(null)
  const update = useAction()

  // The DB allows only one PENDING/CONFIRMED booking per seat, so the server may
  // reject a change with 409; reloading keeps the table in sync either way.
  const setStatus = (booking, status) => {
    // Cancelling runs the same side effects as a customer cancellation
    const effect =
      status !== 'CANCELLED'
        ? ''
        : booking.status === 'CONFIRMED'
          ? ' The payment will be refunded.'
          : booking.status === 'PENDING'
            ? ' The Stripe checkout will be expired and the seat released.'
            : ''
    if (!confirm(`Change booking ${booking.id.slice(0, 8)} from ${booking.status} to ${status}?${effect}`)) return
    update.run(() => api.put(`/bookings/${booking.id}`, { status }).finally(bookings.reload))
  }

  return (
    <Card title="All bookings" actions={<Button variant="secondary" onClick={bookings.reload}>Refresh</Button>}>
      <ErrorBox error={bookings.error ?? update.error} />
      <table className="w-full text-left text-sm">
        <thead className="text-fg-subtle">
          <tr><th className="py-1">Booking</th><th>User</th><th>Price</th><th>Created</th><th>Status</th></tr>
        </thead>
        <tbody>
          {bookings.data?.map((b) => (
            <Fragment key={b.id}>
              <tr
                className="cursor-pointer border-t border-line-soft hover:bg-hover"
                onClick={() => setOpenId(openId === b.id ? null : b.id)}
              >
                <td className="py-1 font-mono text-xs">{b.id.slice(0, 8)}</td>
                <td className="font-mono text-xs">{b.userId.slice(0, 8)}</td>
                <td>{formatMoney(b.priceCents)}</td>
                <td>{formatDate(b.createdAt)}</td>
                <td className="py-1" onClick={(e) => e.stopPropagation()}>
                  <div className="flex items-center gap-2">
                    <Badge>{b.status}</Badge>
                    <select
                      aria-label="Change status"
                      className="rounded border border-control bg-surface px-1 py-0.5 text-xs text-fg"
                      value=""
                      disabled={update.pending}
                      onChange={(e) => setStatus(b, e.target.value)}
                    >
                      <option value="" disabled>Change…</option>
                      {BOOKING_STATUSES.filter((s) => s !== b.status).map((s) => (
                        <option key={s} value={s}>{s}</option>
                      ))}
                    </select>
                  </div>
                </td>
              </tr>
              {openId === b.id && (
                <tr>
                  <td colSpan={5}><BookingDetail bookingId={b.id} /></td>
                </tr>
              )}
            </Fragment>
          ))}
        </tbody>
      </table>
    </Card>
  )
}

function AllPayments() {
  const payments = useFetch(() => api.get('/payments'), [])

  return (
    <Card title="All payments" actions={<Button variant="secondary" onClick={payments.reload}>Refresh</Button>}>
      <ErrorBox error={payments.error} />
      {payments.data?.length === 0 && <p className="text-sm text-fg-subtle">No payments yet.</p>}
      <table className="w-full text-left text-sm">
        <thead className="text-fg-subtle">
          <tr><th className="py-1">Payment intent</th><th>Booking</th><th>Amount</th><th>Purchased</th><th>Status</th></tr>
        </thead>
        <tbody>
          {payments.data?.map((p) => (
            <tr key={p.id} className="border-t border-line-soft">
              <td className="py-1 font-mono text-xs">{p.stripePaymentIntentId}</td>
              <td className="font-mono text-xs">{p.bookingId.slice(0, 8)}</td>
              <td>{formatMoney(p.amountCents, p.currency)}</td>
              <td>{formatDate(p.purchasedAt)}</td>
              <td><Badge>{p.status}</Badge></td>
            </tr>
          ))}
        </tbody>
      </table>
    </Card>
  )
}

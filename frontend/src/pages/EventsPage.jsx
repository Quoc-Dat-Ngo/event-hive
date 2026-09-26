import { useState } from 'react'
import { api } from '../api'
import {
  Badge,
  Button,
  Card,
  ErrorBox,
  Input,
} from '../components/ui'
import { formatDate, formatMoney, useAction, useFetch } from '../hooks'

export default function EventsPage({ session }) {
  const events = useFetch(() => api.get('/events'), [])
  const [selectedId, setSelectedId] = useState(null)
  const selected = events.data?.find((e) => e.id === selectedId)

  return (
    <div className="grid gap-4 md:grid-cols-[320px_1fr]">
      <Card title="Events" actions={<Button variant="secondary" onClick={events.reload}>Refresh</Button>}>
        <ErrorBox error={events.error} />
        {events.loading && <p className="text-sm text-fg-subtle">Loading…</p>}
        <ul className="flex flex-col gap-1">
          {events.data?.map((event) => (
            <li key={event.id}>
              <button
                onClick={() => setSelectedId(event.id)}
                className={`w-full rounded-md px-3 py-2 text-left hover:bg-hover ${
                  event.id === selectedId ? 'bg-active' : ''
                }`}
              >
                <div className="flex items-center justify-between gap-2">
                  <span className="font-medium text-fg">{event.title}</span>
                  <Badge>{event.status}</Badge>
                </div>
                <div className="text-xs text-fg-subtle">{formatDate(event.startsAt)}</div>
              </button>
            </li>
          ))}
          {events.data?.length === 0 && <p className="text-sm text-fg-subtle">No events yet.</p>}
        </ul>
      </Card>

      {selected ? (
        <EventDetail
          key={selected.id}
          event={selected}
          session={session}
          onDeleted={() => {
            setSelectedId(null)
            events.reload()
          }}
        />
      ) : (
        <Card>
          <p className="text-sm text-fg-subtle">Select an event to see its venue and book a seat.</p>
        </Card>
      )}
    </div>
  )
}

function EventDetail({ event, session, onDeleted }) {
  const canManage = session.roles.some((r) => r === 'ADMIN' || r === 'EVENT_ORGANISER')
  const host = useFetch(() => api.get(`/events/${event.id}/host`), [event.id])
  const del = useAction()
  // Bumped when tiers change so the seat map refetches
  const [tierVersion, setTierVersion] = useState(0)

  const remove = () => {
    if (!confirm(`Delete "${event.title}"?`)) return
    del.run(async () => {
      await api.del(`/events/${event.id}`)
      onDeleted()
    })
  }

  return (
    <div className="flex flex-col gap-4">
      <Card
        title={event.title}
        actions={
          canManage && (
            <Button variant="danger" onClick={remove} disabled={del.pending}>
              Delete
            </Button>
          )
        }
      >
        <dl className="grid grid-cols-[auto_1fr] gap-x-4 gap-y-1 text-sm">
          <dt className="text-fg-subtle">Status</dt>
          <dd><Badge>{event.status}</Badge></dd>
          <dt className="text-fg-subtle">Performer</dt>
          <dd>{event.performer || '—'}</dd>
          <dt className="text-fg-subtle">Purpose</dt>
          <dd>{event.purpose || '—'}</dd>
          <dt className="text-fg-subtle">When</dt>
          <dd>{formatDate(event.startsAt)} → {formatDate(event.endsAt)}</dd>
          <dt className="text-fg-subtle">Venue</dt>
          <dd>
            {host.data ? `${host.data.venueName}, ${host.data.location} (capacity ${host.data.capacity})` : '…'}
          </dd>
        </dl>
        <div className="mt-2"><ErrorBox error={host.error ?? del.error} /></div>
      </Card>

      <SeatBooking event={event} refreshKey={tierVersion} />
      {canManage && <TierManager eventId={event.id} onChange={() => setTierVersion((v) => v + 1)} />}
      {canManage && <EventBookings eventId={event.id} />}
    </div>
  )
}

// Tiers are ordered by price (highest first); each gets a stable colour by that position
const TIER_STYLES = [
  'border-violet-300 bg-violet-100 text-violet-900 dark:border-violet-800 dark:bg-violet-950 dark:text-violet-200',
  'border-amber-300 bg-amber-100 text-amber-900 dark:border-amber-800 dark:bg-amber-950 dark:text-amber-200',
  'border-sky-300 bg-sky-100 text-sky-900 dark:border-sky-800 dark:bg-sky-950 dark:text-sky-200',
  'border-emerald-300 bg-emerald-100 text-emerald-900 dark:border-emerald-800 dark:bg-emerald-950 dark:text-emerald-200',
  'border-rose-300 bg-rose-100 text-rose-900 dark:border-rose-800 dark:bg-rose-950 dark:text-rose-200',
  'border-lime-300 bg-lime-100 text-lime-900 dark:border-lime-800 dark:bg-lime-950 dark:text-lime-200',
]

function tierStyle(tiers, tierId) {
  const index = tiers.findIndex((t) => t.id === tierId)
  return TIER_STYLES[index % TIER_STYLES.length]
}

function SeatBooking({ event, refreshKey }) {
  const map = useFetch(
    async () => {
      const [seats, tiers] = await Promise.all([
        api.get(`/events/${event.id}/seats`),
        api.get(`/events/${event.id}/tiers`),
      ])
      return { seats, tiers }
    },
    [event.id, refreshKey],
  )
  const [seatId, setSeatId] = useState(null)
  const booking = useAction()

  const seats = map.data?.seats ?? []
  const tiers = map.data?.tiers ?? []
  const rows = Object.entries(Object.groupBy(seats, (s) => s.seatRow))
  const selected = seats.find((s) => s.seatId === seatId)

  // The server charges the seat's tier price; the client only picks the seat
  const book = () =>
    booking.run(async () => {
      const res = await api.post('/bookings', { eventId: event.id, seatId })
      window.location.href = res.url
    })

  return (
    <Card title="Book a seat" actions={<Button variant="secondary" onClick={map.reload}>Refresh</Button>}>
      <ErrorBox error={map.error} />
      {map.loading && !map.data && <p className="text-sm text-fg-subtle">Loading seats…</p>}
      {map.data && tiers.length === 0 && (
        <p className="text-sm text-fg-subtle">Tickets for this event are not on sale yet.</p>
      )}

      {tiers.length > 0 && (
        <ul className="mb-3 flex flex-wrap gap-2 text-xs">
          {tiers.map((t) => (
            <li key={t.id} className={`rounded border px-2 py-1 ${tierStyle(tiers, t.id)}`}>
              {t.name} · {formatMoney(t.priceCents)}
            </li>
          ))}
          <li className="rounded border border-line bg-active px-2 py-1 text-fg-faint line-through">Taken</li>
          <li className="rounded border border-dashed border-control px-2 py-1 text-fg-faint">Not on sale</li>
        </ul>
      )}

      <div className="flex flex-col gap-1 overflow-x-auto">
        {rows.map(([row, rowSeats]) => (
          <div key={row} className="flex items-center gap-1">
            <span className="w-6 font-mono text-xs text-fg-subtle">{row}</span>
            {rowSeats.map((seat) => {
              const label = `${seat.seatRow}${seat.number}`
              const style =
                seat.seatId === seatId
                  ? 'border-transparent bg-inverse text-on-inverse'
                  : !seat.tierId
                    ? 'border-dashed border-control text-fg-faint'
                    : !seat.available
                      ? 'border-line bg-active text-fg-faint line-through'
                      : `${tierStyle(tiers, seat.tierId)} hover:brightness-95`
              return (
                <button
                  key={seat.seatId}
                  onClick={() => setSeatId(seat.seatId)}
                  disabled={!seat.available}
                  title={seat.tierId ? `${label} · ${seat.tierName} · ${formatMoney(seat.priceCents)}` : `${label} · not on sale`}
                  className={`h-7 w-7 shrink-0 rounded border text-xs disabled:cursor-not-allowed ${style}`}
                >
                  {seat.number}
                </button>
              )
            })}
          </div>
        ))}
      </div>

      <div className="mt-4 flex flex-wrap items-center gap-3">
        <Button onClick={book} disabled={!selected || booking.pending}>
          {booking.pending
            ? 'Redirecting…'
            : selected
              ? `Book ${selected.seatRow}${selected.number} (${selected.tierName}) · ${formatMoney(selected.priceCents)}`
              : 'Select a seat'}
        </Button>
      </div>
      <p className="mt-2 text-xs text-fg-subtle">
        The seat is held for 31 minutes while you complete Stripe checkout. The booking is confirmed by the webhook.
        Cancellations are possible up to 48 hours before the event starts.
      </p>
      <div className="mt-2"><ErrorBox error={booking.error} /></div>
    </Card>
  )
}

const EMPTY_RANGE = { rowFrom: '', rowTo: '', numberFrom: '', numberTo: '' }

function TierManager({ eventId, onChange }) {
  const tiers = useFetch(() => api.get(`/events/${eventId}/tiers`), [eventId])
  const [form, setForm] = useState({ name: '', price: '', ranges: [EMPTY_RANGE] })
  const create = useAction()
  const edit = useAction()

  const changed = async () => {
    await tiers.reload()
    onChange()
  }

  const setRange = (index, key, value) =>
    setForm({ ...form, ranges: form.ranges.map((r, i) => (i === index ? { ...r, [key]: value } : r)) })

  const submit = (e) => {
    e.preventDefault()
    create.run(async () => {
      await api.post(`/events/${eventId}/tiers`, {
        name: form.name,
        priceCents: Math.round(Number(form.price) * 100),
        // Blank bounds mean "whole row" / "same row"
        seatRanges: form.ranges.map((r) => ({
          rowFrom: r.rowFrom,
          rowTo: r.rowTo || null,
          numberFrom: r.numberFrom ? Number(r.numberFrom) : null,
          numberTo: r.numberTo ? Number(r.numberTo) : null,
        })),
      })
      setForm({ name: '', price: '', ranges: [EMPTY_RANGE] })
      await changed()
    })
  }

  const rename = (tier) => {
    const name = prompt('Tier name', tier.name)
    if (name === null) return
    const price = prompt('Price (AUD)', (tier.priceCents / 100).toFixed(2))
    if (price === null) return
    edit.run(async () => {
      await api.put(`/events/${eventId}/tiers/${tier.id}`, { name, priceCents: Math.round(Number(price) * 100) })
      await changed()
    })
  }

  const remove = (tier) => {
    if (!confirm(`Delete tier "${tier.name}"? Its seats will no longer be on sale.`)) return
    edit.run(async () => {
      await api.del(`/events/${eventId}/tiers/${tier.id}`)
      await changed()
    })
  }

  return (
    <Card title="Price tiers">
      <ErrorBox error={tiers.error ?? edit.error} />
      {tiers.data?.length === 0 && <p className="text-sm text-fg-subtle">No tiers yet — no seats are on sale.</p>}
      {tiers.data?.length > 0 && (
        <table className="mb-4 w-full text-left text-sm">
          <thead className="text-fg-subtle">
            <tr><th className="py-1">Tier</th><th>Price</th><th>Seats</th><th /></tr>
          </thead>
          <tbody>
            {tiers.data.map((t) => (
              <tr key={t.id} className="border-t border-line-soft">
                <td className="py-1">
                  <span className={`rounded border px-1.5 py-0.5 text-xs ${tierStyle(tiers.data, t.id)}`}>{t.name}</span>
                </td>
                <td>{formatMoney(t.priceCents)}</td>
                <td>{t.seatCount}</td>
                <td className="flex justify-end gap-1 py-1">
                  <Button variant="secondary" disabled={edit.pending} onClick={() => rename(t)}>Edit</Button>
                  <Button variant="danger" disabled={edit.pending} onClick={() => remove(t)}>Delete</Button>
                </td>
              </tr>
            ))}
          </tbody>
        </table>
      )}

      <form onSubmit={submit} className="flex flex-col gap-3 border-t border-line-soft pt-3">
        <h3 className="text-sm font-medium">New tier</h3>
        <div className="grid gap-2 sm:grid-cols-2">
          <Input label="Name" required value={form.name} onChange={(e) => setForm({ ...form, name: e.target.value })} />
          <Input label="Price (AUD)" type="number" min="0.5" step="0.01" required value={form.price} onChange={(e) => setForm({ ...form, price: e.target.value })} />
        </div>
        {form.ranges.map((r, i) => (
          <div key={i} className="grid grid-cols-[1fr_1fr_1fr_1fr_auto] items-end gap-2">
            <Input label="From row" required maxLength={2} pattern="[A-Za-z]{1,2}" value={r.rowFrom} onChange={(e) => setRange(i, 'rowFrom', e.target.value)} />
            <Input label="To row" maxLength={2} pattern="[A-Za-z]{1,2}" placeholder="same" value={r.rowTo} onChange={(e) => setRange(i, 'rowTo', e.target.value)} />
            <Input label="From seat #" type="number" min="1" placeholder="first" value={r.numberFrom} onChange={(e) => setRange(i, 'numberFrom', e.target.value)} />
            <Input label="To seat #" type="number" min="1" placeholder="last" value={r.numberTo} onChange={(e) => setRange(i, 'numberTo', e.target.value)} />
            <Button
              type="button"
              variant="secondary"
              disabled={form.ranges.length === 1}
              onClick={() => setForm({ ...form, ranges: form.ranges.filter((_, j) => j !== i) })}
            >
              Remove
            </Button>
          </div>
        ))}
        <div className="flex flex-wrap gap-2">
          <Button type="button" variant="secondary" onClick={() => setForm({ ...form, ranges: [...form.ranges, EMPTY_RANGE] })}>
            Add range
          </Button>
          <Button type="submit" disabled={create.pending}>Create tier</Button>
        </div>
        <p className="text-xs text-fg-subtle">
          Rows run A–Z then AA–ZZ. Leave seat numbers blank to include whole rows. A seat can belong to only one tier.
        </p>
        <ErrorBox error={create.error} />
      </form>
    </Card>
  )
}

function EventBookings({ eventId }) {
  const bookings = useFetch(() => api.get(`/events/${eventId}/bookings`), [eventId])

  return (
    <Card title="Bookings for this event" actions={<Button variant="secondary" onClick={bookings.reload}>Refresh</Button>}>
      <ErrorBox error={bookings.error} />
      {bookings.data?.length === 0 && <p className="text-sm text-fg-subtle">No bookings yet.</p>}
      {bookings.data?.length > 0 && (
        <table className="w-full text-left text-sm">
          <thead className="text-fg-subtle">
            <tr><th className="py-1">Booking</th><th>Seat</th><th>User</th><th>Price</th><th>Status</th></tr>
          </thead>
          <tbody>
            {bookings.data.map((b) => (
              <tr key={b.bookingId} className="border-t border-line-soft">
                <td className="py-1 font-mono text-xs">{b.bookingId.slice(0, 8)}</td>
                <td className="font-mono text-xs">{b.seatId.slice(0, 8)}</td>
                <td className="font-mono text-xs">{b.userId.slice(0, 8)}</td>
                <td>{formatMoney(b.priceCents)}</td>
                <td><Badge>{b.status}</Badge></td>
              </tr>
            ))}
          </tbody>
        </table>
      )}
    </Card>
  )
}

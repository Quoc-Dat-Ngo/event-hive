import { useState } from 'react'
import { api } from '../api'
import {
  Badge,
  Button,
  Card,
  ErrorBox,
  Input,
  Notice,
} from '../components/ui'
import { formatDate, formatMoney, useAction, useFetch } from '../hooks'

export default function AccountPage({ session }) {
  return (
    <div className="grid gap-4 md:grid-cols-[320px_1fr]">
      <Profile userId={session.userId} />
      <MyBookings session={session} />
    </div>
  )
}

function Profile({ userId }) {
  const user = useFetch(() => api.get(`/users/${userId}`), [userId])
  const [form, setForm] = useState(null)
  const save = useAction()

  const current = form ?? { firstName: user.data?.firstName ?? '', lastName: user.data?.lastName ?? '' }

  const submit = (e) => {
    e.preventDefault()
    save.run(async () => {
      await api.put(`/users/${userId}`, current)
      setForm(null)
      await user.reload()
    })
  }

  return (
    <Card title="Profile">
      <ErrorBox error={user.error} />
      {user.data && (
        <form onSubmit={submit} className="flex flex-col gap-3">
          <p className="text-sm text-fg-muted">
            {user.data.email} · <Badge>{user.data.role}</Badge>
          </p>
          <Input label="First name" value={current.firstName} onChange={(e) => setForm({ ...current, firstName: e.target.value })} />
          <Input label="Last name" value={current.lastName} onChange={(e) => setForm({ ...current, lastName: e.target.value })} />
          <ErrorBox error={save.error} />
          <Button type="submit" disabled={!form || save.pending}>Save</Button>
          <p className="text-xs text-fg-subtle">Member since {formatDate(user.data.createdAt)}</p>
        </form>
      )}
    </Card>
  )
}

function MyBookings({ session }) {
  const bookings = useFetch(() => api.get(`/users/${session.userId}/bookings`), [session.userId])
  const [openId, setOpenId] = useState(null)

  return (
    <Card title="My bookings" actions={<Button variant="secondary" onClick={bookings.reload}>Refresh</Button>}>
      <ErrorBox error={bookings.error} />
      {bookings.data?.length === 0 && <p className="text-sm text-fg-subtle">You have no bookings yet.</p>}
      <ul className="flex flex-col divide-y divide-line-soft">
        {bookings.data?.map((b) => (
          <li key={b.bookingId} className="py-2">
            <button
              className="flex w-full items-center justify-between gap-2 text-left"
              onClick={() => setOpenId(openId === b.bookingId ? null : b.bookingId)}
            >
              <span className="font-mono text-xs text-fg-muted">{b.bookingId}</span>
              <span className="flex items-center gap-2 text-sm">
                {formatMoney(b.priceCents)} <Badge>{b.status}</Badge>
              </span>
            </button>
            {openId === b.bookingId && (
              <BookingDetail
                bookingId={b.bookingId}
                actions={(event) => (
                  <BookingActions booking={b} event={event} session={session} onChange={bookings.reload} />
                )}
              />
            )}
          </li>
        ))}
      </ul>
    </Card>
  )
}

// `actions` is an optional render prop that receives the loaded event
export function BookingDetail({ bookingId, actions }) {
  const detail = useFetch(async () => {
    const [event, seat, payment] = await Promise.all([
      api.get(`/bookings/${bookingId}/event`),
      api.get(`/bookings/${bookingId}/seat`),
      // A PENDING/EXPIRED booking has no payment row yet
      api.get(`/bookings/${bookingId}/payments`).catch((e) => (e.status === 404 ? null : Promise.reject(e))),
    ])
    return { event, seat, payment }
  }, [bookingId])

  if (detail.error) return <div className="mt-2"><ErrorBox error={detail.error} /></div>
  if (!detail.data) return <p className="mt-2 text-sm text-fg-subtle">Loading…</p>

  const { event, seat, payment } = detail.data
  const details = (
    <dl className="mt-2 grid grid-cols-[auto_1fr] gap-x-4 gap-y-1 rounded-md bg-sunken p-3 text-sm">
      <dt className="text-fg-subtle">Event</dt>
      <dd>{event.title} · {formatDate(event.startsAt)}</dd>
      <dt className="text-fg-subtle">Seat</dt>
      <dd>{seat.seatRow}{seat.number}</dd>
      <dt className="text-fg-subtle">Payment</dt>
      <dd>
        {payment ? (
          <>
            {formatMoney(payment.amountCents, payment.currency)} <Badge>{payment.status}</Badge>
            <span className="ml-2 font-mono text-xs text-fg-subtle">{payment.stripePaymentIntentId}</span>
          </>
        ) : (
          'No payment recorded'
        )}
      </dd>
    </dl>
  )

  return actions ? (
    <>
      {details}
      {actions(event)}
    </>
  ) : (
    details
  )
}

// Mirrors BookingService.CANCELLATION_CUTOFF; the server enforces it either way
const CANCELLATION_CUTOFF_MS = 48 * 60 * 60 * 1000

// Works with both BookingSummaryDTO (bookingId) and BookingDTO (id)
export function BookingActions({ booking, event, session, onChange }) {
  const action = useAction()
  const id = booking.bookingId ?? booking.id
  const isAdmin = session.roles.includes('ADMIN')
  const pastCutoff = event && new Date(event.startsAt).getTime() - Date.now() < CANCELLATION_CUTOFF_MS
  const canCancel = isAdmin || !pastCutoff

  // Re-posting the same seat returns the still-open Stripe session of our PENDING booking
  const resume = () =>
    action.run(async () => {
      const res = await api.post('/bookings', { eventId: booking.eventId, seatId: booking.seatId })
      window.location.href = res.url
    })

  const cancel = () => {
    const refund = booking.status === 'CONFIRMED'
    if (!confirm(refund ? 'Cancel this booking and refund the payment?' : 'Cancel this booking and release the seat?')) return
    action.run(async () => {
      await api.post(`/bookings/${id}/cancel`)
      onChange?.()
    })
  }

  if (booking.status !== 'PENDING' && booking.status !== 'CONFIRMED') return null

  return (
    <div className="mt-2 flex flex-col gap-2">
      <div className="flex flex-wrap gap-2">
        {booking.status === 'PENDING' && (
          <Button onClick={resume} disabled={action.pending}>Resume payment</Button>
        )}
        <Button variant="danger" onClick={cancel} disabled={action.pending || !canCancel}>
          {booking.status === 'CONFIRMED' ? 'Cancel & refund' : 'Cancel booking'}
        </Button>
      </div>
      {!canCancel && (
        <p className="text-xs text-fg-subtle">
          Bookings can only be cancelled up to 48 hours before the event starts. Please contact support for help.
        </p>
      )}
      {isAdmin && pastCutoff && (
        <p className="text-xs text-fg-subtle">Within 48 hours of the event — cancelling as admin.</p>
      )}
      <ErrorBox error={action.error} />
    </div>
  )
}

export function CheckoutBanner({ status, bookingId, session, onDismiss }) {
  // Only the cancelled flow needs the booking (and its event), to offer resume/cancel
  const booking = useFetch(async () => {
    if (status === 'success') return null
    const [data, event] = await Promise.all([
      api.get(`/bookings/${bookingId}`),
      api.get(`/bookings/${bookingId}/event`),
    ])
    return { ...data, event }
  }, [status, bookingId])

  return (
    <div className="flex items-start justify-between gap-2">
      <div className="flex-1">
        {status === 'success' ? (
          <Notice>
            Payment submitted for booking <span className="font-mono">{bookingId}</span>. It becomes CONFIRMED once
            Stripe's webhook arrives — check “Account”.
          </Notice>
        ) : (
          <>
            <ErrorBox
              error={{
                message: `Checkout was not completed for booking ${bookingId}. The seat stays reserved for you for 31 minutes from booking — resume payment, or cancel to release it.`,
              }}
            />
            <ErrorBox error={booking.error} />
            {booking.data && (
              <BookingActions booking={booking.data} event={booking.data.event} session={session} onChange={booking.reload} />
            )}
            {booking.data && booking.data.status !== 'PENDING' && (
              <p className="mt-2 text-sm text-fg-muted">This booking is now {booking.data.status}.</p>
            )}
          </>
        )}
      </div>
      <Button variant="secondary" onClick={onDismiss}>Dismiss</Button>
    </div>
  )
}

import { useState } from 'react'
import { Link } from 'react-router-dom'
import { referralApi } from '../api'
import { useFetch } from '../hooks'
import StatusTimeline from '../components/StatusTimeline'
import { Button, Card, Empty, Notice, Spinner } from '../components/ui'
import { formatDate, statusLabel } from '../utils'

/**
 * The person's own requests, newest first.
 *
 * Status is shown in plain language - "Waiting for review", not
 * PENDING. The enum name is the database's business; what someone
 * waiting to hear back needs is a sentence they can act on.
 */
export default function MyReferralsPage() {
  const { data: page, error, loading, reload } =
    useFetch(() => referralApi.mine({ size: 50 }), [])

  const [open, setOpen] = useState(null)
  const [actionError, setActionError] = useState(null)

  const withdraw = async (id) => {
    setActionError(null)
    try {
      await referralApi.withdraw(id)
      reload()
    } catch (err) {
      setActionError(err.message)
    }
  }

  if (loading) return <main className="page"><Spinner label="Loading your requests" /></main>

  const referrals = page?.content ?? []

  return (
    <main className="page">
      <div className="page__head">
        <h1>My requests</h1>
        <p>Every request you have sent, and where each one stands.</p>
      </div>

      {error && <Notice tone="error">{error.message}</Notice>}
      {actionError && <Notice tone="error">{actionError}</Notice>}

      {referrals.length === 0 ? (
        <Empty title="You have not sent any requests yet">
          <p>
            When you find a provider who fits, send them a request from
            their page. It will show up here with its full history.
          </p>
          <Link to="/">Search the directory</Link>
        </Empty>
      ) : (
        <div className="stack">
          {referrals.map((r) => (
            <Card key={r.id}>
              <div className="row" style={{ justifyContent: 'space-between' }}>
                <div>
                  <h3 style={{ marginBottom: 'var(--s-1)' }}>
                    <Link to={`/providers/${r.providerId}`}>{r.providerName}</Link>
                  </h3>
                  <div className="muted small">{r.organization}</div>
                </div>

                <div className={`status status--${r.status.toLowerCase()}`}>
                  {statusLabel(r.status)}
                </div>
              </div>

              <div className="small muted" style={{ marginTop: 'var(--s-3)' }}>
                Sent {formatDate(r.submittedAt)}
                {r.resolvedAt && ` · Answered ${formatDate(r.resolvedAt)}`}
                {r.resolvedBy && ` by ${r.resolvedBy}`}
              </div>

              {r.message && (
                <p className="small" style={{ marginTop: 'var(--s-3)' }}>
                  “{r.message}”
                </p>
              )}

              <div className="row" style={{ marginTop: 'var(--s-4)' }}>
                <Button
                  variant="quiet"
                  size="small"
                  onClick={() => setOpen(open === r.id ? null : r.id)}
                  aria-expanded={open === r.id}
                >
                  {open === r.id ? 'Hide history' : 'Show history'}
                </Button>

                {r.status === 'PENDING' && (
                  <Button variant="quiet" size="small" onClick={() => withdraw(r.id)}>
                    Withdraw
                  </Button>
                )}
              </div>

              {open === r.id && (
                <div style={{ marginTop: 'var(--s-4)' }}>
                  <StatusTimeline history={r.history} />
                </div>
              )}
            </Card>
          ))}
        </div>
      )}
    </main>
  )
}

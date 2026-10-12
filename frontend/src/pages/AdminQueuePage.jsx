import { useCallback, useState } from 'react'
import { Link } from 'react-router-dom'
import { referralApi } from '../api'
import { useFetch } from '../hooks'
import Modal from '../components/Modal'
import StatusTimeline from '../components/StatusTimeline'
import { Button, Card, Empty, Field, Notice, Spinner, Textarea } from '../components/ui'
import { formatDateTime, statusLabel } from '../utils'

/**
 * The navigator's queue - the review side of the workflow.
 *
 * OLDEST FIRST, deliberately. A newest-first queue guarantees that the
 * longest-waiting request is the one nobody ever sees.
 *
 * THE CASE WORTH READING: a navigator can click Accept and get back
 * WAITLISTED. The backend re-reads the provider under a write lock
 * inside the transaction, and if the last slot has gone since the
 * request was submitted it waitlists rather than overbooks. The
 * response reports the status that was ACTUALLY applied, and this
 * screen says so plainly instead of showing a success message for
 * something that did not happen.
 */
export default function AdminQueuePage() {
  const { data: page, error, loading, reload } =
    useFetch(() => referralApi.queue({ status: 'PENDING', size: 50 }), [])

  const [deciding, setDeciding] = useState(null)   // the referral under review
  const [note, setNote] = useState('')
  const [busy, setBusy] = useState(false)
  const [outcome, setOutcome] = useState(null)
  const [actionError, setActionError] = useState(null)

  const close = useCallback(() => {
    setDeciding(null)
    setNote('')
    setActionError(null)
  }, [])

  const decide = async (status) => {
    setActionError(null)
    setBusy(true)
    try {
      const result = await referralApi.decide(deciding.id, {
        status,
        note: note.trim() || null
      })

      setOutcome({
        requested: status,
        applied: result.status,
        provider: result.providerName,
        client: result.clientName
      })
      close()
      reload()
    } catch (err) {
      setActionError(err.message)
    } finally {
      setBusy(false)
    }
  }

  if (loading) return <main className="page"><Spinner label="Loading the queue" /></main>

  const referrals = page?.content ?? []

  return (
    <main className="page">
      <div className="page__head">
        <h1>Referral queue</h1>
        <p>
          Requests awaiting review, oldest first. {referrals.length} waiting.
        </p>
      </div>

      {error && <Notice tone="error">{error.message}</Notice>}

      {/* The system did something other than what was asked. Say so. */}
      {outcome && (
        <Notice tone={outcome.applied === outcome.requested ? 'ok' : 'waitlist'}>
          {outcome.applied === outcome.requested ? (
            <>
              {outcome.client}'s request to {outcome.provider} is now{' '}
              <strong>{statusLabel(outcome.applied)}</strong>.
            </>
          ) : (
            <>
              You asked for <strong>{statusLabel(outcome.requested)}</strong>,
              but {outcome.provider} had no open slots at review time, so{' '}
              {outcome.client}'s request was set to{' '}
              <strong>{statusLabel(outcome.applied)}</strong> instead.
              Accepting it would have promised an intake that does not exist.
            </>
          )}
        </Notice>
      )}

      {referrals.length === 0 ? (
        <Empty title="The queue is clear">
          <p>Nothing is waiting for review.</p>
        </Empty>
      ) : (
        <div className="stack">
          {referrals.map((r, i) => (
            <Card key={r.id}>
              <div className="row" style={{ justifyContent: 'space-between' }}>
                <div>
                  <h3 style={{ marginBottom: 'var(--s-1)' }}>
                    {r.clientName} → <Link to={`/providers/${r.providerId}`}>{r.providerName}</Link>
                  </h3>
                  <div className="muted small">
                    {r.organization} · Waiting since {formatDateTime(r.submittedAt)}
                    {i === 0 && referrals.length > 1 && ' · oldest'}
                  </div>
                </div>

                <Button size="small" onClick={() => { setDeciding(r); setOutcome(null) }}>
                  Review
                </Button>
              </div>

              {r.message && (
                <p className="small" style={{ marginTop: 'var(--s-3)' }}>
                  “{r.message}”
                </p>
              )}

              <div className="small muted">
                Contact by {r.preferredContact.toLowerCase()}
              </div>
            </Card>
          ))}
        </div>
      )}

      {deciding && (
        <Modal title={`Review ${deciding.clientName}'s request`} onClose={close}>
          <p className="small muted">
            {deciding.providerName} · {deciding.organization}
          </p>

          {deciding.message && <p className="small">“{deciding.message}”</p>}

          {actionError && <Notice tone="error">{actionError}</Notice>}

          <Field
            label="Note"
            id="decision-note"
            hint="Recorded in the referral's history alongside your name."
          >
            <Textarea
              id="decision-note"
              value={note}
              onChange={(e) => setNote(e.target.value)}
              maxLength={255}
              placeholder="Open slot confirmed; intake scheduled for Thursday."
            />
          </Field>

          <div className="row">
            <Button disabled={busy} onClick={() => decide('ACCEPTED')}>
              Accept
            </Button>
            <Button variant="quiet" disabled={busy} onClick={() => decide('WAITLISTED')}>
              Waitlist
            </Button>
            <Button variant="danger" disabled={busy} onClick={() => decide('DECLINED')}>
              Decline
            </Button>
            <Button variant="quiet" disabled={busy} onClick={close}>
              Cancel
            </Button>
          </div>

          <p className="small muted" style={{ marginTop: 'var(--s-4)' }}>
            Accepting consumes one of the provider's open slots. If none
            are left when you submit, the request is waitlisted instead.
          </p>

          <div style={{ marginTop: 'var(--s-5)' }}>
            <h3>History</h3>
            <StatusTimeline history={deciding.history} />
          </div>
        </Modal>
      )}
    </main>
  )
}

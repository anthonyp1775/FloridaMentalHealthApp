import { useCallback, useState } from 'react'
import { Link, useNavigate, useParams } from 'react-router-dom'
import { providerApi } from '../api'
import { useFetch } from '../hooks'
import ReferralForm from '../components/ReferralForm'
import { Button, Card, Notice, Spinner, Tag } from '../components/ui'
import {
  availability, availabilityLabel, canPrescribe,
  credentialLabel, formatWait, orgTypeLabel
} from '../utils'

/**
 * Everything known about one provider, and the place a referral starts.
 *
 * Credential is given more room than it usually gets. It is what
 * determines whether this person can prescribe medication, and it is
 * the thing people most often get wrong before they book.
 */
export default function ProviderDetailPage() {
  const { id } = useParams()
  const navigate = useNavigate()

  const { data: provider, error, loading, reload } =
    useFetch(() => providerApi.get(id), [id])

  const [submitted, setSubmitted] = useState(null)
  const [saveState, setSaveState] = useState(null)

  const onSubmitted = useCallback((referral) => {
    setSubmitted(referral)
    reload()   // capacity may have moved
  }, [reload])

  const save = async () => {
    setSaveState(null)
    try {
      await providerApi.save(id, null)
      setSaveState({ tone: 'ok', message: 'Saved to your list.' })
    } catch (err) {
      setSaveState({ tone: 'error', message: err.message })
    }
  }

  if (loading) return <main className="page"><Spinner label="Loading provider" /></main>

  if (error) {
    return (
      <main className="page">
        <Notice tone="error">{error.message}</Notice>
        <Button variant="quiet" onClick={() => navigate('/')}>Back to search</Button>
      </main>
    )
  }

  const state = availability(provider)

  return (
    <main className="page">
      <p className="small">
        <Link to="/">← Back to search</Link>
      </p>

      <div className="page__head">
        <h1>{provider.firstName} {provider.lastName}</h1>
        <p>
          {credentialLabel(provider.credential)}
          {provider.licenseNumber && ` · License ${provider.licenseNumber}`}
          {provider.yearsExperience > 0 &&
            ` · ${provider.yearsExperience} years in practice`}
        </p>
      </div>

      <div className={`provider provider--${state}`} style={{ cursor: 'default' }}>
        <div className={`provider__status provider__status--${state}`}>
          {availabilityLabel(provider)}
        </div>
        <div className="provider__wait small">
          {formatWait(provider.typicalWaitDays)}
          {provider.waitlistCount > 0 &&
            ` · ${provider.waitlistCount} people on the waitlist`}
        </div>
      </div>

      <div className="detail">
        <div className="stack">
          {provider.bio && (
            <Card>
              <h2>About</h2>
              <p style={{ marginBottom: 0 }}>{provider.bio}</p>
            </Card>
          )}

          <Card>
            <h2>Where</h2>
            <p>
              {provider.organization}<br />
              <span className="muted">{orgTypeLabel(provider.orgType)}</span><br />
              {provider.city}, {provider.county} County
            </p>
            <div className="tags">
              {provider.offersInPerson && <Tag>In person</Tag>}
              {provider.offersTelehealth && <Tag>Telehealth</Tag>}
              {canPrescribe(provider.credential) &&
                <Tag variant="prescriber">Can prescribe medication</Tag>}
            </div>
          </Card>

          <Card>
            <h2>Accepted coverage</h2>
            <div className="tags">
              {provider.insurancePlans.length === 0
                ? <span className="muted small">No plans listed.</span>
                : provider.insurancePlans.map((p) => <Tag key={p}>{p}</Tag>)}
            </div>
          </Card>
        </div>

        <aside className="stack">
          <Card>
            <h2>Request a referral</h2>

            {submitted ? (
              <>
                <Notice tone="ok">
                  Request sent. A navigator will review it.
                </Notice>
                <Link to="/referrals">Track it under My requests →</Link>
              </>
            ) : (
              <>
                {state === 'closed' && (
                  <Notice tone="waitlist">
                    This provider is not accepting new clients. You can
                    still send a request, but it will most likely be
                    waitlisted.
                  </Notice>
                )}
                {state === 'waitlist' && (
                  <Notice tone="waitlist">
                    No open slots right now. A request here joins the
                    waitlist.
                  </Notice>
                )}
                <ReferralForm provider={provider} onSubmitted={onSubmitted} />
              </>
            )}
          </Card>

          <Card>
            <h2>Keep for later</h2>
            {saveState && <Notice tone={saveState.tone}>{saveState.message}</Notice>}
            <Button variant="quiet" block onClick={save}>
              Save to my list
            </Button>
          </Card>
        </aside>
      </div>
    </main>
  )
}

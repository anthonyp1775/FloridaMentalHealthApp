import { useMemo, useState } from 'react'
import { reportApi } from '../api'
import { useFetch } from '../hooks'
import Table from '../components/Table'
import { Button, Card, Field, Input, Notice, Spinner } from '../components/ui'
import { planTypeLabel } from '../utils'

/**
 * Reporting for navigators.
 *
 * THE ACCESS GAP is the number worth looking at. It is not a count of
 * rows - it is the difference between how many clinicians take
 * commercial insurance and how many take Medicaid. The bars below are
 * drawn relative to the largest value, so that gap is visible at a
 * glance rather than something you have to work out from two numbers.
 *
 * It falls out of data the directory already holds, which is the point:
 * no separate survey, no extra form for anyone to fill in.
 */
export default function ReportsPage() {
  const [range, setRange] = useState({ from: '', to: '' })

  const rangeKey = `${range.from}|${range.to}`

  const referrals = useFetch(
    () => reportApi.referrals({
      from: range.from || undefined,
      to: range.to || undefined
    }),
    [rangeKey]
  )

  const accessGap = useFetch(() => reportApi.accessGap(), [])
  const capacity = useFetch(() => reportApi.countyCapacity(), [])

  const maxAccepting = useMemo(
    () => Math.max(1, ...(accessGap.data ?? []).map((r) => r.acceptingProviders)),
    [accessGap.data]
  )

  const capacityColumns = [
    { key: 'county', label: 'County' },
    { key: 'region', label: 'Region' },
    { key: 'providers', label: 'Providers' },
    { key: 'openSlots', label: 'Open slots' },
    { key: 'waitlisted', label: 'Waitlisted' }
  ]

  return (
    <main className="page">
      <div className="page__head">
        <h1>Reports</h1>
        <p>Where the demand is, and where the coverage is not.</p>
      </div>

      {/* --- access gap: the headline number --- */}
      <Card>
        <h2>Who takes which coverage</h2>
        <p className="muted small">
          Providers currently accepting new clients, by plan type.
        </p>

        {accessGap.loading && <Spinner />}
        {accessGap.error && <Notice tone="error">{accessGap.error.message}</Notice>}

        {accessGap.data && (
          <ul className="gap">
            {accessGap.data.map((row) => (
              <li key={row.planType}>
                <span className="gap__label">{planTypeLabel(row.planType)}</span>
                <span className="gap__bar">
                  <span
                    className="gap__fill"
                    style={{ width: `${(row.acceptingProviders / maxAccepting) * 100}%` }}
                  />
                </span>
                <span className="gap__value">{row.acceptingProviders}</span>
              </li>
            ))}
          </ul>
        )}
      </Card>

      {/* --- referral volume --- */}
      <Card style={{ marginTop: 'var(--s-5)' }}>
        <h2>Referral volume</h2>

        <div className="row" style={{ alignItems: 'flex-end' }}>
          <Field label="From" id="from">
            <Input
              id="from"
              type="date"
              value={range.from}
              onChange={(e) => setRange((r) => ({ ...r, from: e.target.value }))}
            />
          </Field>
          <Field label="To" id="to">
            <Input
              id="to"
              type="date"
              value={range.to}
              onChange={(e) => setRange((r) => ({ ...r, to: e.target.value }))}
            />
          </Field>
          <Field>
            <Button variant="quiet" onClick={() => setRange({ from: '', to: '' })}>
              All time
            </Button>
          </Field>
        </div>

        {referrals.loading && <Spinner />}
        {referrals.error && <Notice tone="error">{referrals.error.message}</Notice>}

        {referrals.data && (
          <div className="figures">
            <Figure label="Total" value={referrals.data.total} />
            <Figure label="Waiting for review" value={referrals.data.pending} />
            <Figure label="Accepted" value={referrals.data.accepted} />
            <Figure label="Waitlisted" value={referrals.data.waitlisted} />
            <Figure label="Declined" value={referrals.data.declined} />
            <Figure label="Withdrawn" value={referrals.data.withdrawn} />
          </div>
        )}
      </Card>

      {/* --- county capacity --- */}
      <Card style={{ marginTop: 'var(--s-5)', padding: 0 }}>
        <div style={{ padding: 'var(--s-5)', paddingBottom: 0 }}>
          <h2>Capacity by county</h2>
          <p className="muted small">Only counties with at least one provider appear.</p>
        </div>

        {capacity.loading && <div style={{ padding: 'var(--s-5)' }}><Spinner /></div>}
        {capacity.error && (
          <div style={{ padding: 'var(--s-5)' }}>
            <Notice tone="error">{capacity.error.message}</Notice>
          </div>
        )}

        {capacity.data && (
          <div style={{ overflowX: 'auto' }}>
            <Table
              columns={capacityColumns}
              rows={capacity.data}
              keyField="county"
              caption="Providers, open slots and waitlist totals by county"
            />
          </div>
        )}
      </Card>
    </main>
  )
}

function Figure({ label, value }) {
  return (
    <div className="figure">
      <div className="figure__value">{value}</div>
      <div className="figure__label">{label}</div>
    </div>
  )
}

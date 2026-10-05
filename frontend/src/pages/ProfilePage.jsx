import { useEffect, useMemo, useState } from 'react'
import { catalogApi, profileApi } from '../api'
import { useFetch } from '../hooks'
import { Button, Card, Checkbox, Field, Notice, Select, Spinner } from '../components/ui'

/**
 * The person's own stated preferences.
 *
 * NOTHING CLINICAL. County, language, coverage, how to be contacted,
 * whether telehealth works for them - all self-described, all optional.
 * The app never asks for a diagnosis, so it never has to protect one
 * (ADR-0004).
 *
 * One wrinkle worth knowing about: GET /api/profile returns NAMES
 * ("Miami-Dade", "Spanish"), not ids, because that is what a display
 * needs. A form needs ids. Rather than change a working API this close
 * to the end, the form resolves names back to ids against the catalog -
 * see `initialForm` below. If this API were being designed again it
 * would return both.
 */
export default function ProfilePage() {
  const profile = useFetch(() => profileApi.get(), [])

  const catalog = useFetch(
    () => Promise.all([
      catalogApi.counties(),
      catalogApi.languages(),
      catalogApi.insurancePlans()
    ]).then(([counties, languages, insurancePlans]) =>
      ({ counties, languages, insurancePlans })),
    []
  )

  const [form, setForm] = useState(null)
  const [saved, setSaved] = useState(false)
  const [saveError, setSaveError] = useState(null)
  const [busy, setBusy] = useState(false)

  /** Match a stored name back to its catalog id. '' when not set. */
  const idForName = (list, name) =>
    (list?.find((item) => item.name === name)?.id ?? '').toString()

  const initialForm = useMemo(() => {
    if (!profile.data || !catalog.data) return null
    const p = profile.data
    return {
      phone: p.phone ?? '',
      preferredCountyId: idForName(catalog.data.counties, p.preferredCounty),
      preferredLanguageId: idForName(catalog.data.languages, p.preferredLanguage),
      insurancePlanId: idForName(catalog.data.insurancePlans, p.insurancePlan),
      prefersTelehealth: !!p.prefersTelehealth,
      contactPreference: p.contactPreference || 'EMAIL'
    }
  }, [profile.data, catalog.data])

  useEffect(() => { if (initialForm) setForm(initialForm) }, [initialForm])

  const set = (field) => (e) => {
    setSaved(false)
    setForm((prev) => ({ ...prev, [field]: e.target.value }))
  }

  const submit = async (e) => {
    e.preventDefault()
    setSaveError(null)
    setSaved(false)
    setBusy(true)
    try {
      const updated = await profileApi.update({
        phone: form.phone || null,
        // '' means "not set", which the API expects as null.
        preferredCountyId: form.preferredCountyId || null,
        preferredLanguageId: form.preferredLanguageId || null,
        insurancePlanId: form.insurancePlanId || null,
        prefersTelehealth: form.prefersTelehealth,
        contactPreference: form.contactPreference
      })
      profile.setData(updated)
      setSaved(true)
    } catch (err) {
      setSaveError(err)
    } finally {
      setBusy(false)
    }
  }

  if (profile.loading || catalog.loading || !form) {
    return <main className="page page--narrow"><Spinner label="Loading your profile" /></main>
  }

  return (
    <main className="page page--narrow">
      <div className="page__head">
        <h1>Your preferences</h1>
        <p>
          Used to pre-fill your searches. All optional, and none of it is
          shared with providers until you send a request.
        </p>
      </div>

      {profile.error && <Notice tone="error">{profile.error.message}</Notice>}
      {saveError && <Notice tone="error">{saveError.message}</Notice>}
      {saved && <Notice tone="ok">Preferences saved.</Notice>}

      <Card>
        <p className="small muted">
          {profile.data.fullName} · {profile.data.email}
        </p>

        <form onSubmit={submit} noValidate>
          <Field
            label="Phone"
            id="phone"
            hint="Only used if you ask to be contacted by phone or text."
            error={saveError?.fieldErrors?.phone}
          >
            <input
              id="phone"
              className="input"
              type="tel"
              value={form.phone}
              onChange={set('phone')}
              autoComplete="tel"
            />
          </Field>

          <Field label="County you'd like care in" id="county">
            <Select
              id="county"
              value={form.preferredCountyId}
              onChange={set('preferredCountyId')}
              options={catalog.data.counties}
              anyLabel="No preference"
            />
          </Field>

          <Field label="Language you'd prefer" id="language">
            <Select
              id="language"
              value={form.preferredLanguageId}
              onChange={set('preferredLanguageId')}
              options={catalog.data.languages}
              anyLabel="No preference"
            />
          </Field>

          <Field label="Your coverage" id="insurance">
            <Select
              id="insurance"
              value={form.insurancePlanId}
              onChange={set('insurancePlanId')}
              options={catalog.data.insurancePlans}
              anyLabel="Not listed / not sure"
            />
          </Field>

          <Field label="How to contact you" id="contact">
            <select
              id="contact"
              className="select"
              value={form.contactPreference}
              onChange={set('contactPreference')}
            >
              <option value="EMAIL">Email</option>
              <option value="PHONE">Phone call</option>
              <option value="TEXT">Text message</option>
            </select>
          </Field>

          <Field>
            <Checkbox
              id="telehealth"
              label="I'd rather have appointments by video"
              checked={form.prefersTelehealth}
              onChange={() => {
                setSaved(false)
                setForm((p) => ({ ...p, prefersTelehealth: !p.prefersTelehealth }))
              }}
            />
          </Field>

          <Button type="submit" disabled={busy}>
            {busy ? 'Saving...' : 'Save preferences'}
          </Button>
        </form>
      </Card>
    </main>
  )
}

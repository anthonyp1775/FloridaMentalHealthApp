import { useState } from 'react'
import { referralApi } from '../api'
import { Button, Field, Notice, Textarea } from './ui'

const CONTACT_OPTIONS = [
  { id: 'EMAIL', name: 'Email' },
  { id: 'PHONE', name: 'Phone call' },
  { id: 'TEXT', name: 'Text message' }
]

/**
 * Submitting a referral request to one provider.
 *
 * The message box is free text and the placeholder deliberately asks
 * about scheduling, not symptoms. Nothing here asks a clinical
 * question, and nothing here is required - someone can send a request
 * with no message at all.
 */
export default function ReferralForm({ provider, onSubmitted }) {
  const [message, setMessage] = useState('')
  const [preferredContact, setPreferredContact] = useState('EMAIL')
  const [error, setError] = useState(null)
  const [busy, setBusy] = useState(false)

  const submit = async (e) => {
    e.preventDefault()
    setError(null)
    setBusy(true)
    try {
      const referral = await referralApi.submit({
        providerId: provider.id,
        message: message.trim() || null,
        preferredContact
      })
      onSubmitted(referral)
    } catch (err) {
      setError(err)
    } finally {
      setBusy(false)
    }
  }

  return (
    <form onSubmit={submit} noValidate>
      {error && <Notice tone="error">{error.message}</Notice>}

      <Field
        label="Anything they should know?"
        id="referral-message"
        hint="Optional. Scheduling, how to reach you, what you are looking for."
      >
        <Textarea
          id="referral-message"
          value={message}
          onChange={(e) => setMessage(e.target.value)}
          maxLength={2000}
          placeholder="Weekday evenings work best for me."
        />
      </Field>

      <Field label="How should they contact you?" id="referral-contact">
        <select
          id="referral-contact"
          className="select"
          value={preferredContact}
          onChange={(e) => setPreferredContact(e.target.value)}
        >
          {CONTACT_OPTIONS.map((o) => (
            <option key={o.id} value={o.id}>{o.name}</option>
          ))}
        </select>
      </Field>

      <p className="small muted">
        A navigator reviews requests in the order they arrive. You will
        see the outcome under "My requests".
      </p>

      <Button type="submit" disabled={busy}>
        {busy ? 'Sending...' : 'Send request'}
      </Button>
    </form>
  )
}

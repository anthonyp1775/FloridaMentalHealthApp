/** Credentials that can prescribe medication. */
export const PRESCRIBERS = ['PSYCHIATRIST', 'PSYCHIATRIC_ARNP']

/**
 * Users should not have to decode acronyms to find out whether someone
 * can prescribe. Spell the credential out wherever it is shown.
 */
const CREDENTIAL_LABELS = {
  PSYCHIATRIST:      'Psychiatrist',
  PSYCHIATRIC_ARNP:  'Psychiatric nurse practitioner',
  PSYCHOLOGIST:      'Psychologist',
  LMHC:              'Mental health counselor',
  LCSW:              'Clinical social worker',
  LMFT:              'Marriage & family therapist',
  REGISTERED_INTERN: 'Registered intern (supervised)',
  CAP:               'Certified addiction professional',
  PEER_SPECIALIST:   'Peer support specialist'
}

export const credentialLabel = (c) => CREDENTIAL_LABELS[c] || c

export const canPrescribe = (c) => PRESCRIBERS.includes(c)

const STATUS_LABELS = {
  PENDING:    'Waiting for review',
  ACCEPTED:   'Accepted',
  WAITLISTED: 'On the waitlist',
  DECLINED:   'Not accepted',
  WITHDRAWN:  'Withdrawn',
  CLOSED:     'Closed'
}

export const statusLabel = (s) => STATUS_LABELS[s] || s

const ORG_TYPE_LABELS = {
  PRIVATE_PRACTICE: 'Private practice',
  GROUP_PRACTICE: 'Group practice',
  COMMUNITY_MENTAL_HEALTH_CENTER: 'Community mental health center',
  HOSPITAL: 'Hospital',
  CRISIS_STABILIZATION_UNIT: 'Crisis stabilization unit',
  RESIDENTIAL: 'Residential program',
  FQHC: 'Community health center',
  TELEHEALTH_ONLY: 'Telehealth only',
  NONPROFIT: 'Nonprofit'
}

export const orgTypeLabel = (t) => ORG_TYPE_LABELS[t] || t

const PLAN_TYPE_LABELS = {
  MEDICAID: 'Medicaid',
  MEDICARE: 'Medicare',
  COMMERCIAL: 'Commercial insurance',
  MARKETPLACE: 'Marketplace plan',
  SLIDING_SCALE: 'Sliding scale',
  SELF_PAY: 'Self-pay'
}

export const planTypeLabel = (t) => PLAN_TYPE_LABELS[t] || t

/** Plain date, no clock time - nobody needs the seconds. */
export function formatDate(iso) {
  if (!iso) return ''
  return new Date(iso).toLocaleDateString(undefined, {
    year: 'numeric', month: 'short', day: 'numeric'
  })
}

export function formatDateTime(iso) {
  if (!iso) return ''
  return new Date(iso).toLocaleString(undefined, {
    month: 'short', day: 'numeric', hour: 'numeric', minute: '2-digit'
  })
}

/**
 * "about 2 weeks" reads better than "14 days" when someone is deciding
 * whether they can wait.
 */
export function formatWait(days) {
  if (days == null) return 'Wait time not listed'
  if (days <= 3)  return 'Usually within a few days'
  if (days <= 10) return 'Usually about a week'
  if (days <= 21) return 'Usually about two weeks'
  if (days <= 45) return 'Usually about a month'
  return 'Usually over a month'
}

/** available | waitlist | closed - used for the card's left edge. */
export function availability(provider) {
  if (provider.acceptingNewClients && provider.openSlots > 0) return 'available'
  if (provider.acceptingNewClients) return 'waitlist'
  return 'closed'
}

export function availabilityLabel(provider) {
  const state = availability(provider)
  if (state === 'available') {
    return provider.openSlots === 1
      ? 'Accepting new clients - 1 opening'
      : `Accepting new clients - ${provider.openSlots} openings`
  }
  if (state === 'waitlist') return 'Waitlist only'
  return 'Not accepting new clients'
}

/**
 * Statewide Resources - the one page in this application whose content
 * is REAL, not sample data.
 *
 * WHY IT EXISTS: the provider directory is synthetic (every organization
 * is prefixed "Example"). Someone who opens this app and actually needs
 * help should still find something true. These are national and Florida
 * resources that do not go stale the way clinic capacity does.
 *
 * VERIFIED 2026-10-04 against each organization's own site. Where a
 * service lists a text option as well as a phone number, both are here:
 * for some people texting is the only reachable option, and for some
 * situations it is the only safe one.
 *
 * Re-check before any future presentation. This is the one place in the
 * project where a stale value could matter to a person rather than to a
 * grade.
 *
 * This page is public on purpose. Someone who needs 988 should not have
 * to create an account first, so it sits outside ProtectedRoute.
 */
export const CRISIS_RESOURCES = [
  {
    name: '988 Suicide & Crisis Lifeline',
    contact: 'Call or text 988',
    tel: '988',
    sms: '988',
    url: 'https://988lifeline.org',
    note: 'Free and confidential, 24/7/365. Chat at chat.988lifeline.org. '
        + 'Specialised access for Deaf and hard-of-hearing callers.'
  },
  {
    name: 'Crisis Text Line',
    contact: 'Text HOME to 741741',
    sms: '741741',
    url: 'https://www.crisistextline.org',
    note: 'Text-based crisis support, 24/7.'
  },
  {
    name: 'Veterans Crisis Line',
    contact: 'Dial 988, then press 1',
    tel: '988',
    sms: '838255',
    url: 'https://www.veteranscrisisline.net',
    note: 'For veterans, service members and their families. '
        + 'Text 838255, or chat online. 24/7 and confidential.'
  },
  {
    name: 'Emergency services',
    contact: 'Call 911',
    tel: '911',
    url: null,
    note: 'If someone is in immediate danger.'
  }
]

export const SUPPORT_RESOURCES = [
  {
    name: '211 Florida',
    contact: 'Dial 211',
    tel: '211',
    url: 'https://www.211.org',
    note: 'Local referrals for mental health, housing, food and utilities.'
  },
  {
    name: 'SAMHSA National Helpline',
    contact: '1-800-662-HELP (4357)',
    tel: '18006624357',
    url: 'https://www.samhsa.gov/find-help/helplines/national-helpline',
    note: 'Free, confidential treatment referral and information in English '
        + 'and Spanish, 24/7, 365 days a year.'
  },
  {
    name: 'FindTreatment.gov',
    contact: null,
    url: 'https://findtreatment.gov',
    note: "SAMHSA's searchable directory of licensed treatment facilities."
  },
  {
    name: 'NAMI Florida',
    contact: null,
    url: 'https://namiflorida.org',
    note: 'Education, support groups and advocacy through local affiliates.'
  },
  {
    name: 'NAMI HelpLine',
    contact: '1-800-950-NAMI (6264)',
    tel: '18009506264',
    url: 'https://www.nami.org/help',
    note: 'Information and referrals, Monday to Friday 10am-10pm ET, closed '
        + 'federal holidays. Text NAMI to 62640. Not a crisis line - for a '
        + 'crisis, use 988.'
  },
  {
    name: 'Florida DCF — Substance Abuse & Mental Health',
    contact: null,
    url: 'https://www.myflfamilies.com',
    note: 'State programs and the regional Managing Entity for your county.'
  },
  {
    name: 'Disaster Distress Helpline',
    contact: 'Call or text 1-800-985-5990',
    tel: '18009855990',
    sms: '18009855990',
    url: 'https://www.samhsa.gov/find-help/helplines/disaster-distress-helpline',
    note: 'Year-round crisis counselling after hurricanes and other '
        + 'disasters. Multilingual, 24/7.'
  },
  {
    name: 'The Trevor Project',
    contact: '1-866-488-7386',
    tel: '18664887386',
    url: 'https://www.thetrevorproject.org/get-help/',
    note: 'Crisis support for LGBTQ young people, 24/7. Text START to '
        + '678-678, or chat online. TTY 1-866-803-3699.'
  }
]

import axios from 'axios'

/**
 * One axios instance for the whole app.
 *
 * The interceptors below mean no component ever thinks about tokens or
 * about what an error looks like - they call a function and get data or
 * an Error with a readable message.
 */
const client = axios.create({
  baseURL: import.meta.env.VITE_API_BASE_URL || '/api',
  headers: { 'Content-Type': 'application/json' }
})

const TOKEN_KEY = 'fmh.token'

export const tokenStore = {
  get: () => localStorage.getItem(TOKEN_KEY),
  set: (t) => localStorage.setItem(TOKEN_KEY, t),
  clear: () => localStorage.removeItem(TOKEN_KEY)
}

// Attach the token to every outgoing request.
client.interceptors.request.use((config) => {
  const token = tokenStore.get()
  if (token) config.headers.Authorization = `Bearer ${token}`
  return config
})

/**
 * Normalize every failure into an Error carrying the API's own message.
 *
 * The backend returns one consistent shape - timestamp, status, error,
 * message, path - so this is the single place that has to know it.
 */
client.interceptors.response.use(
  (res) => res,
  (err) => {
    if (err.response?.status === 401) {
      // Token expired or rejected. Drop it so ProtectedRoute redirects.
      tokenStore.clear()
    }

    const data = err.response?.data
    const message =
      data?.message ||
      (err.code === 'ERR_NETWORK'
        ? 'Cannot reach the server. Is the backend running?'
        : 'Something went wrong.')

    const error = new Error(message)
    error.status = err.response?.status
    error.fieldErrors = data?.fieldErrors
    return Promise.reject(error)
  }
)

const unwrap = (p) => p.then((res) => res.data)

export const authApi = {
  login:    (body) => unwrap(client.post('/auth/login', body)),
  register: (body) => unwrap(client.post('/auth/register', body))
}

export const profileApi = {
  get:    () => unwrap(client.get('/profile')),
  update: (body) => unwrap(client.put('/profile', body))
}

export const catalogApi = {
  counties:       () => unwrap(client.get('/counties')),
  specialties:    () => unwrap(client.get('/specialties')),
  populations:    () => unwrap(client.get('/populations')),
  languages:      () => unwrap(client.get('/languages')),
  insurancePlans: () => unwrap(client.get('/insurance-plans')),
  organizations:  (params) => unwrap(client.get('/organizations', { params }))
}

export const providerApi = {
  search:   (params) => unwrap(client.get('/providers/search', { params })),
  get:      (id) => unwrap(client.get(`/providers/${id}`)),
  saved:    () => unwrap(client.get('/providers/saved')),
  save:     (id, note) => unwrap(client.post(`/providers/${id}/save`, { note })),
  unsave:   (id) => unwrap(client.delete(`/providers/${id}/save`)),
  setCapacity: (id, body) => unwrap(client.patch(`/providers/${id}/capacity`, body))
}

export const referralApi = {
  submit:   (body) => unwrap(client.post('/referrals', body)),
  mine:     (params) => unwrap(client.get('/referrals/mine', { params })),
  get:      (id) => unwrap(client.get(`/referrals/${id}`)),
  withdraw: (id) => unwrap(client.post(`/referrals/${id}/withdraw`)),
  queue:    (params) => unwrap(client.get('/referrals/queue', { params })),
  decide:   (id, body) => unwrap(client.post(`/referrals/${id}/decision`, body))
}

export const reportApi = {
  referrals:      (params) => unwrap(client.get('/reports/referrals', { params })),
  accessGap:      () => unwrap(client.get('/reports/access-gap')),
  countyCapacity: () => unwrap(client.get('/reports/county-capacity'))
}

export default client

import { useState } from 'react'
import { Link, Navigate, useLocation, useNavigate } from 'react-router-dom'
import { useAuth } from '../context/AuthContext'
import CrisisBanner from '../components/CrisisBanner'
import { Button, Field, Input, Notice } from '../components/ui'

/**
 * Sign in, or create an account.
 *
 * One page with two modes rather than two routes - the fields overlap
 * and someone who guessed wrong should not have to find a second page.
 *
 * Note what this form does NOT ask for: no date of birth, no diagnosis,
 * no insurance member number. An account needs a name, an email and a
 * password. Everything else is optional and lives on the profile page.
 */
export default function AuthPage() {
  const [mode, setMode] = useState('signin')
  const [form, setForm] = useState({
    firstName: '', lastName: '', email: '', password: ''
  })
  const [error, setError] = useState(null)
  const [busy, setBusy] = useState(false)

  const { login, register, isSignedIn } = useAuth()
  const navigate = useNavigate()
  const location = useLocation()

  const destination = location.state?.from || '/'

  /*
   * Already signed in - redirect by rendering <Navigate>, not by
   * calling navigate() during render. Routing is a side effect, and a
   * side effect in a render body fires twice under StrictMode.
   */
  if (isSignedIn) {
    return <Navigate to={destination} replace />
  }

  const set = (field) => (e) =>
    setForm((prev) => ({ ...prev, [field]: e.target.value }))

  const submit = async (e) => {
    e.preventDefault()
    setError(null)
    setBusy(true)
    try {
      if (mode === 'signin') {
        await login(form.email, form.password)
      } else {
        await register(form)
      }
      navigate(destination, { replace: true })
    } catch (err) {
      setError(err)
    } finally {
      setBusy(false)
    }
  }

  const fieldError = (name) => error?.fieldErrors?.[name]

  return (
    <div className="shell">
      <CrisisBanner />

      <main className="page page--narrow">
        <div className="page__head">
          <h1>Find Care Florida</h1>
          <p>
            A directory of mental health providers across all 67 Florida
            counties, with the one thing most directories leave out:
            whether they are taking new clients.
          </p>
        </div>

        <div className="card">
          <h2>{mode === 'signin' ? 'Sign in' : 'Create an account'}</h2>

          {error && <Notice tone="error">{error.message}</Notice>}

          <form onSubmit={submit} noValidate>
            {mode === 'register' && (
              <>
                <Field label="First name" id="firstName" error={fieldError('firstName')}>
                  <Input
                    id="firstName"
                    value={form.firstName}
                    onChange={set('firstName')}
                    autoComplete="given-name"
                    required
                  />
                </Field>

                <Field label="Last name" id="lastName" error={fieldError('lastName')}>
                  <Input
                    id="lastName"
                    value={form.lastName}
                    onChange={set('lastName')}
                    autoComplete="family-name"
                    required
                  />
                </Field>
              </>
            )}

            <Field label="Email" id="email" error={fieldError('email')}>
              <Input
                id="email"
                type="email"
                value={form.email}
                onChange={set('email')}
                autoComplete="email"
                required
              />
            </Field>

            <Field
              label="Password"
              id="password"
              error={fieldError('password')}
              hint={mode === 'register' ? 'At least 8 characters.' : undefined}
            >
              <Input
                id="password"
                type="password"
                value={form.password}
                onChange={set('password')}
                autoComplete={mode === 'signin' ? 'current-password' : 'new-password'}
                required
              />
            </Field>

            <Button type="submit" block disabled={busy}>
              {busy
                ? 'Working...'
                : mode === 'signin' ? 'Sign in' : 'Create account'}
            </Button>
          </form>

          <p className="small muted" style={{ marginTop: 'var(--s-4)', marginBottom: 0 }}>
            {mode === 'signin' ? (
              <>
                No account yet?{' '}
                <Button
                  variant="quiet"
                  size="small"
                  onClick={() => { setMode('register'); setError(null) }}
                >
                  Create one
                </Button>
              </>
            ) : (
              <>
                Already have an account?{' '}
                <Button
                  variant="quiet"
                  size="small"
                  onClick={() => { setMode('signin'); setError(null) }}
                >
                  Sign in
                </Button>
              </>
            )}
          </p>
        </div>

        <p className="sample-note">
          You do not need an account to see crisis and statewide help.{' '}
          <Link to="/resources">Go to resources</Link>.
        </p>
      </main>
    </div>
  )
}

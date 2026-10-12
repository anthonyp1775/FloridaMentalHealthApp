import { useCallback, useMemo, useState } from 'react'
import { Link } from 'react-router-dom'
import { providerApi } from '../api'
import { useDebounce, useFetch } from '../hooks'
import Modal from '../components/Modal'
import Table from '../components/Table'
import { Button, Checkbox, Field, Input, Notice, Spinner, Textarea } from '../components/ui'
import { availability, availabilityLabel, credentialLabel } from '../utils'

/**
 * Directory management: adjusting who is actually taking clients.
 *
 * THE INVARIANT THIS SCREEN EXISTS TO SHOW: a provider cannot be
 * "accepting new clients" with zero open slots. The API rejects that
 * combination with a 400 on every write path, because a directory that
 * says someone is available when they are not is worse than no
 * directory at all.
 *
 * The form lets you try it. The error you get back is the backend's own
 * message, not a copy of the rule re-implemented here - one rule, one
 * place.
 */
export default function AdminProvidersPage() {
  const [query, setQuery] = useState('')
  const debounced = useDebounce(query)

  const { data: page, error, loading, reload } =
    useFetch(() => providerApi.search({ size: 100 }), [])

  const [editing, setEditing] = useState(null)
  const [form, setForm] = useState({ openSlots: 0, acceptingNewClients: false, note: '' })
  const [busy, setBusy] = useState(false)
  const [saveError, setSaveError] = useState(null)
  const [saved, setSaved] = useState(null)

  const providers = page?.content ?? []

  /* Filtering in the browser: the whole directory is 35 rows and
     already loaded, so a round trip per keystroke would be slower and
     no more correct. */
  const visible = useMemo(() => {
    const q = debounced.trim().toLowerCase()
    if (!q) return providers
    return providers.filter((p) =>
      p.fullName.toLowerCase().includes(q) ||
      p.organization.toLowerCase().includes(q) ||
      p.county.toLowerCase().includes(q))
  }, [providers, debounced])

  const openEditor = useCallback((provider) => {
    setSaved(null)
    setSaveError(null)
    setEditing(provider)
    setForm({
      openSlots: provider.openSlots,
      acceptingNewClients: provider.acceptingNewClients,
      note: ''
    })
  }, [])

  const close = useCallback(() => {
    setEditing(null)
    setSaveError(null)
  }, [])

  const save = async (e) => {
    e.preventDefault()
    setSaveError(null)
    setBusy(true)
    try {
      const updated = await providerApi.setCapacity(editing.id, {
        openSlots: Number(form.openSlots),
        acceptingNewClients: form.acceptingNewClients,
        note: form.note.trim() || null
      })
      setSaved(`${updated.firstName} ${updated.lastName} updated.`)
      close()
      reload()
    } catch (err) {
      setSaveError(err.message)
    } finally {
      setBusy(false)
    }
  }

  if (loading) return <main className="page"><Spinner label="Loading the directory" /></main>

  const columns = [
    {
      key: 'fullName',
      label: 'Provider',
      render: (p) => (
        <>
          <Link to={`/providers/${p.id}`}>{p.fullName}</Link>
          <div className="muted">{credentialLabel(p.credential)}</div>
        </>
      )
    },
    { key: 'organization', label: 'Organization' },
    { key: 'county', label: 'County' },
    { key: 'openSlots', label: 'Open slots' },
    {
      key: 'status',
      label: 'Availability',
      render: (p) => (
        <span className={`provider__status provider__status--${availability(p)}`}>
          {availabilityLabel(p)}
        </span>
      )
    },
    {
      key: 'action',
      label: '',
      render: (p) => (
        <Button variant="quiet" size="small" onClick={() => openEditor(p)}>
          Edit capacity
        </Button>
      )
    }
  ]

  return (
    <main className="page">
      <div className="page__head">
        <h1>Directory</h1>
        <p>{providers.length} providers. Keeping capacity current is what makes search worth using.</p>
      </div>

      {error && <Notice tone="error">{error.message}</Notice>}
      {saved && <Notice tone="ok">{saved}</Notice>}

      <Field label="Find a provider" id="dir-search">
        <Input
          id="dir-search"
          type="search"
          value={query}
          onChange={(e) => setQuery(e.target.value)}
          placeholder="Name, organization or county"
        />
      </Field>

      {visible.length === 0 ? (
        <p className="muted">No provider matches “{debounced}”.</p>
      ) : (
        <div className="card" style={{ padding: 0, overflowX: 'auto' }}>
          <Table columns={columns} rows={visible} caption="Providers and current capacity" />
        </div>
      )}

      {editing && (
        <Modal title={`Capacity for ${editing.fullName}`} onClose={close}>
          {saveError && <Notice tone="error">{saveError}</Notice>}

          <form onSubmit={save} noValidate>
            <Field
              label="Open intake slots"
              id="open-slots"
              hint="How many new clients this provider can take right now."
            >
              <Input
                id="open-slots"
                type="number"
                min="0"
                value={form.openSlots}
                onChange={(e) => setForm((p) => ({ ...p, openSlots: e.target.value }))}
              />
            </Field>

            <Field>
              <Checkbox
                id="accepting"
                label="Accepting new clients"
                checked={form.acceptingNewClients}
                onChange={() => setForm((p) =>
                  ({ ...p, acceptingNewClients: !p.acceptingNewClients }))}
              />
            </Field>

            <Field label="Note" id="capacity-note" hint="Optional. Why this changed.">
              <Textarea
                id="capacity-note"
                value={form.note}
                onChange={(e) => setForm((p) => ({ ...p, note: e.target.value }))}
                maxLength={255}
              />
            </Field>

            <p className="small muted">
              Zero slots and "accepting new clients" cannot both be true.
              The API rejects that combination.
            </p>

            <div className="row">
              <Button type="submit" disabled={busy}>
                {busy ? 'Saving...' : 'Save capacity'}
              </Button>
              <Button variant="quiet" type="button" disabled={busy} onClick={close}>
                Cancel
              </Button>
            </div>
          </form>
        </Modal>
      )}
    </main>
  )
}

import { Button, Checkbox, Field, Select } from './ui'

/**
 * The search controls.
 *
 * Every filter describes what the PERSON wants - county, insurance,
 * language, focus area, modality. None of them ask anything clinical.
 * That is a deliberate boundary (ADR-0004): this app never holds health
 * information, so it never asks for any.
 *
 * State lives in SearchPage's reducer, not here - this component only
 * renders it and reports changes.
 */
export default function SearchFilters({ filters, catalog, onChange, onToggle, onReset }) {
  const set = (field) => (e) => onChange(field, e.target.value)

  return (
    <section className="filters" aria-label="Search filters">
      <div className="filters__grid">
        <Field label="County" id="f-county">
          <Select
            id="f-county"
            value={filters.countyId}
            onChange={set('countyId')}
            options={catalog.counties}
            anyLabel="Anywhere in Florida"
          />
        </Field>

        <Field label="What you need help with" id="f-specialty">
          <Select
            id="f-specialty"
            value={filters.specialtyId}
            onChange={set('specialtyId')}
            options={catalog.specialties}
            anyLabel="Any focus area"
          />
        </Field>

        <Field label="Your insurance" id="f-insurance">
          <Select
            id="f-insurance"
            value={filters.insurancePlanId}
            onChange={set('insurancePlanId')}
            options={catalog.insurancePlans}
            anyLabel="Any coverage"
          />
        </Field>

        <Field label="Language" id="f-language">
          <Select
            id="f-language"
            value={filters.languageId}
            onChange={set('languageId')}
            options={catalog.languages}
            anyLabel="Any language"
          />
        </Field>

        <Field label="Who it's for" id="f-population">
          <Select
            id="f-population"
            value={filters.populationId}
            onChange={set('populationId')}
            options={catalog.populations}
            anyLabel="Anyone"
          />
        </Field>
      </div>

      <div className="filters__foot">
        <div className="filters__toggles">
          <Checkbox
            id="f-accepting"
            label="Only show providers accepting new clients"
            checked={filters.acceptingOnly}
            onChange={() => onToggle('acceptingOnly')}
          />
          <Checkbox
            id="f-telehealth"
            label="Offers telehealth"
            checked={filters.telehealth}
            onChange={() => onToggle('telehealth')}
          />
        </div>

        <Button variant="quiet" size="small" onClick={onReset}>
          Clear filters
        </Button>
      </div>
    </section>
  )
}

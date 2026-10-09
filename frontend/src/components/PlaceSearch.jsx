import { useEffect, useState } from 'react'
import { searchPlaces } from '../api'

/** Text box with OpenStreetMap place suggestions & local search. */
export default function PlaceSearch({ value, onSelect, placeholder, extraOption }) {
  const [q, setQ] = useState(value?.name || '')
  const [results, setResults] = useState([])
  const [open, setOpen] = useState(false)
  const [loading, setLoading] = useState(false)

  useEffect(() => {
    setQ(value?.name || '')
  }, [value?.name])

  useEffect(() => {
    if (!open || q.length < 3 || q === value?.name) {
      setResults([])
      setLoading(false)
      return
    }
    setLoading(true)
    const id = setTimeout(() => {
      searchPlaces(q)
        .then((res) => setResults(Array.isArray(res) ? res : []))
        .catch(() => setResults([]))
        .finally(() => setLoading(false))
    }, 400)
    return () => clearTimeout(id)
  }, [q, open, value?.name])

  const handleClear = (e) => {
    e.stopPropagation()
    setQ('')
    setResults([])
    onSelect(null)
  }

  return (
    <div className="place-search">
      <div style={{ position: 'relative', width: '100%' }}>
        <input
          className="input"
          value={q}
          placeholder={placeholder}
          role="combobox"
          aria-autocomplete="list"
          aria-expanded={open && (results.length > 0 || !!extraOption)}
          onFocus={() => setOpen(true)}
          onBlur={() => setTimeout(() => setOpen(false), 250)}
          onChange={(e) => setQ(e.target.value)}
          style={{ paddingRight: q ? 38 : 14 }}
        />
        {q && (
          <button
            type="button"
            onClick={handleClear}
            style={{
              position: 'absolute',
              right: 10,
              top: '50%',
              transform: 'translateY(-50%)',
              background: 'none',
              border: 'none',
              color: 'var(--muted)',
              fontSize: '1.1rem',
              cursor: 'pointer',
              padding: '4px 6px',
              display: 'grid',
              placeItems: 'center'
            }}
            aria-label="Clear location input"
          >
            ✕
          </button>
        )}
      </div>

      {open && (results.length > 0 || extraOption || loading) && (
        <ul className="suggestions" role="listbox">
          {extraOption && (
            <li>
              <button
                type="button"
                onMouseDown={(e) => {
                  e.preventDefault()
                  extraOption.onClick()
                  setOpen(false)
                }}
              >
                📍 <b>{extraOption.label}</b>
              </button>
            </li>
          )}
          {loading && (
            <li style={{ padding: '8px 12px', color: 'var(--muted)', fontSize: '0.84rem' }}>
              Searching places…
            </li>
          )}
          {results.map((r, i) => (
            <li key={i}>
              <button
                type="button"
                onMouseDown={(e) => {
                  e.preventDefault()
                  const shortName = r.name.split(',').slice(0, 3).join(', ')
                  onSelect({ ...r, name: shortName })
                  setQ(shortName)
                  setOpen(false)
                  setResults([])
                }}
              >
                🔍 {r.name}
              </button>
            </li>
          ))}
        </ul>
      )}
    </div>
  )
}

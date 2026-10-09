import { useEffect, useState } from 'react'
import { searchPlaces } from '../api'

/** Text box with OpenStreetMap place suggestions. */
export default function PlaceSearch({ value, onSelect, placeholder, extraOption }) {
  const [q, setQ] = useState(value?.name || '')
  const [results, setResults] = useState([])
  const [open, setOpen] = useState(false)

  useEffect(() => { setQ(value?.name || '') }, [value?.name])

  useEffect(() => {
    if (!open || q.length < 3 || q === value?.name) return
    const id = setTimeout(() => searchPlaces(q).then(setResults).catch(() => setResults([])), 450)
    return () => clearTimeout(id)
  }, [q, open, value?.name])

  return (
    <div className="place-search">
      <input
        className="input"
        value={q}
        placeholder={placeholder}
        onFocus={() => setOpen(true)}
        onBlur={() => setTimeout(() => setOpen(false), 200)}
        onChange={(e) => setQ(e.target.value)}
      />
      {open && (results.length > 0 || extraOption) && (
        <ul className="suggestions">
          {extraOption && (
            <li><button onMouseDown={() => { extraOption.onClick(); setOpen(false) }}>📍 {extraOption.label}</button></li>
          )}
          {results.map((r, i) => (
            <li key={i}>
              <button onMouseDown={() => { onSelect({ ...r, name: r.name.split(',').slice(0, 3).join(',') }); setOpen(false); setResults([]) }}>
                {r.name}
              </button>
            </li>
          ))}
        </ul>
      )}
    </div>
  )
}

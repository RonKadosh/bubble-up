import { ReactNode } from 'react'

export interface Column<T> {
  header: string
  cell: (row: T) => ReactNode
  width?: string
}

interface Props<T> {
  columns: Column<T>[]
  rows: T[]
  keyOf: (row: T) => string
  onRowClick?: (row: T) => void
  empty?: ReactNode
}

export default function AdminTable<T>({ columns, rows, keyOf, onRowClick, empty }: Props<T>) {
  if (rows.length === 0) {
    return (
      <div className="rounded-2xl border border-line bg-surface p-8 text-center text-secondary">
        {empty ?? 'No results.'}
      </div>
    )
  }
  return (
    <div className="rounded-2xl border border-line bg-surface overflow-hidden">
      <table className="w-full text-sm">
        <thead className="bg-base/40">
          <tr>
            {columns.map((c, i) => (
              <th
                key={i}
                className="text-start px-4 py-2 font-medium text-secondary text-xs uppercase tracking-wide"
                style={c.width ? { width: c.width } : undefined}
              >
                {c.header}
              </th>
            ))}
          </tr>
        </thead>
        <tbody>
          {rows.map((row) => (
            <tr
              key={keyOf(row)}
              onClick={onRowClick ? () => onRowClick(row) : undefined}
              className={`border-t border-line ${onRowClick ? 'cursor-pointer hover:bg-surface-hover' : ''}`}
            >
              {columns.map((c, i) => (
                <td key={i} className="px-4 py-3 align-middle">
                  {c.cell(row)}
                </td>
              ))}
            </tr>
          ))}
        </tbody>
      </table>
    </div>
  )
}

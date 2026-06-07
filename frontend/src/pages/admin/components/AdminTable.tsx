import { ReactNode } from 'react'
import { Card } from '../../../components/Card'

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
      <Card size="lg" className="p-8 text-center text-muted">
        {empty ?? 'No results.'}
      </Card>
    )
  }
  return (
    <Card size="lg" className="overflow-x-auto">
      <table className="w-full min-w-[42rem] text-sm">
        <thead className="bg-surface-muted">
          <tr>
            {columns.map((c, i) => (
              <th
                key={i}
                className="text-start px-4 py-2 font-semibold text-muted text-xs uppercase tracking-wide"
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
    </Card>
  )
}

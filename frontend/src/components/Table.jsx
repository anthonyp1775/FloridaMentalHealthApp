/**
 * A plain data table.
 *
 * Reports are columns of numbers that people compare down a column and
 * read across a row, which is what a table is for. Rendering them as
 * cards would make both of those harder.
 */
export default function Table({ columns, rows, keyField = 'id', caption }) {
  return (
    <table className="table">
      {caption && <caption className="sr-only">{caption}</caption>}

      <thead>
        <tr>
          {columns.map((c) => (
            <th key={c.key} scope="col">{c.label}</th>
          ))}
        </tr>
      </thead>

      <tbody>
        {rows.map((row, i) => (
          <tr key={row[keyField] ?? i}>
            {columns.map((c) => (
              <td key={c.key}>
                {c.render ? c.render(row) : row[c.key]}
              </td>
            ))}
          </tr>
        ))}
      </tbody>
    </table>
  )
}

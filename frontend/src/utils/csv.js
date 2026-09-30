/**
 * One small, reusable CSV export utility — no new dependency (no
 * papaparse or similar), since a correct CSV writer for this project's
 * needs is a handful of lines. Used by AdminMembers, AdminRevenue, and
 * AdminReports; never exposed on any Member-facing page.
 */
export function downloadCsv(filename, headers, rows) {
  const escape = (value) => {
    const str = value === null || value === undefined ? '' : String(value);
    // Proper CSV escaping: any field containing a comma, a quote, or a
    // newline must be wrapped in quotes, with internal quotes doubled.
    if (str.includes(',') || str.includes('"') || str.includes('\n')) {
      return `"${str.replace(/"/g, '""')}"`;
    }
    return str;
  };

  const lines = [headers, ...rows].map((row) => row.map(escape).join(','));
  const csvContent = lines.join('\r\n');

  const blob = new Blob([csvContent], { type: 'text/csv;charset=utf-8;' });
  const url = URL.createObjectURL(blob);
  const link = document.createElement('a');
  link.href = url;
  link.download = filename;
  document.body.appendChild(link);
  link.click();
  document.body.removeChild(link);
  URL.revokeObjectURL(url);
}
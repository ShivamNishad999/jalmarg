export function safePdfFilename(name) {
  const clean = String(name || 'jalmarg-evidence').replace(/\.pdf$/i, '').replace(/[^a-zA-Z0-9_-]/g, '-').slice(0, 90)
  return `${clean || 'jalmarg-evidence'}.pdf`
}
export async function validatePdfBlob(blob) {
  if (!(blob instanceof Blob) || blob.type.split(';')[0].toLowerCase() !== 'application/pdf' || blob.size < 5 ||
      await blob.slice(0, 5).text() !== '%PDF-') throw new Error('Invalid PDF')
  return blob
}

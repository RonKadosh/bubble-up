import { useState } from 'react'
import { useTranslation } from 'react-i18next'
import { useNavigate } from 'react-router-dom'
import { attachReportImage, REPORT_CATEGORIES, ReportCategory, submitReport } from '../../api/report'
import { describeError } from '../../api/errors'
import { Card } from '../../components/Card'
import { Button } from '../../components/Button'
import { FormField, fieldInputClass } from '../../components/FormField'

/**
 * `/report` — the Report Center. A concise, entity-agnostic form: pick a
 * category, describe the issue, optionally attach a screenshot. Submissions land
 * in the admin Reports inbox.
 */
export default function ReportPage() {
  const { t } = useTranslation()
  const navigate = useNavigate()

  const [category, setCategory] = useState<ReportCategory>('ABUSE')
  const [subject, setSubject] = useState('')
  const [description, setDescription] = useState('')
  const [image, setImage] = useState<File | null>(null)
  const [previewUrl, setPreviewUrl] = useState<string | null>(null)
  const [submitting, setSubmitting] = useState(false)
  const [error, setError] = useState<string | null>(null)
  const [done, setDone] = useState(false)

  function onPickImage(file: File | null) {
    if (previewUrl) URL.revokeObjectURL(previewUrl)
    setImage(file)
    setPreviewUrl(file ? URL.createObjectURL(file) : null)
  }

  async function handleSubmit(e: React.FormEvent) {
    e.preventDefault()
    if (!subject.trim() || !description.trim()) return
    setSubmitting(true)
    setError(null)
    try {
      const report = await submitReport({ category, subject: subject.trim(), description: description.trim() })
      if (image) await attachReportImage(report.id, image)
      if (previewUrl) URL.revokeObjectURL(previewUrl)
      setDone(true)
    } catch (err) {
      setError(describeError(err, t,
        {
          REPORT_IMAGE_TYPE_NOT_ALLOWED: 'report.error.imageType',
          REPORT_IMAGE_TOO_LARGE: 'report.error.imageTooLarge',
        },
        'report.error.generic'))
    } finally {
      setSubmitting(false)
    }
  }

  if (done) {
    return (
      <div className="flex-1 overflow-y-auto p-8">
        <div className="max-w-2xl mx-auto">
          <Card size="lg" className="p-8 text-center flex flex-col items-center gap-4">
            <div className="w-12 h-12 rounded-full bg-bubble-green-soft text-bubble-green flex items-center justify-center text-2xl">✓</div>
            <h1 className="text-xl font-bold text-base">{t('report.success.title')}</h1>
            <p className="text-sm text-muted">{t('report.success.body')}</p>
            <Button type="button" size="sm" onClick={() => navigate('/dashboard')}>
              {t('report.success.backToDashboard')}
            </Button>
          </Card>
        </div>
      </div>
    )
  }

  return (
    <div className="flex-1 overflow-y-auto p-8">
      <div className="max-w-2xl mx-auto">
        <div className="flex items-center gap-3 mb-2">
          <div className="w-2.5 h-2.5 rounded-full bg-bubble-magenta shadow-sm" />
          <div className="w-1.5 h-1.5 rounded-full bg-bubble-green" />
          <h1 className="text-2xl font-bold text-base">{t('report.title')}</h1>
        </div>
        <p className="text-sm text-muted mb-6 ms-[1.6rem]">{t('report.subtitle')}</p>

        <Card size="lg" className="p-6">
          <form onSubmit={handleSubmit} className="space-y-4">
            <FormField label={t('report.categoryLabel')} required>
              <select
                value={category}
                onChange={(e) => setCategory(e.target.value as ReportCategory)}
                className={fieldInputClass}
              >
                {REPORT_CATEGORIES.map((c) => (
                  <option key={c} value={c}>{t(`report.category.${c}`)}</option>
                ))}
              </select>
            </FormField>

            <FormField label={t('report.subjectLabel')} required>
              <input
                type="text"
                value={subject}
                onChange={(e) => setSubject(e.target.value)}
                maxLength={200}
                required
                placeholder={t('report.subjectPlaceholder')}
                className={fieldInputClass}
              />
            </FormField>

            <FormField label={t('report.descriptionLabel')} required>
              <textarea
                value={description}
                onChange={(e) => setDescription(e.target.value)}
                maxLength={4000}
                rows={6}
                required
                placeholder={t('report.descriptionPlaceholder')}
                className={fieldInputClass}
              />
            </FormField>

            <FormField label={t('report.imageLabel')} hint={t('report.imageHint')}>
              <input
                type="file"
                accept="image/png,image/jpeg,image/webp,image/gif"
                onChange={(e) => onPickImage(e.target.files?.[0] ?? null)}
                className="block text-sm text-muted file:me-3 file:rounded-full file:border-0 file:bg-surface-muted file:px-3 file:py-1.5 file:text-sm file:text-base hover:file:bg-surface-hover"
              />
              {previewUrl && (
                <img src={previewUrl} alt={t('report.imageLabel')} className="mt-2 max-h-48 rounded-xl border border-line" />
              )}
            </FormField>

            {error && <div className="text-sm text-danger">{error}</div>}

            <Button
              type="submit"
              size="sm"
              disabled={submitting || !subject.trim() || !description.trim()}
              className="w-full sm:w-auto"
            >
              {submitting ? t('report.submitting') : t('report.submit')}
            </Button>
          </form>
        </Card>
      </div>
    </div>
  )
}

import { useEffect, useMemo, useRef, useState } from 'react'
import { useParams } from 'react-router-dom'
import { useTranslation } from 'react-i18next'
import { useAuthStore } from '../store/authStore'
import { Avatar } from '../components/Avatar'
import { Button } from '../components/Button'
import { Card } from '../components/Card'
import {
  UserProfile,
  deleteAvatar,
  getMyProfile,
  getUserProfile,
  updateMyProfile,
  uploadAvatar,
} from '../api/users'
import { Department, University, getDepartments, getUniversities } from '../api/catalog'
import { describeError, errorCode } from '../api/errors'

/**
 * `/profile` (self) and `/profile/:userId` (other). Same component branches on whether
 * the URL parameter equals the authenticated user. Self mode shows inline edit
 * affordances + avatar upload; other mode is read-only.
 */
export default function ProfilePage() {
  const { t, i18n } = useTranslation()
  const { userId } = useParams<{ userId?: string }>()
  const me = useAuthStore((s) => s.user)
  const updateAuthUser = useAuthStore((s) => s.updateUser)

  const targetUserId = userId ?? me?.id ?? null
  const isSelf = !!me && (!userId || userId === me.id)

  const [profile, setProfile] = useState<UserProfile | null>(null)
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState('')
  const [editing, setEditing] = useState(false)
  const [saving, setSaving] = useState(false)
  const [notVisible, setNotVisible] = useState(false)

  // Self-edit form state — derived from profile on enter-edit.
  const [formDisplayName, setFormDisplayName] = useState('')
  const [formBio, setFormBio] = useState('')
  const [formUniversityId, setFormUniversityId] = useState('')
  const [formDepartmentId, setFormDepartmentId] = useState('')
  const [formEnrollmentYear, setFormEnrollmentYear] = useState('')

  const [universities, setUniversities] = useState<University[]>([])
  const [departments, setDepartments] = useState<Department[]>([])
  const fileInputRef = useRef<HTMLInputElement | null>(null)
  const [avatarUploading, setAvatarUploading] = useState(false)

  // Load (or reload) the profile when the target changes.
  useEffect(() => {
    if (!targetUserId) return
    let cancelled = false
    setLoading(true)
    setError('')
    setNotVisible(false)
    const loader = isSelf ? getMyProfile() : getUserProfile(targetUserId)
    loader
      .then((p) => { if (!cancelled) setProfile(p) })
      .catch((e) => {
        if (cancelled) return
        if (errorCode(e) === 'PROFILE_NOT_VISIBLE') {
          setNotVisible(true)
        } else {
          setError(t('profile.errorLoad'))
        }
      })
      .finally(() => { if (!cancelled) setLoading(false) })
    return () => { cancelled = true }
  }, [targetUserId, isSelf, t])

  // Lazy-load universities the first time edit opens.
  useEffect(() => {
    if (!editing || universities.length > 0) return
    getUniversities().then(setUniversities).catch(() => {/* keep empty — form still valid */})
  }, [editing, universities.length])

  // Cascade: load departments when the chosen university changes.
  useEffect(() => {
    if (!formUniversityId) {
      setDepartments([])
      return
    }
    getDepartments(formUniversityId).then(setDepartments).catch(() => setDepartments([]))
  }, [formUniversityId])

  function startEditing() {
    if (!profile) return
    setFormDisplayName(profile.displayName)
    setFormBio(profile.bio ?? '')
    setFormUniversityId(profile.universityId ?? '')
    setFormDepartmentId(profile.departmentId ?? '')
    setFormEnrollmentYear(profile.enrollmentYear != null ? String(profile.enrollmentYear) : '')
    setEditing(true)
  }

  async function handleSave(e: React.FormEvent) {
    e.preventDefault()
    if (!profile) return
    const trimmedName = formDisplayName.trim()
    if (!trimmedName) return
    setSaving(true)
    setError('')
    try {
      const updated = await updateMyProfile({
        displayName: trimmedName,
        bio: formBio,
        universityId: formUniversityId || undefined,
        departmentId: formDepartmentId || undefined,
        enrollmentYear: formEnrollmentYear ? Number(formEnrollmentYear) : undefined,
      })
      setProfile(updated)
      setEditing(false)
      // Keep the store in sync so the rail avatar + chat sender row update immediately.
      updateAuthUser({ displayName: updated.displayName, avatarUrl: updated.avatarUrl })
    } catch (err) {
      setError(describeError(err, t, {}, 'profile.errorSave'))
    } finally {
      setSaving(false)
    }
  }

  async function handleAvatarFile(file: File) {
    setError('')
    setAvatarUploading(true)
    try {
      const updated = await uploadAvatar(file)
      setProfile(updated)
      updateAuthUser({ avatarUrl: updated.avatarUrl })
    } catch (err) {
      setError(describeError(err, t, {
        AVATAR_TYPE_NOT_ALLOWED: 'profile.errorAvatarType',
        AVATAR_TOO_LARGE: 'profile.errorAvatarTooLarge',
      }, 'profile.errorAvatarGeneric'))
    } finally {
      setAvatarUploading(false)
    }
  }

  async function handleAvatarDelete() {
    setError('')
    setAvatarUploading(true)
    try {
      const updated = await deleteAvatar()
      setProfile(updated)
      updateAuthUser({ avatarUrl: null })
    } catch (err) {
      setError(describeError(err, t, {}, 'profile.errorAvatarGeneric'))
    } finally {
      setAvatarUploading(false)
    }
  }

  const joinedLabel = useMemo(() => {
    if (!profile?.createdAt) return ''
    try {
      return new Date(profile.createdAt).toLocaleDateString(i18n.language, {
        year: 'numeric', month: 'short', day: 'numeric',
      })
    } catch {
      return profile.createdAt
    }
  }, [profile?.createdAt, i18n.language])

  if (notVisible) {
    return (
      <div className="flex-1 overflow-y-auto">
        <div className="container-app px-4 tablet:px-6 desktop:px-8 py-12 flex justify-center">
          <Card size="lg" className="px-6 py-10 max-w-md text-center shadow-bubble">
            <p className="text-base font-semibold">{t('profile.cannotView')}</p>
          </Card>
        </div>
      </div>
    )
  }

  if (loading || !profile) {
    return (
      <div className="flex-1 overflow-y-auto">
        <div className="container-app px-4 tablet:px-6 desktop:px-8 py-8">
          <p className="text-muted text-sm">{t('common.loading')}</p>
        </div>
      </div>
    )
  }

  return (
    <div className="flex-1 overflow-y-auto">
      <div className="container-app px-4 tablet:px-6 desktop:px-8 py-6 tablet:py-8">
        <header className="mb-6">
          <div className="flex items-center gap-3 mb-1">
            <div className="w-2.5 h-2.5 rounded-full bg-bubble-magenta shadow-sm" />
            <div className="w-1.5 h-1.5 rounded-full bg-bubble-green" />
            <h1 className="text-2xl font-bold text-base">
              {isSelf ? t('profile.title') : t('profile.titleOther')}
            </h1>
          </div>
        </header>

        {error && (
          <div className="bg-danger-soft text-danger text-sm px-4 py-2.5 rounded-2xl border border-line mb-4">
            {error}
          </div>
        )}

        <Card size="lg" className="p-5 tablet:p-6 shadow-bubble max-w-2xl">
          <div className="flex items-start gap-4 tablet:gap-6 flex-wrap">
            <div className="shrink-0 flex flex-col items-center gap-2">
              <Avatar
                id={profile.userId}
                name={profile.displayName}
                imageUrl={profile.avatarUrl}
                size="lg"
                ring
                className="!w-20 !h-20"
              />
              {isSelf && !editing && (
                <div className="flex flex-col gap-1 items-center">
                  <input
                    ref={fileInputRef}
                    type="file"
                    accept="image/png,image/jpeg,image/webp,image/gif"
                    className="hidden"
                    onChange={(e) => {
                      const f = e.target.files?.[0]
                      if (f) handleAvatarFile(f)
                      e.target.value = ''
                    }}
                  />
                  <Button
                    variant="ghost"
                    size="xs"
                    onClick={() => fileInputRef.current?.click()}
                    disabled={avatarUploading}
                  >
                    {avatarUploading ? t('common.loading') : t('profile.uploadAvatar')}
                  </Button>
                  {profile.avatarUrl && (
                    <Button
                      variant="ghost"
                      size="xs"
                      onClick={handleAvatarDelete}
                      disabled={avatarUploading}
                      className="text-danger"
                    >
                      {t('profile.removeAvatar')}
                    </Button>
                  )}
                </div>
              )}
            </div>

            <div className="flex-1 min-w-0">
              {!editing ? (
                <>
                  <div className="flex items-center gap-2 flex-wrap">
                    <h2 className="text-xl font-bold text-base break-words">{profile.displayName}</h2>
                    <span className="text-xs px-2 py-0.5 rounded-md bg-surface-muted text-secondary">
                      {profile.role}
                    </span>
                  </div>
                  <p className="text-sm text-muted mt-0.5">
                    {t('profile.joinedAt', { date: joinedLabel })}
                  </p>
                  <p className="text-sm text-secondary mt-4 whitespace-pre-wrap break-words">
                    {profile.bio || t('profile.noBio')}
                  </p>
                  <p className="text-sm text-secondary mt-4">
                    <span className="text-muted">{t('profile.affiliationLabel')}: </span>
                    {profile.universityName || profile.departmentName || profile.enrollmentYear != null
                      ? [
                          profile.universityName,
                          profile.departmentName,
                          profile.enrollmentYear != null
                            ? `${t('profile.enrollmentYearLabel')} ${profile.enrollmentYear}`
                            : null,
                        ].filter(Boolean).join(' · ')
                      : t('profile.noAffiliation')}
                  </p>
                  {isSelf && (
                    <div className="mt-5">
                      <Button onClick={startEditing} size="sm">
                        {t('common.edit')}
                      </Button>
                    </div>
                  )}
                </>
              ) : (
                <form onSubmit={handleSave} className="flex flex-col gap-3">
                  <div>
                    <label className="text-xs font-medium text-secondary mb-1 block">
                      {t('profile.displayNameLabel')}
                    </label>
                    <input
                      value={formDisplayName}
                      onChange={(e) => setFormDisplayName(e.target.value)}
                      placeholder={t('profile.displayNamePlaceholder')}
                      className="w-full bg-surface text-base border border-line rounded-2xl px-4 py-2.5 text-sm focus:outline-none focus:border-primary-400 focus:ring-2 focus:ring-primary-100 transition"
                      maxLength={100}
                      required
                    />
                  </div>
                  <div>
                    <label className="text-xs font-medium text-secondary mb-1 block">
                      {t('profile.bioLabel')}
                    </label>
                    <textarea
                      value={formBio}
                      onChange={(e) => setFormBio(e.target.value)}
                      placeholder={t('profile.bioPlaceholder')}
                      className="w-full bg-surface text-base border border-line rounded-2xl px-4 py-2.5 text-sm focus:outline-none focus:border-primary-400 focus:ring-2 focus:ring-primary-100 transition resize-y min-h-[5rem]"
                      maxLength={280}
                      rows={3}
                    />
                  </div>
                  <div>
                    <label className="text-xs font-medium text-secondary mb-1 block">
                      {t('profile.universityLabel')}
                    </label>
                    <select
                      value={formUniversityId}
                      onChange={(e) => {
                        setFormUniversityId(e.target.value)
                        setFormDepartmentId('')
                      }}
                      className="w-full bg-surface text-base border border-line rounded-2xl px-4 py-2.5 text-sm focus:outline-none focus:border-primary-400 transition"
                    >
                      <option value="">{t('profile.noAffiliation')}</option>
                      {universities.map((u) => (
                        <option key={u.id} value={u.id}>{u.name}</option>
                      ))}
                    </select>
                  </div>
                  <div>
                    <label className="text-xs font-medium text-secondary mb-1 block">
                      {t('profile.departmentLabel')}
                    </label>
                    <select
                      value={formDepartmentId}
                      onChange={(e) => setFormDepartmentId(e.target.value)}
                      disabled={!formUniversityId}
                      className="w-full bg-surface text-base border border-line rounded-2xl px-4 py-2.5 text-sm focus:outline-none focus:border-primary-400 transition disabled:opacity-50"
                    >
                      <option value="">{t('profile.selectDepartment')}</option>
                      {departments.map((d) => (
                        <option key={d.id} value={d.id}>{d.name}</option>
                      ))}
                    </select>
                  </div>
                  <div>
                    <label className="text-xs font-medium text-secondary mb-1 block">
                      {t('profile.enrollmentYearLabel')}
                    </label>
                    <input
                      type="number"
                      min={1}
                      max={10}
                      value={formEnrollmentYear}
                      onChange={(e) => setFormEnrollmentYear(e.target.value)}
                      className="w-full bg-surface text-base border border-line rounded-2xl px-4 py-2.5 text-sm focus:outline-none focus:border-primary-400 transition"
                    />
                  </div>
                  <div className="flex gap-2 mt-2">
                    <Button type="submit" size="sm" disabled={saving || !formDisplayName.trim()}>
                      {saving ? t('profile.savingButton') : t('profile.saveButton')}
                    </Button>
                    <Button
                      type="button"
                      variant="ghost"
                      size="sm"
                      onClick={() => setEditing(false)}
                      disabled={saving}
                    >
                      {t('common.cancel')}
                    </Button>
                  </div>
                </form>
              )}
            </div>
          </div>
        </Card>
      </div>
    </div>
  )
}

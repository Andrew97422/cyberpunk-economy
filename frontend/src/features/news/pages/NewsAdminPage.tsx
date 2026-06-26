import { useEffect, useRef, useState, FormEvent } from 'react';
import client from '../../../shared/api/client';
import { ENDPOINTS } from '../../../shared/api/config';
import type { NewsResponse, PagedResponse } from '../../../shared/types';
import { NEWS_STATUSES } from '../../../shared/types';
import { extractError, formatDate, NEWS_STATUS_LABELS } from '../../../shared/utils';
import { PageSection, FormField, ErrorBlock, SuccessMessage, LoadingBlock } from '../../../shared/ui';
import ImageUpload from '../../../shared/ui/ImageUpload';
import { useTableSort, SortTh } from '../../../shared/ui/tableSort';
import { uploadMedia } from '../../../shared/api/media';
import { renderMarkdown } from '../markdown';
import '../markdown.css';

const EMPTY_FORM = { title: '', summary: '', category: '', body: '', pinned: false, publishNow: false };
const SIZE = 20;

function NewsBadge({ status }: { status: string }) {
  return <span className={`badge badge-${status.toLowerCase()}`}>{NEWS_STATUS_LABELS[status] || status}</span>;
}

interface BodyEditorProps {
  id: string;
  value: string;
  onChange: (v: string) => void;
}

/**
 * Markdown-aware body textarea with an "insert image" toolbar.
 * Uploads the chosen file via `uploadNewsMedia` and injects `![](<url>)`
 * markdown at the current cursor position (falls back to appending).
 */
function BodyEditor({ id, value, onChange }: BodyEditorProps) {
  const textareaRef = useRef<HTMLTextAreaElement | null>(null);
  const fileRef = useRef<HTMLInputElement | null>(null);
  const [uploading, setUploading] = useState(false);
  const [uploadError, setUploadError] = useState('');

  const insertAtCursor = (snippet: string) => {
    const ta = textareaRef.current;
    if (!ta) {
      onChange(value ? `${value}\n${snippet}` : snippet);
      return;
    }
    const start = ta.selectionStart ?? value.length;
    const end = ta.selectionEnd ?? value.length;
    const next = value.slice(0, start) + snippet + value.slice(end);
    onChange(next);
    // Restore caret just after the inserted snippet on the next frame.
    requestAnimationFrame(() => {
      const pos = start + snippet.length;
      ta.focus();
      ta.setSelectionRange(pos, pos);
    });
  };

  const handleFile = async (e: React.ChangeEvent<HTMLInputElement>) => {
    const file = e.target.files?.[0];
    e.target.value = '';
    if (!file) return;
    setUploading(true);
    setUploadError('');
    try {
      const url = await uploadMedia(file);
      insertAtCursor(`![](${url})`);
    } catch (err) {
      setUploadError(extractError(err));
    } finally {
      setUploading(false);
    }
  };

  return (
    <div>
      <div style={{ display: 'flex', alignItems: 'center', gap: 10, marginBottom: 6, flexWrap: 'wrap' }}>
        <button
          type="button"
          className="btn btn-sm btn-secondary"
          onMouseDown={(e) => e.preventDefault()}
          onClick={() => fileRef.current?.click()}
          disabled={uploading}
          title="Загрузить картинку и вставить в позицию курсора"
        >
          {uploading ? 'Загрузка…' : '🖼 Вставить картинку'}
        </button>
        <input
          ref={fileRef}
          type="file"
          accept="image/*"
          onChange={handleFile}
          style={{ display: 'none' }}
        />
      </div>
      <textarea
        id={id}
        ref={textareaRef}
        rows={6}
        value={value}
        onChange={(e) => onChange(e.target.value)}
        required
      />
      <p className="subtext" style={{ marginTop: 6 }}>
        Поддерживается Markdown: **жирный**, ссылки, ![картинки](). Картинка вставляется в позицию курсора.
      </p>
      {uploadError && <ErrorBlock message={uploadError} />}
      {value.trim() && (
        <div style={{ marginTop: 10 }}>
          <div className="image-upload-label" style={{ marginBottom: 6 }}>Предпросмотр</div>
          <div
            className="md-content"
            style={{ border: '1px solid var(--border)', borderRadius: 8, padding: '12px 14px', background: 'var(--surface-muted)' }}
            dangerouslySetInnerHTML={{ __html: renderMarkdown(value) }}
          />
        </div>
      )}
    </div>
  );
}

export default function NewsAdminPage() {
  const [posts, setPosts] = useState<NewsResponse[]>([]);
  const [statusFilter, setStatusFilter] = useState('');
  const [total, setTotal] = useState(0);
  const [page, setPage] = useState(0);
  const [totalPages, setTotalPages] = useState(1);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState('');

  const [form, setForm] = useState(EMPTY_FORM);
  const [coverImageUrl, setCoverImageUrl] = useState('');
  const [galleryUrls, setGalleryUrls] = useState<string[]>([]);
  const [creating, setCreating] = useState(false);
  const [createError, setCreateError] = useState('');
  const [createSuccess, setCreateSuccess] = useState('');

  const setF = (k: keyof typeof EMPTY_FORM) => (v: string | boolean) => setForm((f) => ({ ...f, [k]: v }));

  // edit panel
  const [editing, setEditing] = useState<NewsResponse | null>(null);
  const [editForm, setEditForm] = useState({ title: '', summary: '', category: '', body: '' });
  const [editCover, setEditCover] = useState('');
  const [editGallery, setEditGallery] = useState<string[]>([]);
  const [savingEdit, setSavingEdit] = useState(false);
  const [editError, setEditError] = useState('');
  const setE = (k: keyof typeof editForm) => (v: string) => setEditForm((f) => ({ ...f, [k]: v }));

  const fetchNews = async (p = page) => {
    setLoading(true);
    setError('');
    try {
      const params: Record<string, unknown> = { page: p, size: SIZE };
      if (statusFilter) params.status = statusFilter;
      const res = await client.get<PagedResponse<NewsResponse>>(ENDPOINTS.news, { params });
      setPosts(res.data.content);
      setTotal(res.data.totalElements);
      setTotalPages(res.data.totalPages);
      setPage(p);
    } catch (err) {
      setError(extractError(err));
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    fetchNews(0);
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [statusFilter]);

  const handleCreate = async (e: FormEvent) => {
    e.preventDefault();
    setCreating(true);
    setCreateError('');
    setCreateSuccess('');
    try {
      await client.post(ENDPOINTS.news, {
        title: form.title.trim(),
        summary: form.summary || undefined,
        category: form.category || undefined,
        body: form.body,
        pinned: form.pinned,
        status: form.publishNow ? 'PUBLISHED' : 'DRAFT',
        coverImageUrl: coverImageUrl || undefined,
        galleryUrls: galleryUrls.length ? galleryUrls : undefined,
      });
      setCreateSuccess(`Новость «${form.title.trim()}» ${form.publishNow ? 'опубликована' : 'сохранена в черновики'}`);
      setForm(EMPTY_FORM);
      setCoverImageUrl('');
      setGalleryUrls([]);
      fetchNews(0);
    } catch (err) {
      setCreateError(extractError(err));
    } finally {
      setCreating(false);
    }
  };

  const changeStatus = async (p: NewsResponse, status: string) => {
    try {
      await client.post(ENDPOINTS.newsStatus(p.id), { status });
      fetchNews();
    } catch (err) {
      setError(extractError(err));
    }
  };

  const togglePin = async (p: NewsResponse) => {
    try {
      await client.post(ENDPOINTS.newsPin(p.id), { pinned: !p.pinned });
      fetchNews();
    } catch (err) {
      setError(extractError(err));
    }
  };

  const openEdit = (p: NewsResponse) => {
    setEditing(p);
    setEditForm({ title: p.title || '', summary: p.summary || '', category: p.category || '', body: p.body || '' });
    setEditCover(p.coverImageUrl || '');
    setEditGallery(p.galleryUrls || []);
    setEditError('');
    window.scrollTo({ top: 0, behavior: 'smooth' });
  };

  const saveEdit = async (e: FormEvent) => {
    e.preventDefault();
    if (!editing) return;
    setSavingEdit(true);
    setEditError('');
    try {
      // Empty cover/gallery clears them (the service treats blank/[] as "remove").
      await client.patch(ENDPOINTS.newsById(editing.id), {
        title: editForm.title.trim(),
        summary: editForm.summary,
        category: editForm.category,
        body: editForm.body,
        coverImageUrl: editCover,
        galleryUrls: editGallery,
      });
      setEditing(null);
      fetchNews();
    } catch (err) {
      setEditError(extractError(err));
    } finally {
      setSavingEdit(false);
    }
  };

  const sort = useTableSort(posts, 'createdAt', 'desc');

  return (
    <div className="page" style={{ maxWidth: 1200 }}>
      <h1 className="page-title">Управление новостями</h1>
      <p className="subtext">Создание анонсов, публикация черновиков и закрепление.</p>

      {editing && (
        <PageSection title={`Редактировать: ${editing.title}`}>
          <form onSubmit={saveEdit} className="form-grid">
            <FormField label="Заголовок" id="e-title" required>
              <input id="e-title" type="text" value={editForm.title} onChange={(e) => setE('title')(e.target.value)} required />
            </FormField>
            <FormField label="Категория" id="e-cat">
              <input id="e-cat" type="text" value={editForm.category} onChange={(e) => setE('category')(e.target.value)} />
            </FormField>
            <div className="full">
              <FormField label="Краткое описание" id="e-sum">
                <input id="e-sum" type="text" value={editForm.summary} onChange={(e) => setE('summary')(e.target.value)} />
              </FormField>
            </div>
            <div className="full">
              <FormField label="Текст" id="e-body" required>
                <BodyEditor id="e-body" value={editForm.body} onChange={setE('body')} />
              </FormField>
            </div>
            <div className="full">
              <ImageUpload label="Обложка" value={editCover} onChange={setEditCover} />
            </div>
            <div className="full">
              <ImageUpload label="Галерея" multiple value={editGallery} onChange={setEditGallery} />
            </div>
            {editError && <div className="full"><ErrorBlock message={editError} /></div>}
            <div className="full btn-group">
              <button type="submit" className="btn btn-primary" disabled={savingEdit || !editForm.title.trim() || !editForm.body.trim()}>
                {savingEdit ? 'Сохраняю...' : 'Сохранить изменения'}
              </button>
              <button type="button" className="btn btn-ghost" onClick={() => setEditing(null)}>Отмена</button>
            </div>
          </form>
        </PageSection>
      )}

      <PageSection title="Создать новость">
        <form onSubmit={handleCreate} className="form-grid">
          <FormField label="Заголовок" id="n-title" required>
            <input id="n-title" type="text" value={form.title} onChange={(e) => setF('title')(e.target.value)} required />
          </FormField>
          <FormField label="Категория" id="n-cat">
            <input id="n-cat" type="text" value={form.category} onChange={(e) => setF('category')(e.target.value)} />
          </FormField>
          <div className="full">
            <FormField label="Краткое описание" id="n-sum">
              <input id="n-sum" type="text" value={form.summary} onChange={(e) => setF('summary')(e.target.value)} />
            </FormField>
          </div>
          <div className="full">
            <FormField label="Текст" id="n-body" required>
              <BodyEditor id="n-body" value={form.body} onChange={setF('body')} />
            </FormField>
          </div>
          <div className="full">
            <ImageUpload label="Обложка" value={coverImageUrl} onChange={setCoverImageUrl} />
          </div>
          <div className="full">
            <ImageUpload label="Галерея" multiple value={galleryUrls} onChange={setGalleryUrls} />
          </div>
          <div className="full" style={{ display: 'flex', gap: 20, alignItems: 'center' }}>
            <label style={{ display: 'flex', gap: 6, alignItems: 'center' }}>
              <input type="checkbox" checked={form.pinned} onChange={(e) => setF('pinned')(e.target.checked)} /> Закрепить
            </label>
            <label style={{ display: 'flex', gap: 6, alignItems: 'center' }}>
              <input type="checkbox" checked={form.publishNow} onChange={(e) => setF('publishNow')(e.target.checked)} /> Опубликовать сразу
            </label>
          </div>
          {createError && <div className="full"><ErrorBlock message={createError} /></div>}
          {createSuccess && <div className="full"><SuccessMessage message={createSuccess} /></div>}
          <div className="full">
            <button type="submit" className="btn btn-primary" disabled={creating || !form.title.trim() || !form.body.trim()}>
              {creating ? 'Сохраняю...' : 'Сохранить'}
            </button>
          </div>
        </form>
      </PageSection>

      <div className="filters-bar">
        <select value={statusFilter} onChange={(e) => setStatusFilter(e.target.value)}>
          <option value="">Все статусы</option>
          {NEWS_STATUSES.map((s) => <option key={s} value={s}>{NEWS_STATUS_LABELS[s]}</option>)}
        </select>
        <button className="btn btn-ghost" onClick={() => fetchNews(page)}>Обновить</button>
      </div>

      {error && <ErrorBlock message={error} />}
      {loading ? (
        <LoadingBlock />
      ) : (
        <>
          <div className="table-meta">Найдено: {total}</div>
          <div className="page-section" style={{ padding: 0, overflow: 'hidden' }}>
            <table className="table">
              <thead>
                <tr>
                  <SortTh k="id" label="ID" sort={sort} />
                  <SortTh k="title" label="Заголовок" sort={sort} />
                  <SortTh k="category" label="Категория" sort={sort} />
                  <th>Закреп</th>
                  <SortTh k="status" label="Статус" sort={sort} />
                  <SortTh k="createdAt" label="Дата" sort={sort} />
                  <th>Действия</th>
                </tr>
              </thead>
              <tbody>
                {sort.sorted.map((p) => (
                  <tr key={p.id}>
                    <td className="mono">{p.id}</td>
                    <td>{p.title}</td>
                    <td>{p.category || '—'}</td>
                    <td>{p.pinned ? '📌' : '—'}</td>
                    <td><NewsBadge status={p.status} /></td>
                    <td>{formatDate(p.publishedAt || p.publishAt || p.createdAt)}</td>
                    <td className="actions">
                      <select value={p.status} onChange={(e) => changeStatus(p, e.target.value)} style={{ maxWidth: 140 }} title="Сменить статус">
                        {NEWS_STATUSES.map((s) => <option key={s} value={s}>{NEWS_STATUS_LABELS[s]}</option>)}
                      </select>
                      <button className="btn btn-sm btn-ghost" onClick={() => togglePin(p)}>{p.pinned ? 'Открепить' : 'Закрепить'}</button>
                      <button className="btn btn-sm btn-ghost" onClick={() => openEdit(p)}>Изменить</button>
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
          {posts.length === 0 && <p className="empty-state">Новости не найдены</p>}
          <div className="pagination">
            <button className="btn btn-ghost btn-sm" disabled={page === 0} onClick={() => fetchNews(page - 1)}>← Назад</button>
            <span>Страница {page + 1} из {totalPages || 1}</span>
            <button className="btn btn-ghost btn-sm" disabled={page + 1 >= totalPages} onClick={() => fetchNews(page + 1)}>Вперёд →</button>
          </div>
        </>
      )}
    </div>
  );
}

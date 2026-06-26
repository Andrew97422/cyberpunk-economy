import { useEffect, useState } from 'react';
import { Link } from 'react-router-dom';
import client from '../../../shared/api/client';
import { ENDPOINTS } from '../../../shared/api/config';
import type { NewsResponse, PagedResponse } from '../../../shared/types';
import { extractError, formatDate } from '../../../shared/utils';
import { ErrorBlock, LoadingBlock } from '../../../shared/ui';
import { stripMarkdown } from '../markdown';

const SIZE = 30;

function teaser(p: NewsResponse): string {
  if (p.summary && p.summary.trim()) return stripMarkdown(p.summary);
  const body = stripMarkdown(p.body || '');
  return body.length > 180 ? body.slice(0, 180).trimEnd() + '…' : body;
}

export default function NewsFeedPage() {
  const [posts, setPosts] = useState<NewsResponse[]>([]);
  const [search, setSearch] = useState('');
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState('');

  const fetchNews = async () => {
    setLoading(true);
    setError('');
    try {
      const params: Record<string, unknown> = { status: 'PUBLISHED', page: 0, size: SIZE };
      if (search.trim()) params.search = search.trim();
      const res = await client.get<PagedResponse<NewsResponse>>(ENDPOINTS.news, { params });
      setPosts(res.data.content);
    } catch (err) {
      setError(extractError(err));
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    fetchNews();
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, []);

  return (
    <div className="page" style={{ maxWidth: 820 }}>
      <h1 className="page-title">Новости</h1>
      <p className="subtext">Анонсы и объявления мастеров игры. Нажмите на карточку, чтобы открыть полностью.</p>

      <div className="filters-bar">
        <input
          type="text"
          placeholder="Поиск по новостям..."
          value={search}
          onChange={(e) => setSearch(e.target.value)}
          onKeyDown={(e) => e.key === 'Enter' && fetchNews()}
          style={{ minWidth: 280 }}
        />
        <button className="btn btn-ghost" onClick={fetchNews}>Найти</button>
      </div>

      {error && <ErrorBlock message={error} />}
      {loading ? (
        <LoadingBlock />
      ) : posts.length === 0 ? (
        <p className="empty-state">Новостей пока нет</p>
      ) : (
        <div className="news-feed">
          {posts.map((p) => (
            <Link to={`/news/${p.id}`} key={p.id} className="news-card news-card-link">
              {p.coverImageUrl && (
                <div className="news-cover">
                  <img src={p.coverImageUrl} alt={p.title} loading="lazy" />
                </div>
              )}
              <div className="news-body">
                <div className="news-meta">
                  {p.pinned && <span className="badge badge-active">📌 Закреплено</span>}
                  {p.category && <span className="news-chip">{p.category}</span>}
                  <span className="news-date">{formatDate(p.publishedAt || p.publishAt || p.createdAt)}</span>
                </div>
                <h2 className="news-title">{p.title}</h2>
                <p className="news-summary">{teaser(p)}</p>
                <div className="news-readmore">
                  Читать{p.galleryUrls && p.galleryUrls.length > 0 ? ` · 🖼 ${p.galleryUrls.length}` : ''} →
                </div>
              </div>
            </Link>
          ))}
        </div>
      )}
    </div>
  );
}

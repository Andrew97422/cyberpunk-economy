import { useEffect, useState } from 'react';
import { useParams, useNavigate } from 'react-router-dom';
import client from '../../../shared/api/client';
import { ENDPOINTS } from '../../../shared/api/config';
import type { NewsResponse } from '../../../shared/types';
import { extractError, formatDate } from '../../../shared/utils';
import { ErrorBlock, LoadingBlock } from '../../../shared/ui';
import { renderMarkdown } from '../markdown';
import '../markdown.css';

export default function NewsDetailPage() {
  const { id } = useParams();
  const navigate = useNavigate();
  const [post, setPost] = useState<NewsResponse | null>(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState('');
  const [lightbox, setLightbox] = useState<string | null>(null);

  useEffect(() => {
    let active = true;
    (async () => {
      setLoading(true);
      setError('');
      try {
        const res = await client.get<NewsResponse>(ENDPOINTS.newsById(id!));
        if (active) setPost(res.data);
      } catch (err) {
        if (active) setError(extractError(err));
      } finally {
        if (active) setLoading(false);
      }
    })();
    return () => {
      active = false;
    };
  }, [id]);

  return (
    <div className="page" style={{ maxWidth: 820 }}>
      <button
        className="back-link"
        onClick={() => navigate('/news')}
        style={{ background: 'none', border: 'none', cursor: 'pointer' }}
      >
        ← К новостям
      </button>

      {error && <ErrorBlock message={error} />}
      {loading ? (
        <LoadingBlock />
      ) : !post ? (
        !error && <p className="empty-state">Новость не найдена</p>
      ) : (
        <article className="news-card news-detail">
          {post.coverImageUrl && (
            <div className="news-cover news-cover-lg">
              <img src={post.coverImageUrl} alt={post.title} />
            </div>
          )}
          <div className="news-body">
            <div className="news-meta">
              {post.pinned && <span className="badge badge-active">📌 Закреплено</span>}
              {post.category && <span className="news-chip">{post.category}</span>}
              <span className="news-date">{formatDate(post.publishedAt || post.publishAt || post.createdAt)}</span>
            </div>
            <h1 className="news-title" style={{ fontSize: 26 }}>{post.title}</h1>
            {post.summary && <p className="news-summary">{post.summary}</p>}
            <div className="md-content" dangerouslySetInnerHTML={{ __html: renderMarkdown(post.body) }} />
            {post.authorPublicName && <div className="news-author">— {post.authorPublicName}</div>}
            {post.galleryUrls && post.galleryUrls.length > 0 && (
              <div className="news-gallery">
                {post.galleryUrls.map((url, i) => (
                  <button
                    type="button"
                    className="news-gallery-thumb"
                    key={`${url}-${i}`}
                    onClick={() => setLightbox(url)}
                    title="Открыть изображение"
                  >
                    <img src={url} alt="" loading="lazy" />
                  </button>
                ))}
              </div>
            )}
          </div>
        </article>
      )}

      {lightbox && (
        <div className="lightbox" onClick={() => setLightbox(null)} role="dialog" aria-modal="true">
          <button type="button" className="lightbox-close" onClick={() => setLightbox(null)} aria-label="Закрыть">
            ×
          </button>
          <img src={lightbox} alt="" onClick={(e) => e.stopPropagation()} />
        </div>
      )}
    </div>
  );
}

import client from '../../shared/api/client';
import { ENDPOINTS } from '../../shared/api/config';

export interface NewsMediaResponse {
  url: string;
  filename: string;
  size: number;
}

/**
 * Upload a single image for a news post.
 *
 * The shared axios `client` does NOT force a JSON Content-Type, so we simply
 * hand axios the FormData and let it set the `multipart/form-data` header
 * together with the correct boundary. The auth interceptor adds the bearer.
 */
export async function uploadNewsMedia(file: File): Promise<string> {
  const formData = new FormData();
  formData.append('file', file);
  const res = await client.post<NewsMediaResponse>(ENDPOINTS.newsMedia, formData);
  return res.data.url;
}

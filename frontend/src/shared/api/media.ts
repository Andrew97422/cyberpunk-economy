import client from './client';
import { ENDPOINTS } from './config';

export interface MediaUploadResponse {
  url: string;
  filename: string;
  size: number;
}

/**
 * Upload a single image to the gateway media store and return its URL.
 * The shared axios `client` does not force a JSON content-type, so axios sets
 * the multipart boundary automatically for the FormData; the auth interceptor
 * attaches the bearer token.
 */
export async function uploadMedia(file: File): Promise<string> {
  const formData = new FormData();
  formData.append('file', file);
  const res = await client.post<MediaUploadResponse>(ENDPOINTS.media, formData);
  return res.data.url;
}

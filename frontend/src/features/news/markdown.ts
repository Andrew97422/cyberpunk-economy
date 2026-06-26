import { marked } from 'marked';
import DOMPurify from 'dompurify';

/**
 * Render markdown source to sanitized HTML suitable for `dangerouslySetInnerHTML`.
 *
 * - GFM enabled, single line breaks become <br> (`breaks: true`).
 * - Parsing is forced synchronous so we always get a string back.
 * - Output is sanitized with DOMPurify; we additionally allow `target`/`rel`
 *   attributes so links can safely open in new tabs. Images are allowed by the
 *   DOMPurify default profile.
 */
export function renderMarkdown(src: string): string {
  const raw = src ?? '';
  const html = marked.parse(raw, { gfm: true, breaks: true, async: false }) as string;
  return DOMPurify.sanitize(html, { ADD_ATTR: ['target', 'rel'] });
}

/**
 * Produce a plain-text approximation of markdown for feed teasers.
 * Regex-based and intentionally simple: it strips the common markdown markers
 * so cards never display raw syntax.
 */
export function stripMarkdown(src: string): string {
  let text = src ?? '';
  // Remove image syntax entirely: ![alt](url)
  text = text.replace(/!\[[^\]]*\]\([^)]*\)/g, '');
  // Convert links [text](url) -> text
  text = text.replace(/\[([^\]]*)\]\([^)]*\)/g, '$1');
  // Strip inline code / code fences markers
  text = text.replace(/`+/g, '');
  // Strip leading block markers (headings, blockquotes, list bullets) per line
  text = text.replace(/^[ \t]*[#>][ \t]*/gm, '');
  text = text.replace(/^[ \t]*[-*+][ \t]+/gm, '');
  // Strip emphasis markers (**bold**, *italic*, __, _)
  text = text.replace(/(\*\*|__|\*|_)/g, '');
  // Collapse all whitespace (including newlines) to single spaces
  text = text.replace(/\s+/g, ' ').trim();
  return text;
}

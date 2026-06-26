// Minimal client-side JWT helpers. We only read the (unverified) `exp` claim to drive
// the session countdown — the server remains the source of truth for validity.

export function getTokenExpMs(token: string | null | undefined): number | null {
  if (!token) return null;
  const parts = token.split('.');
  if (parts.length < 2) return null;
  try {
    let b64 = parts[1].replace(/-/g, '+').replace(/_/g, '/');
    b64 += '='.repeat((4 - (b64.length % 4)) % 4);
    const json = JSON.parse(decodeURIComponent(escape(atob(b64))));
    return typeof json.exp === 'number' ? json.exp * 1000 : null;
  } catch {
    return null;
  }
}

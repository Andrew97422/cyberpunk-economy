// Shared configuration for the e2e scripts.
//
// Resolution order for every value: explicit environment variable, then the
// repository's .env, then a safe default. Credentials have no default — the
// services themselves refuse to start without BOOTSTRAP_ADMIN_PASSWORD, so a
// hardcoded fallback here could only ever be wrong.
//
// The point is that `node e2e/run-scenarios.mjs` just works against a stack
// started from the same .env, with nothing to export by hand.

import { readFileSync } from 'node:fs';
import { dirname, join } from 'node:path';
import { fileURLToPath } from 'node:url';

const ROOT = join(dirname(fileURLToPath(import.meta.url)), '..');

/** Parse KEY=VALUE lines from the repo's .env. Missing file is not an error. */
function readDotEnv() {
  let raw;
  try {
    raw = readFileSync(join(ROOT, '.env'), 'utf8');
  } catch {
    return {};
  }

  const out = {};
  for (const line of raw.split(/\r?\n/)) {
    const trimmed = line.trim();
    if (!trimmed || trimmed.startsWith('#')) continue;
    const eq = trimmed.indexOf('=');
    if (eq < 1) continue;
    const key = trimmed.slice(0, eq).trim();
    let value = trimmed.slice(eq + 1).trim();
    if (value.length > 1 && ((value.startsWith('"') && value.endsWith('"')) ||
                             (value.startsWith("'") && value.endsWith("'")))) {
      value = value.slice(1, -1);
    }
    out[key] = value;
  }
  return out;
}

const dotEnv = readDotEnv();

export const BASE = process.env.BASE || 'http://localhost:8080/api';
export const ADMIN_NAME =
  process.env.ADMIN_NAME || dotEnv.BOOTSTRAP_ADMIN_NAME || 'admin';

export const ADMIN_PASSWORD =
  process.env.ADMIN_PASSWORD || dotEnv.BOOTSTRAP_ADMIN_PASSWORD || '';

/** Fail loudly and usefully rather than sending an empty password at the gateway. */
export function requireAdminPassword(script) {
  if (ADMIN_PASSWORD) return;

  console.error(
    `\nNo admin password found, so ${script} cannot authenticate.\n\n` +
      'Pick one:\n' +
      '  1. Create .env from .env.example (the stack reads the same file), or\n' +
      '  2. Pass it explicitly:\n' +
      '       PowerShell:  $env:ADMIN_PASSWORD = "..."; node e2e/' + script + '\n' +
      '       bash:        ADMIN_PASSWORD=... node e2e/' + script + '\n\n' +
      'The value is BOOTSTRAP_ADMIN_PASSWORD — the same one the stack was started with.\n'
  );
  process.exit(2);
}

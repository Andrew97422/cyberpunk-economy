import axios from 'axios';
import { API_BASE_URL } from './config';

const TOKEN_KEY = 'banking_api_human_ui_token';
const AUTH_KEY = 'banking_api_human_ui_auth';

export function getStoredToken() {
  return localStorage.getItem(TOKEN_KEY) || '';
}

export function getStoredAuth() {
  const raw = localStorage.getItem(AUTH_KEY);
  if (!raw) return null;
  try {
    return JSON.parse(raw);
  } catch {
    return null;
  }
}

export function storeAuth(auth: unknown, token: string) {
  localStorage.setItem(TOKEN_KEY, token);
  localStorage.setItem(AUTH_KEY, JSON.stringify(auth));
}

export function clearAuth() {
  localStorage.removeItem(TOKEN_KEY);
  localStorage.removeItem(AUTH_KEY);
}

export const api = axios.create({
  baseURL: API_BASE_URL,
});

api.interceptors.request.use((config) => {
  const token = getStoredToken();
  if (token) {
    config.headers = config.headers || {};
    config.headers.Authorization = `Bearer ${token}`;
  }
  return config;
});

import { Injectable } from '@nestjs/common';
import { createSign } from 'crypto';

export interface PushMessage {
  token: string;
  title: string;
  body?: string | null;
  data?: Record<string, string>;
}

export interface PushResult {
  ok: boolean;
  provider: string;
  disabled?: boolean;
  retryable?: boolean;
  reason?: string;
}

export interface PushProvider {
  readonly name: string;
  send(message: PushMessage): Promise<PushResult>;
}

@Injectable()
export class PushService {
  private providerInstance?: PushProvider;

  private provider(): PushProvider {
    if (this.providerInstance) return this.providerInstance;
    const projectId = (process.env.FCM_PROJECT_ID || '').trim();
    const clientEmail = (process.env.FCM_CLIENT_EMAIL || '').trim();
    const privateKey = normalizePrivateKey(process.env.FCM_PRIVATE_KEY || '');
    this.providerInstance = !projectId || !clientEmail || !privateKey
      ? new DisabledPushProvider()
      : new FcmHttpV1Provider(projectId, clientEmail, privateKey);
    return this.providerInstance;
  }

  async send(message: PushMessage): Promise<PushResult> {
    if (!message.token || message.token.length < 16) return { ok: false, provider: 'validation', reason: 'invalid-token' };
    if (!message.title.trim()) return { ok: false, provider: 'validation', reason: 'missing-title' };
    const sanitizedData: Record<string, string> = {};
    for (const [key, value] of Object.entries(message.data || {})) {
      if (/^[a-zA-Z0-9_.-]{1,64}$/.test(key)) sanitizedData[key] = String(value).slice(0, 1024);
    }
    return this.provider().send({
      ...message,
      title: message.title.slice(0, 160),
      body: message.body?.slice(0, 500),
      data: sanitizedData,
    });
  }
}

class DisabledPushProvider implements PushProvider {
  readonly name = 'disabled';
  async send(_message: PushMessage): Promise<PushResult> {
    return { ok: false, provider: this.name, disabled: true, reason: 'fcm-not-configured' };
  }
}

class FcmHttpV1Provider implements PushProvider {
  readonly name = 'fcm-http-v1';
  private accessToken?: { value: string; expiresAt: number };

  constructor(
    private readonly projectId: string,
    private readonly clientEmail: string,
    private readonly privateKey: string,
  ) {}

  async send(message: PushMessage): Promise<PushResult> {
    try {
      const token = await this.getAccessToken();
      const controller = new AbortController();
      const timer = setTimeout(() => controller.abort(), 10000);
      let response: Response;
      try {
        response = await fetch(`https://fcm.googleapis.com/v1/projects/${encodeURIComponent(this.projectId)}/messages:send`, {
          method: 'POST',
          headers: {
            authorization: `Bearer ${token}`,
            'content-type': 'application/json',
          },
          body: JSON.stringify({
            message: {
              token: message.token,
              notification: {
                title: message.title,
                ...(message.body ? { body: message.body } : {}),
              },
              data: message.data || {},
              android: { priority: 'high' },
            },
          }),
          signal: controller.signal,
        });
      } finally {
        clearTimeout(timer);
      }

      if (response.ok) return { ok: true, provider: this.name };

      const body = await safeJson(response);
      const status = String(body?.error?.status || '').toUpperCase();
      const reason = String(body?.error?.message || `http-${response.status}`).slice(0, 300);
      const invalidToken = response.status === 404 || status === 'NOT_FOUND' || status === 'INVALID_ARGUMENT';
      const retryable = response.status === 429 || response.status >= 500 || status === 'UNAVAILABLE' || status === 'RESOURCE_EXHAUSTED';
      return { ok: false, provider: this.name, retryable: retryable && !invalidToken, reason };
    } catch (error) {
      const reason = error instanceof Error ? error.message : 'fcm-send-failed';
      return { ok: false, provider: this.name, retryable: true, reason: reason.slice(0, 300) };
    }
  }

  private async getAccessToken(): Promise<string> {
    const now = Date.now();
    if (this.accessToken && this.accessToken.expiresAt - 60000 > now) return this.accessToken.value;

    const issuedAt = Math.floor(now / 1000);
    const assertion = this.signJwt({
      iss: this.clientEmail,
      scope: 'https://www.googleapis.com/auth/firebase.messaging',
      aud: 'https://oauth2.googleapis.com/token',
      iat: issuedAt,
      exp: issuedAt + 3600,
    });

    const controller = new AbortController();
    const timer = setTimeout(() => controller.abort(), 10000);
    let response: Response;
    try {
      response = await fetch('https://oauth2.googleapis.com/token', {
        method: 'POST',
        headers: { 'content-type': 'application/x-www-form-urlencoded' },
        body: new URLSearchParams({
          grant_type: 'urn:ietf:params:oauth:grant-type:jwt-bearer',
          assertion,
        }),
        signal: controller.signal,
      });
    } finally {
      clearTimeout(timer);
    }

    const body = await safeJson(response);
    if (!response.ok || typeof body?.access_token !== 'string') {
      throw new Error(String(body?.error_description || body?.error || `oauth-http-${response.status}`));
    }

    const expiresIn = Math.max(300, Number(body.expires_in || 3600));
    this.accessToken = { value: body.access_token, expiresAt: now + expiresIn * 1000 };
    return this.accessToken.value;
  }

  private signJwt(payload: Record<string, unknown>): string {
    const header = base64Url(JSON.stringify({ alg: 'RS256', typ: 'JWT' }));
    const body = base64Url(JSON.stringify(payload));
    const unsigned = `${header}.${body}`;
    const signer = createSign('RSA-SHA256');
    signer.update(unsigned);
    signer.end();
    const signature = signer.sign(this.privateKey);
    return `${unsigned}.${base64Url(signature)}`;
  }
}

function normalizePrivateKey(value: string): string {
  return value.trim().replace(/\\n/g, '\n');
}

function base64Url(value: string | Buffer): string {
  return Buffer.from(value).toString('base64url');
}

async function safeJson(response: Response): Promise<any> {
  try { return await response.json(); }
  catch { return {}; }
}

import { Injectable } from '@nestjs/common';

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
  private provider(): PushProvider {
    const projectId = (process.env.FCM_PROJECT_ID || '').trim();
    const clientEmail = (process.env.FCM_CLIENT_EMAIL || '').trim();
    const privateKey = (process.env.FCM_PRIVATE_KEY || '').trim();
    if (!projectId || !clientEmail || !privateKey) return new DisabledPushProvider();
    return new FcmContractProvider(projectId);
  }

  async send(message: PushMessage): Promise<PushResult> {
    if (!message.token || message.token.length < 16) return { ok: false, provider: 'validation', reason: 'invalid-token' };
    if (!message.title.trim()) return { ok: false, provider: 'validation', reason: 'missing-title' };
    const sanitizedData: Record<string, string> = {};
    for (const [key, value] of Object.entries(message.data || {})) {
      if (/^[a-zA-Z0-9_.-]{1,64}$/.test(key)) sanitizedData[key] = String(value).slice(0, 1024);
    }
    return this.provider().send({ ...message, title: message.title.slice(0, 160), body: message.body?.slice(0, 500), data: sanitizedData });
  }
}

class DisabledPushProvider implements PushProvider {
  readonly name = 'disabled';
  async send(_message: PushMessage): Promise<PushResult> {
    return { ok: false, provider: this.name, disabled: true, reason: 'fcm-not-configured' };
  }
}

class FcmContractProvider implements PushProvider {
  readonly name = 'fcm-http-v1';
  constructor(private readonly projectId: string) {}

  async send(_message: PushMessage): Promise<PushResult> {
    // Real FCM delivery needs an OAuth2 service-account signer.
    // Keep credentials server-side and never expose them to Android clients.
    return { ok: false, provider: this.name, disabled: true, reason: `oauth-signer-not-enabled:${this.projectId}` };
  }
}

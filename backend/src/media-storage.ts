import { BadRequestException, Injectable } from '@nestjs/common';
import { createHash, createHmac, randomBytes } from 'crypto';

export type MediaKind = 'IMAGE' | 'VIDEO' | 'VOICE' | 'FILE';

export interface MediaPrepareInput {
  chatId: string;
  userId: string;
  fileName: string;
  mimeType: string;
  byteSize: number;
  sha256?: string | null;
}

export interface MediaPreparedUpload {
  provider: string;
  objectKey: string;
  expiresAt: Date;
  uploadUrl?: string;
  method?: 'PUT' | 'POST';
  headers?: Record<string, string>;
}

export interface MediaStorageProvider {
  readonly name: string;
  prepareUpload(input: MediaPrepareInput, objectKey: string, expiresAt: Date): Promise<MediaPreparedUpload>;
  publicUrl(objectKey: string): string;
}

const IMAGE_LIMIT = 15 * 1024 * 1024;
const VIDEO_LIMIT = 100 * 1024 * 1024;
const VOICE_LIMIT = 25 * 1024 * 1024;
const FILE_LIMIT = 50 * 1024 * 1024;

const fileMimeAllowlist = new Set([
  'application/pdf',
  'application/zip',
  'application/x-zip-compressed',
  'text/plain',
  'application/msword',
  'application/vnd.openxmlformats-officedocument.wordprocessingml.document',
  'application/vnd.ms-excel',
  'application/vnd.openxmlformats-officedocument.spreadsheetml.sheet',
]);

export function classifyMedia(mimeInput: string): MediaKind {
  const mime = mimeInput.trim().toLowerCase();
  if (mime.startsWith('image/')) return 'IMAGE';
  if (mime.startsWith('video/')) return 'VIDEO';
  if (mime.startsWith('audio/')) return 'VOICE';
  if (fileMimeAllowlist.has(mime)) return 'FILE';
  throw new BadRequestException('Unsupported media type');
}

export function validateMedia(input: MediaPrepareInput): MediaKind {
  const kind = classifyMedia(input.mimeType);
  if (!input.chatId || !input.userId) throw new BadRequestException('Chat and user are required');
  const name = input.fileName.trim();
  if (!name || name.length > 240 || /[\u0000-\u001f]/.test(name)) throw new BadRequestException('Invalid file name');
  if (!Number.isSafeInteger(input.byteSize) || input.byteSize <= 0) throw new BadRequestException('Invalid media size');
  const limit = kind === 'IMAGE' ? IMAGE_LIMIT : kind === 'VIDEO' ? VIDEO_LIMIT : kind === 'VOICE' ? VOICE_LIMIT : FILE_LIMIT;
  if (input.byteSize > limit) throw new BadRequestException(`Media exceeds ${limit} byte limit`);
  if (input.sha256 && !/^[a-f0-9]{64}$/i.test(input.sha256)) throw new BadRequestException('Invalid SHA-256');
  return kind;
}

export function safeObjectKey(input: MediaPrepareInput): string {
  validateMedia(input);
  const rawExt = input.fileName.includes('.') ? input.fileName.split('.').pop()! : 'bin';
  const ext = rawExt.toLowerCase().replace(/[^a-z0-9]/g, '').slice(0, 10) || 'bin';
  const digest = createHash('sha256').update(`${input.chatId}:${input.userId}`).digest('hex').slice(0, 16);
  return `chat/${digest}/${Date.now()}-${randomBytes(18).toString('hex')}.${ext}`;
}

@Injectable()
export class MediaStorageService {
  private provider(): MediaStorageProvider {
    const configured = (process.env.MEDIA_PROVIDER || '').trim().toLowerCase();
    if (!configured) throw new BadRequestException('Media provider is not configured');
    if (configured === 'http-presigned') return new HttpPresignedProvider();
    throw new BadRequestException('Unsupported media provider');
  }

  async prepare(input: MediaPrepareInput): Promise<MediaPreparedUpload> {
    validateMedia(input);
    const key = safeObjectKey(input);
    const expiresAt = new Date(Date.now() + 15 * 60_000);
    return this.provider().prepareUpload(input, key, expiresAt);
  }

  publicUrl(objectKey: string): string {
    return this.provider().publicUrl(objectKey);
  }
}

type SignerResponse = {
  uploadUrl?: unknown;
  method?: unknown;
  headers?: unknown;
};

class HttpPresignedProvider implements MediaStorageProvider {
  readonly name = 'http-presigned';

  async prepareUpload(input: MediaPrepareInput, objectKey: string, expiresAt: Date): Promise<MediaPreparedUpload> {
    const endpoint = (process.env.MEDIA_SIGNING_ENDPOINT || '').trim();
    if (!/^https:\/\//i.test(endpoint)) throw new BadRequestException('Media signing endpoint must use HTTPS');

    const payload = JSON.stringify({
      objectKey,
      mimeType: input.mimeType.trim().toLowerCase(),
      byteSize: input.byteSize,
      sha256: input.sha256 || null,
      expiresAt: expiresAt.toISOString(),
    });
    const accessKey = (process.env.MEDIA_ACCESS_KEY || '').trim();
    const secret = process.env.MEDIA_SECRET_KEY || '';
    const headers: Record<string, string> = { 'content-type': 'application/json', 'accept': 'application/json' };
    if (accessKey) headers['x-aliqo-media-key'] = accessKey;
    if (secret) headers['x-aliqo-media-signature'] = createHmac('sha256', secret).update(payload).digest('hex');

    let response: Response;
    try {
      response = await fetch(endpoint, { method: 'POST', headers, body: payload, signal: AbortSignal.timeout(10_000) });
    } catch (_) {
      throw new BadRequestException('Media signing service unavailable');
    }
    if (!response.ok) throw new BadRequestException('Media signing service rejected request');

    let result: SignerResponse;
    try { result = await response.json() as SignerResponse; }
    catch (_) { throw new BadRequestException('Invalid media signing response'); }

    const uploadUrl = typeof result.uploadUrl === 'string' ? result.uploadUrl.trim() : '';
    if (!/^https:\/\//i.test(uploadUrl)) throw new BadRequestException('Signer returned an invalid upload URL');
    const methodRaw = typeof result.method === 'string' ? result.method.toUpperCase() : 'PUT';
    if (methodRaw !== 'PUT' && methodRaw !== 'POST') throw new BadRequestException('Signer returned an unsupported upload method');

    const uploadHeaders: Record<string, string> = {};
    if (result.headers && typeof result.headers === 'object' && !Array.isArray(result.headers)) {
      for (const [name, value] of Object.entries(result.headers as Record<string, unknown>)) {
        if (typeof value !== 'string') continue;
        const normalized = name.trim().toLowerCase();
        if (!normalized || normalized === 'authorization' || normalized === 'cookie' || normalized === 'host') continue;
        if (value.length <= 4096) uploadHeaders[name] = value;
      }
    }

    return { provider: this.name, objectKey, expiresAt, uploadUrl, method: methodRaw, headers: uploadHeaders };
  }

  publicUrl(objectKey: string): string {
    const base = (process.env.MEDIA_BASE_URL || '').trim().replace(/\/$/, '');
    if (!/^https:\/\//i.test(base)) throw new BadRequestException('Media base URL must use HTTPS');
    if (!/^[a-zA-Z0-9/_\-.]+$/.test(objectKey) || objectKey.includes('..')) throw new BadRequestException('Invalid object key');
    return `${base}/${objectKey}`;
  }
}

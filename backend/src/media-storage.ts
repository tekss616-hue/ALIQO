import { BadRequestException, Injectable } from '@nestjs/common';
import { createHash, randomBytes } from 'crypto';

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
    if (configured === 'http-presigned') return new HttpPresignedContractProvider();
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

class HttpPresignedContractProvider implements MediaStorageProvider {
  readonly name = 'http-presigned';

  async prepareUpload(_input: MediaPrepareInput, objectKey: string, expiresAt: Date): Promise<MediaPreparedUpload> {
    // This adapter is deliberately contract-only until a durable object-storage signer is configured.
    // MEDIA_SECRET_KEY is never returned or embedded in clients.
    const signingEndpoint = (process.env.MEDIA_SIGNING_ENDPOINT || '').trim();
    if (!signingEndpoint) throw new BadRequestException('Media signing endpoint is not configured');
    throw new BadRequestException('External media signing adapter is not enabled yet');
  }

  publicUrl(objectKey: string): string {
    const base = (process.env.MEDIA_BASE_URL || '').trim().replace(/\/$/, '');
    if (!base) throw new BadRequestException('Media base URL is not configured');
    if (!/^[a-zA-Z0-9/_\-.]+$/.test(objectKey) || objectKey.includes('..')) throw new BadRequestException('Invalid object key');
    return `${base}/${objectKey}`;
  }
}

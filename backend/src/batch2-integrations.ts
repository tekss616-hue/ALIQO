import { DevicePlatform, MediaUploadStatus, MessageType, PrismaClient } from '@prisma/client';
import { createHash } from 'crypto';
import { classifyMedia, MediaKind, MediaStorageService, validateMedia } from './media-storage';
import { PushService } from './push-provider';

const prisma = new PrismaClient();
const sha256 = (value: string) => createHash('sha256').update(value).digest('hex');
const publicUserSelect = { id: true, username: true, profile: true } as const;

export type RegisterDeviceInput = { token: string; platform?: DevicePlatform; appVersion?: string };
export type PrepareMediaInput = { chatId: string; fileName: string; mimeType: string; byteSize: number; sha256?: string };

export class Batch2Integrations {
  constructor(
    private readonly media = new MediaStorageService(),
    private readonly push = new PushService(),
  ) {}

  async registerDevice(userId: string, input: RegisterDeviceInput) {
    const token = input.token.trim();
    if (token.length < 16 || token.length > 4096) throw new Error('INVALID_DEVICE_TOKEN');
    const tokenHash = sha256(token);
    const row = await prisma.deviceToken.upsert({
      where: { tokenHash },
      create: { userId, tokenHash, tokenValue: token, platform: input.platform || DevicePlatform.ANDROID, appVersion: input.appVersion?.slice(0, 40) },
      update: { userId, tokenValue: token, platform: input.platform || DevicePlatform.ANDROID, appVersion: input.appVersion?.slice(0, 40), lastSeenAt: new Date(), revokedAt: null },
    });
    return { id: row.id, platform: row.platform, lastSeenAt: row.lastSeenAt };
  }

  async revokeDevice(userId: string, id: string) {
    const result = await prisma.deviceToken.updateMany({ where: { id, userId, revokedAt: null }, data: { revokedAt: new Date() } });
    return { ok: result.count > 0 };
  }

  async activeDevices(userId: string) {
    return prisma.deviceToken.findMany({ where: { userId, revokedAt: null }, select: { id: true, platform: true, appVersion: true, lastSeenAt: true, createdAt: true }, orderBy: { lastSeenAt: 'desc' } });
  }

  async prepareMedia(userId: string, input: PrepareMediaInput) {
    const member = await prisma.chatMember.findUnique({ where: { chatId_userId: { chatId: input.chatId, userId } } });
    if (!member) throw new Error('NOT_CHAT_MEMBER');
    const mediaInput = { chatId: input.chatId, userId, fileName: input.fileName, mimeType: input.mimeType, byteSize: input.byteSize, sha256: input.sha256?.toLowerCase() || null };
    const kind = validateMedia(mediaInput);
    const prepared = await this.media.prepare(mediaInput);
    const row = await prisma.mediaUpload.create({ data: { userId, chatId: input.chatId, status: MediaUploadStatus.PENDING, objectKey: prepared.objectKey, fileName: input.fileName.trim().slice(0, 240), mimeType: input.mimeType.trim().toLowerCase().slice(0, 120), byteSize: input.byteSize, sha256: input.sha256?.toLowerCase(), expiresAt: prepared.expiresAt } });
    return { uploadId: row.id, kind, ...prepared };
  }

  async markUploaded(userId: string, uploadId: string) {
    const row = await prisma.mediaUpload.findFirst({ where: { id: uploadId, userId } });
    if (!row) throw new Error('UPLOAD_NOT_FOUND');
    if (row.expiresAt <= new Date()) { await prisma.mediaUpload.update({ where: { id: uploadId }, data: { status: MediaUploadStatus.EXPIRED } }); throw new Error('UPLOAD_EXPIRED'); }
    if (row.status !== MediaUploadStatus.PENDING) throw new Error('UPLOAD_STATE_INVALID');
    const publicUrl = this.media.publicUrl(row.objectKey);
    return prisma.mediaUpload.update({ where: { id: uploadId }, data: { status: MediaUploadStatus.UPLOADED, publicUrl, uploadedAt: new Date() } });
  }

  async consumeUploaded(userId: string, chatId: string, uploadId: string) {
    const row = await prisma.mediaUpload.findFirst({ where: { id: uploadId, userId, chatId, status: MediaUploadStatus.UPLOADED } });
    if (!row || !row.publicUrl) throw new Error('UPLOAD_NOT_READY');
    const updated = await prisma.mediaUpload.updateMany({ where: { id: row.id, status: MediaUploadStatus.UPLOADED }, data: { status: MediaUploadStatus.ATTACHED, attachedAt: new Date() } });
    if (updated.count !== 1) throw new Error('UPLOAD_ALREADY_CONSUMED');
    return row;
  }

  async createMediaMessage(userId: string, chatId: string, upload: { publicUrl: string | null; fileName: string; mimeType: string; byteSize: number }, type: string, replyToId?: string, caption?: string) {
    if (!upload.publicUrl) throw new Error('UPLOAD_NOT_READY');
    if (replyToId && !await prisma.message.findFirst({ where: { id: replyToId, chatId, deletedAt: null } })) throw new Error('INVALID_REPLY');
    const messageType = MessageType[type as keyof typeof MessageType];
    if (!messageType || messageType === MessageType.TEXT || messageType === MessageType.SYSTEM) throw new Error('UPLOAD_NOT_READY');
    const message = await prisma.message.create({
      data: { chatId, senderId: userId, type: messageType, text: caption?.trim() || null, mediaUrl: upload.publicUrl, mediaName: upload.fileName, mediaMime: upload.mimeType, mediaSize: upload.byteSize, replyToId: replyToId || null },
      include: { sender: { select: publicUserSelect }, replyTo: { select: { id: true, text: true, type: true, senderId: true } }, reactions: true },
    });
    await prisma.chat.update({ where: { id: chatId }, data: { updatedAt: new Date() } });
    return message;
  }

  async pushToUser(userId: string, title: string, body: string, data: Record<string, string> = {}) {
    const devices = await prisma.deviceToken.findMany({ where: { userId, revokedAt: null }, select: { id: true, tokenValue: true } });
    const results: { id: string; ok: boolean; reason?: string }[] = [];
    for (const device of devices) {
      const result = await this.push.send({ token: device.tokenValue, title, body, data });
      results.push({ id: device.id, ok: result.ok, reason: result.reason });
      if (!result.ok && result.reason === 'invalid-token') await prisma.deviceToken.update({ where: { id: device.id }, data: { revokedAt: new Date() } });
    }
    return results;
  }
}

export const inferMessageType = (kind: MediaKind) => kind === 'IMAGE' ? 'IMAGE' : kind === 'VIDEO' ? 'VIDEO' : kind === 'VOICE' ? 'VOICE' : 'FILE';
export const detectMediaKind = (mimeType: string) => classifyMedia(mimeType);

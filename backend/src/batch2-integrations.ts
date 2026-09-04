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

  mediaCapabilities() {
    const provider = (process.env.MEDIA_PROVIDER || '').trim().toLowerCase();
    const signingEndpoint = (process.env.MEDIA_SIGNING_ENDPOINT || '').trim();
    const baseUrl = (process.env.MEDIA_BASE_URL || '').trim();
    const r2Endpoint = (process.env.MEDIA_R2_ENDPOINT || '').trim();
    const r2Bucket = (process.env.MEDIA_R2_BUCKET || '').trim();
    const r2Access = (process.env.MEDIA_R2_ACCESS_KEY_ID || '').trim();
    const r2Secret = process.env.MEDIA_R2_SECRET_ACCESS_KEY || '';
    const cloudName = (process.env.MEDIA_CLOUDINARY_CLOUD_NAME || '').trim();
    const cloudKey = (process.env.MEDIA_CLOUDINARY_API_KEY || '').trim();
    const cloudSecret = process.env.MEDIA_CLOUDINARY_API_SECRET || '';
    const imageKitPublicKey = (process.env.IMAGEKIT_PUBLIC_KEY || '').trim();
    const imageKitPrivateKey = process.env.IMAGEKIT_PRIVATE_KEY || '';
    const imageKitUrlEndpoint = (process.env.IMAGEKIT_URL_ENDPOINT || '').trim();
    const httpEnabled = provider === 'http-presigned' && /^https:\/\//i.test(signingEndpoint) && /^https:\/\//i.test(baseUrl);
    const r2Enabled = provider === 'r2' && /^https:\/\//i.test(r2Endpoint) && !!r2Bucket && !!r2Access && !!r2Secret && /^https:\/\//i.test(baseUrl);
    const cloudinaryEnabled = provider === 'cloudinary' && !!cloudName && !!cloudKey && !!cloudSecret;
    const imageKitEnabled = provider === 'imagekit' && !!imageKitPublicKey && !!imageKitPrivateKey && /^https:\/\//i.test(imageKitUrlEndpoint);
    const enabled = httpEnabled || r2Enabled || cloudinaryEnabled || imageKitEnabled;
    return {
      enabled,
      provider: enabled ? provider : null,
      kinds: ['IMAGE', 'VIDEO', 'VOICE', 'FILE'],
      maxBytes: {
        IMAGE: 15 * 1024 * 1024,
        VIDEO: 100 * 1024 * 1024,
        VOICE: 25 * 1024 * 1024,
        FILE: provider === 'imagekit' ? 25 * 1024 * 1024 : 50 * 1024 * 1024,
      },
    };
  }

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
    if (row.expiresAt <= new Date()) {
      await prisma.mediaUpload.updateMany({ where: { id: uploadId, status: MediaUploadStatus.PENDING }, data: { status: MediaUploadStatus.EXPIRED } });
      throw new Error('UPLOAD_EXPIRED');
    }
    if (row.status !== MediaUploadStatus.PENDING) throw new Error('UPLOAD_STATE_INVALID');
    const publicUrl = this.media.publicUrl(row.objectKey, row.mimeType);
    const updated = await prisma.mediaUpload.updateMany({ where: { id: uploadId, userId, status: MediaUploadStatus.PENDING }, data: { status: MediaUploadStatus.UPLOADED, publicUrl, uploadedAt: new Date() } });
    if (updated.count !== 1) throw new Error('UPLOAD_STATE_INVALID');
    return prisma.mediaUpload.findUniqueOrThrow({ where: { id: uploadId } });
  }

  async attachUploadedMessage(userId: string, chatId: string, uploadId: string, replyToId?: string, caption?: string) {
    return prisma.$transaction(async tx => {
      const member = await tx.chatMember.findUnique({ where: { chatId_userId: { chatId, userId } } });
      if (!member) throw new Error('NOT_CHAT_MEMBER');

      const upload = await tx.mediaUpload.findFirst({ where: { id: uploadId, userId, chatId } });
      if (!upload) throw new Error('UPLOAD_NOT_FOUND');
      if (upload.expiresAt <= new Date()) {
        if (upload.status === MediaUploadStatus.PENDING || upload.status === MediaUploadStatus.UPLOADED) {
          await tx.mediaUpload.updateMany({ where: { id: upload.id, status: { in: [MediaUploadStatus.PENDING, MediaUploadStatus.UPLOADED] } }, data: { status: MediaUploadStatus.EXPIRED } });
        }
        throw new Error('UPLOAD_EXPIRED');
      }
      if (upload.status !== MediaUploadStatus.UPLOADED || !upload.publicUrl) throw new Error('UPLOAD_NOT_READY');
      if (replyToId && !await tx.message.findFirst({ where: { id: replyToId, chatId, deletedAt: null } })) throw new Error('INVALID_REPLY');

      const kind = classifyMedia(upload.mimeType);
      const type = kind === 'IMAGE' ? MessageType.IMAGE : kind === 'VIDEO' ? MessageType.VIDEO : kind === 'VOICE' ? MessageType.VOICE : MessageType.FILE;
      const consumed = await tx.mediaUpload.updateMany({ where: { id: upload.id, userId, chatId, status: MediaUploadStatus.UPLOADED }, data: { status: MediaUploadStatus.ATTACHED, attachedAt: new Date() } });
      if (consumed.count !== 1) throw new Error('UPLOAD_ALREADY_CONSUMED');

      const message = await tx.message.create({
        data: { chatId, senderId: userId, type, text: caption?.trim() || null, mediaUrl: upload.publicUrl, mediaName: upload.fileName, mediaMime: upload.mimeType, mediaSize: upload.byteSize, replyToId: replyToId || null },
        include: { sender: { select: publicUserSelect }, replyTo: { select: { id: true, text: true, type: true, senderId: true } }, reactions: true },
      });
      await tx.chat.update({ where: { id: chatId }, data: { updatedAt: new Date() } });

      const recipients = await tx.chatMember.findMany({ where: { chatId, userId: { not: userId } }, select: { userId: true } });
      if (recipients.length) {
        const senderName = message.sender.profile?.displayName || message.sender.username;
        const body = message.text || (type === MessageType.IMAGE ? 'صورة' : type === MessageType.VIDEO ? 'فيديو' : type === MessageType.VOICE ? 'رسالة صوتية' : 'ملف');
        await tx.notification.createMany({
          data: recipients.map(recipient => ({
            userId: recipient.userId,
            type: 'CHAT_MESSAGE',
            title: senderName,
            body,
            dataJson: JSON.stringify({ chatId, messageId: message.id, media: true }),
          })),
        });
      }
      return { message, recipientIds: recipients.map(recipient => recipient.userId) };
    }, { isolationLevel: 'Serializable' });
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
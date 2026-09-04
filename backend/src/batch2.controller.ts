import { BadRequestException, Body, Controller, Delete, Get, Param, Post, Req, UseGuards } from '@nestjs/common';
import { AuthGuard } from '@nestjs/passport';
import { DevicePlatform } from '@prisma/client';
import { IsEnum, IsInt, IsOptional, IsString, Max, MaxLength, Min, MinLength, Matches } from 'class-validator';
import { Batch2Integrations } from './batch2-integrations';
import { batch2Events } from './batch2-events';

class RegisterDeviceDto {
  @IsString() @MinLength(16) @MaxLength(4096) token!: string;
  @IsOptional() @IsEnum(DevicePlatform) platform?: DevicePlatform;
  @IsOptional() @IsString() @MaxLength(40) appVersion?: string;
}

class PrepareMediaDto {
  @IsString() chatId!: string;
  @IsString() @MinLength(1) @MaxLength(240) fileName!: string;
  @IsString() @MinLength(3) @MaxLength(120) mimeType!: string;
  @IsInt() @Min(1) @Max(100 * 1024 * 1024) byteSize!: number;
  @IsOptional() @IsString() @Matches(/^[a-fA-F0-9]{64}$/) sha256?: string;
}

class AttachMediaDto {
  @IsString() uploadId!: string;
  @IsOptional() @IsString() replyToId?: string;
  @IsOptional() @IsString() @MaxLength(4000) caption?: string;
}

@Controller() @UseGuards(AuthGuard('jwt'))
export class Batch2Controller {
  private readonly integrations = new Batch2Integrations();

  private translate(error: unknown): never {
    const code = error instanceof Error ? error.message : 'INTEGRATION_ERROR';
    if (code === 'NOT_CHAT_MEMBER') throw new BadRequestException('Not a chat member');
    if (code === 'UPLOAD_NOT_FOUND') throw new BadRequestException('Upload not found');
    if (code === 'UPLOAD_EXPIRED') throw new BadRequestException('Upload expired');
    if (code === 'UPLOAD_STATE_INVALID') throw new BadRequestException('Upload state invalid');
    if (code === 'UPLOAD_NOT_READY') throw new BadRequestException('Upload not ready');
    if (code === 'UPLOAD_ALREADY_CONSUMED') throw new BadRequestException('Upload already attached');
    if (code === 'INVALID_REPLY') throw new BadRequestException('Invalid reply');
    if (code === 'INVALID_MEDIA_URL') throw new BadRequestException('Invalid media URL');
    if (code === 'INVALID_DEVICE_TOKEN') throw new BadRequestException('Invalid device token');
    throw error;
  }

  @Post('devices/register')
  async registerDevice(@Req() req: any, @Body() dto: RegisterDeviceDto) {
    try { return await this.integrations.registerDevice(req.user.id, dto); }
    catch (error) { this.translate(error); }
  }

  @Get('devices')
  async devices(@Req() req: any) { return this.integrations.activeDevices(req.user.id); }

  @Delete('devices/:id')
  async revokeDevice(@Req() req: any, @Param('id') id: string) {
    return this.integrations.revokeDevice(req.user.id, id);
  }

  @Get('media/capabilities')
  mediaCapabilities() { return this.integrations.mediaCapabilities(); }

  @Post('media/prepare')
  async prepareMedia(@Req() req: any, @Body() dto: PrepareMediaDto) {
    try { return await this.integrations.prepareMedia(req.user.id, dto); }
    catch (error) { this.translate(error); }
  }

  @Post('media/:id/complete')
  async completeMedia(@Req() req: any, @Param('id') id: string) {
    try {
      const row = await this.integrations.markUploaded(req.user.id, id);
      return { id: row.id, status: row.status, chatId: row.chatId, fileName: row.fileName, mimeType: row.mimeType, byteSize: row.byteSize, publicUrl: row.publicUrl, uploadedAt: row.uploadedAt };
    } catch (error) { this.translate(error); }
  }

  @Post('chats/:chatId/media-message')
  async attachMedia(@Req() req: any, @Param('chatId') chatId: string, @Body() dto: AttachMediaDto) {
    try {
      const result = await this.integrations.attachUploadedMessage(req.user.id, chatId, dto.uploadId, dto.replyToId, dto.caption);
      batch2Events.emitSecureMediaAttached({ chatId, senderId: req.user.id, recipientIds: result.recipientIds, message: result.message });
      return result.message;
    } catch (error) { this.translate(error); }
  }
}

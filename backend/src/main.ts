import 'reflect-metadata';
import { BadRequestException, Body, ConflictException, Controller, Delete, ForbiddenException, Get, Injectable, Module, NotFoundException, Param, Patch, Post, Query, Req, UnauthorizedException, UseGuards, ValidationPipe } from '@nestjs/common';
import { NestFactory } from '@nestjs/core';
import { JwtModule, JwtService } from '@nestjs/jwt';
import { AuthGuard, PassportModule } from '@nestjs/passport';
import { PassportStrategy } from '@nestjs/passport';
import { ExtractJwt, Strategy } from 'passport-jwt';
import { ChatType, FriendshipStatus, MatchMode, MatchQueueStatus, MatchSessionStatus, MessageType, PrismaClient, RoomVisibility, UserRole } from '@prisma/client';
import * as argon2 from 'argon2';
import { IsArray, IsEmail, IsEnum, IsInt, IsOptional, IsString, Length, Matches, Max, MaxLength, Min, MinLength } from 'class-validator';
import { createHash, randomBytes } from 'crypto';
import { ConnectedSocket, MessageBody, OnGatewayConnection, OnGatewayDisconnect, OnGatewayInit, SubscribeMessage, WebSocketGateway } from '@nestjs/websockets';
import { Server, Socket } from 'socket.io';
import { Batch2Controller } from './batch2.controller';

const prisma = new PrismaClient();
const hashToken = (value: string) => createHash('sha256').update(value).digest('hex');
const publicUserSelect = { id: true, username: true, profile: true } as const;

class RegisterDto { @IsEmail() email!: string; @IsString() @Matches(/^[a-zA-Z0-9_]{3,24}$/) username!: string; @IsString() @MinLength(8) @MaxLength(72) password!: string; @IsString() @Length(1, 60) displayName!: string; }
class LoginDto { @IsEmail() email!: string; @IsString() password!: string; }
class RefreshDto { @IsString() refreshToken!: string; }
class ForgotPasswordDto { @IsEmail() email!: string; }
class ResetPasswordDto { @IsString() token!: string; @IsString() @MinLength(8) @MaxLength(72) password!: string; }
class UpdateProfileDto { @IsOptional() @IsString() @Length(1, 60) displayName?: string; @IsOptional() @IsString() @MaxLength(280) bio?: string; @IsOptional() @IsString() @MaxLength(500) avatarUrl?: string; }
class CreateDirectDto { @IsString() userId!: string; }
class CreateGroupDto { @IsString() @Length(1, 80) title!: string; @IsArray() memberIds!: string[]; }
class RenameGroupDto { @IsString() @Length(1, 80) title!: string; }
class GroupMemberDto { @IsString() userId!: string; }
class SendMessageDto { @IsOptional() @IsEnum(MessageType) type?: MessageType; @IsOptional() @IsString() @MaxLength(4000) text?: string; @IsOptional() @IsString() @MaxLength(1000) mediaUrl?: string; @IsOptional() @IsString() @MaxLength(240) mediaName?: string; @IsOptional() @IsString() @MaxLength(120) mediaMime?: string; @IsOptional() @IsInt() @Min(0) @Max(100000000) mediaSize?: number; @IsOptional() @IsString() replyToId?: string; }
class EditMessageDto { @IsString() @Length(1, 4000) text!: string; }
class ReactionDto { @IsString() @Length(1, 32) emoji!: string; }
class ReadDto { @IsOptional() @IsString() messageId?: string; }
class WsChatDto { @IsString() chatId!: string; }
class MatchQueueDto { @IsEnum(MatchMode) mode!: MatchMode; }
class CreateRoomDto { @IsString() @Length(2, 80) name!: string; @IsOptional() @IsString() @MaxLength(280) description?: string; @IsOptional() @IsInt() @Min(5) @Max(200) capacity?: number; }

@Injectable()
class AuthService {
  constructor(private readonly jwt: JwtService) {}
  private async issueTokens(userId: string, role: UserRole) {
    const ttl = Number(process.env.ACCESS_TOKEN_TTL_SECONDS || 900);
    const accessToken = await this.jwt.signAsync({ sub: userId, role }, { secret: process.env.JWT_ACCESS_SECRET, expiresIn: ttl });
    const rawRefresh = randomBytes(48).toString('base64url');
    const days = Number(process.env.REFRESH_TOKEN_TTL_DAYS || 30);
    await prisma.refreshToken.create({ data: { userId, tokenHash: hashToken(rawRefresh), expiresAt: new Date(Date.now() + days * 86400000) } });
    return { accessToken, refreshToken: rawRefresh };
  }
  safeUser(user: any) { const { passwordHash, ...safe } = user; return safe; }
  async register(dto: RegisterDto) {
    const email = dto.email.trim().toLowerCase(), username = dto.username.trim().toLowerCase();
    if (await prisma.user.findFirst({ where: { OR: [{ email }, { username }] } })) throw new ConflictException('Email or username already in use');
    const role = email === process.env.PRIMARY_ADMIN_EMAIL?.trim().toLowerCase() ? UserRole.PRIMARY_ADMIN : UserRole.USER;
    const user = await prisma.user.create({ data: { email, username, passwordHash: await argon2.hash(dto.password), role, profile: { create: { displayName: dto.displayName.trim() } } }, include: { profile: true } });
    return { user: this.safeUser(user), ...(await this.issueTokens(user.id, user.role)) };
  }
  async login(dto: LoginDto) {
    const user = await prisma.user.findUnique({ where: { email: dto.email.trim().toLowerCase() }, include: { profile: true } });
    if (!user || !user.isActive || user.deletedAt || !(await argon2.verify(user.passwordHash, dto.password))) throw new UnauthorizedException('Invalid credentials');
    return { user: this.safeUser(user), ...(await this.issueTokens(user.id, user.role)) };
  }
  async refresh(raw: string) {
    const row = await prisma.refreshToken.findUnique({ where: { tokenHash: hashToken(raw) }, include: { user: true } });
    if (!row || row.revokedAt || row.expiresAt <= new Date() || !row.user.isActive || row.user.deletedAt) throw new UnauthorizedException('Invalid refresh token');
    await prisma.refreshToken.update({ where: { id: row.id }, data: { revokedAt: new Date() } });
    return this.issueTokens(row.userId, row.user.role);
  }
  async logout(raw: string) { await prisma.refreshToken.updateMany({ where: { tokenHash: hashToken(raw), revokedAt: null }, data: { revokedAt: new Date() } }); return { ok: true }; }
  async forgotPassword(emailInput: string) {
    const user = await prisma.user.findUnique({ where: { email: emailInput.trim().toLowerCase() } });
    if (!user || !user.isActive || user.deletedAt) return { ok: true };
    await prisma.passwordResetToken.deleteMany({ where: { userId: user.id, usedAt: null } });
    const raw = randomBytes(32).toString('base64url');
    await prisma.passwordResetToken.create({ data: { userId: user.id, tokenHash: hashToken(raw), expiresAt: new Date(Date.now() + 15 * 60000) } });
    return process.env.NODE_ENV === 'production' ? { ok: true } : { ok: true, developmentResetToken: raw };
  }
  async resetPassword(raw: string, password: string) {
    const token = await prisma.passwordResetToken.findUnique({ where: { tokenHash: hashToken(raw) } });
    if (!token || token.usedAt || token.expiresAt <= new Date()) throw new BadRequestException('Invalid or expired reset token');
    await prisma.$transaction([prisma.user.update({ where: { id: token.userId }, data: { passwordHash: await argon2.hash(password) } }), prisma.passwordResetToken.update({ where: { id: token.id }, data: { usedAt: new Date() } }), prisma.refreshToken.updateMany({ where: { userId: token.userId, revokedAt: null }, data: { revokedAt: new Date() } })]);
    return { ok: true };
  }
}

@Injectable()
class JwtStrategy extends PassportStrategy(Strategy) {
  constructor() { super({ jwtFromRequest: ExtractJwt.fromAuthHeaderAsBearerToken(), ignoreExpiration: false, secretOrKey: process.env.JWT_ACCESS_SECRET || 'dev-only-change-me' }); }
  async validate(payload: any) { const user = await prisma.user.findUnique({ where: { id: payload.sub } }); if (!user || !user.isActive || user.deletedAt) throw new UnauthorizedException(); return { id: user.id, role: user.role }; }
}
const JwtAuthGuard = AuthGuard('jwt');

@WebSocketGateway({ cors: { origin: true, credentials: true }, transports: ['websocket', 'polling'] })
@Injectable()
class RealtimeGateway implements OnGatewayInit, OnGatewayConnection, OnGatewayDisconnect {
  private server?: Server;
  constructor(private readonly jwt: JwtService) {}
  afterInit(server: Server) { this.server = server; }
  async handleConnection(client: Socket) {
    try {
      const header = client.handshake.headers.authorization;
      const authToken = typeof client.handshake.auth?.token === 'string' ? client.handshake.auth.token : undefined;
      const token = authToken || (typeof header === 'string' ? header.replace(/^Bearer\s+/i, '') : '');
      if (!token) return client.disconnect(true);
      const payload = await this.jwt.verifyAsync(token, { secret: process.env.JWT_ACCESS_SECRET });
      const user = await prisma.user.findFirst({ where: { id: payload.sub, isActive: true, deletedAt: null }, select: { id: true } });
      if (!user) return client.disconnect(true);
      client.data.userId = user.id; await client.join(`user:${user.id}`);
      await prisma.profile.updateMany({ where: { userId: user.id }, data: { isOnline: true } });
      this.server?.emit('presence:changed', { userId: user.id, isOnline: true, at: Date.now() });
    } catch (_) { client.disconnect(true); }
  }
  async handleDisconnect(client: Socket) {
    const userId = client.data?.userId; if (!userId) return;
    const sockets = await this.server?.in(`user:${userId}`).fetchSockets();
    if ((sockets?.length || 0) === 0) { await prisma.profile.updateMany({ where: { userId }, data: { isOnline: false, lastSeenAt: new Date() } }); this.server?.emit('presence:changed', { userId, isOnline: false, at: Date.now() }); }
  }
  @SubscribeMessage('chat:join') async join(@ConnectedSocket() client: Socket, @MessageBody() dto: WsChatDto) { const member = await prisma.chatMember.findUnique({ where: { chatId_userId: { chatId: dto.chatId, userId: client.data.userId } } }); if (member) await client.join(`chat:${dto.chatId}`); return { ok: !!member }; }
  @SubscribeMessage('chat:leave') async leave(@ConnectedSocket() client: Socket, @MessageBody() dto: WsChatDto) { await client.leave(`chat:${dto.chatId}`); return { ok: true }; }
  @SubscribeMessage('typing:start') async typingStart(@ConnectedSocket() client: Socket, @MessageBody() dto: WsChatDto) { return this.typing(client, dto.chatId, true); }
  @SubscribeMessage('typing:stop') async typingStop(@ConnectedSocket() client: Socket, @MessageBody() dto: WsChatDto) { return this.typing(client, dto.chatId, false); }
  private async typing(client: Socket, chatId: string, isTyping: boolean) { const userId = client.data.userId; const member = await prisma.chatMember.findUnique({ where: { chatId_userId: { chatId, userId } } }); if (!member) return { ok: false }; client.to(`chat:${chatId}`).emit('typing:changed', { chatId, userId, isTyping, at: Date.now() }); return { ok: true }; }
  emitUser(userId: string, event: string, payload: any) { this.server?.to(`user:${userId}`).emit(event, payload); }
  emitChat(chatId: string, event: string, payload: any) { this.server?.to(`chat:${chatId}`).emit(event, payload); }
  emitFriendState(userId: string, type: string, actorId?: string) { this.emitUser(userId, 'friends:changed', { type, actorId: actorId || null, at: Date.now() }); }
}

async function publicChat(chatId: string) { return prisma.chat.findUnique({ where: { id: chatId }, include: { members: { include: { user: { select: publicUserSelect } } }, messages: { where: { deletedAt: null }, orderBy: { createdAt: 'desc' }, take: 1, include: { sender: { select: publicUserSelect }, reactions: true } } } }); }

@Controller('health') class HealthController { @Get() async health() { await prisma.$queryRaw`SELECT 1`; return { ok: true, service: 'aliqo-backend' }; } }
@Controller('auth') class AuthController { constructor(private auth: AuthService) {} @Post('register') register(@Body() dto: RegisterDto) { return this.auth.register(dto); } @Post('login') login(@Body() dto: LoginDto) { return this.auth.login(dto); } @Post('refresh') refresh(@Body() dto: RefreshDto) { return this.auth.refresh(dto.refreshToken); } @Post('logout') logout(@Body() dto: RefreshDto) { return this.auth.logout(dto.refreshToken); } @Post('forgot-password') forgot(@Body() dto: ForgotPasswordDto) { return this.auth.forgotPassword(dto.email); } @Post('reset-password') reset(@Body() dto: ResetPasswordDto) { return this.auth.resetPassword(dto.token, dto.password); } }

@Controller('users') @UseGuards(JwtAuthGuard)
class UsersController {
  constructor(private readonly realtime: RealtimeGateway) {}
  @Get('me') async me(@Req() req: any) { const user = await prisma.user.findUnique({ where: { id: req.user.id }, include: { profile: true } }); if (!user) throw new NotFoundException(); const { passwordHash, ...safe } = user; return safe; }
  @Patch('me/profile') async update(@Req() req: any, @Body() dto: UpdateProfileDto) { const profile = await prisma.profile.update({ where: { userId: req.user.id }, data: dto }); const links = await prisma.friendship.findMany({ where: { status: FriendshipStatus.ACCEPTED, OR: [{ requesterId: req.user.id }, { addresseeId: req.user.id }] }, select: { requesterId: true, addresseeId: true } }); for (const link of links) this.realtime.emitUser(link.requesterId === req.user.id ? link.addresseeId : link.requesterId, 'profile:updated', { userId: req.user.id, profile, at: Date.now() }); return profile; }
  @Get('search') async search(@Req() req: any, @Query('q') q = '') { const term = q.trim().toLowerCase(); if (term.length < 2) return []; return prisma.user.findMany({ where: { id: { not: req.user.id }, isActive: true, deletedAt: null, username: { contains: term, mode: 'insensitive' }, blocksReceived: { none: { blockerId: req.user.id } }, blocksCreated: { none: { blockedId: req.user.id } } }, select: publicUserSelect, take: 20 }); }
  @Delete('me') async remove(@Req() req: any) { await prisma.$transaction([prisma.refreshToken.updateMany({ where: { userId: req.user.id, revokedAt: null }, data: { revokedAt: new Date() } }), prisma.passwordResetToken.deleteMany({ where: { userId: req.user.id } }), prisma.matchQueueEntry.updateMany({ where: { userId: req.user.id, status: MatchQueueStatus.WAITING }, data: { status: MatchQueueStatus.CANCELLED } }), prisma.user.update({ where: { id: req.user.id }, data: { isActive: false, deletedAt: new Date(), email: `deleted-${req.user.id}@invalid.local`, username: `deleted_${req.user.id.slice(-12)}` } })]); return { ok: true }; }
}

@Controller('friends') @UseGuards(JwtAuthGuard)
class FriendsController {
  constructor(private readonly realtime: RealtimeGateway) {}
  private publicUser(user: any) { return { id: user.id, username: user.username, profile: user.profile }; }
  private emitBoth(a: string, b: string, type: string, actorId: string) { this.realtime.emitFriendState(a, type, actorId); this.realtime.emitFriendState(b, type, actorId); }
  @Get() async list(@Req() req: any) { const rows = await prisma.friendship.findMany({ where: { status: FriendshipStatus.ACCEPTED, OR: [{ requesterId: req.user.id }, { addresseeId: req.user.id }] }, include: { requester: { include: { profile: true } }, addressee: { include: { profile: true } } }, orderBy: { updatedAt: 'desc' } }); const blocks = await prisma.blockedUser.findMany({ where: { OR: [{ blockerId: req.user.id }, { blockedId: req.user.id }] }, select: { blockerId: true, blockedId: true } }); const blockedIds = new Set(blocks.map(b => b.blockerId === req.user.id ? b.blockedId : b.blockerId)); return rows.map(r => r.requesterId === req.user.id ? r.addressee : r.requester).filter(u => !blockedIds.has(u.id)).map(u => this.publicUser(u)); }
  @Get('requests') async requests(@Req() req: any) { const rows = await prisma.friendship.findMany({ where: { addresseeId: req.user.id, status: FriendshipStatus.PENDING }, include: { requester: { include: { profile: true } } }, orderBy: { createdAt: 'desc' } }); return rows.map(r => ({ id: r.id, createdAt: r.createdAt, user: this.publicUser(r.requester) })); }
  @Get('blocked') async blocked(@Req() req: any) { const rows = await prisma.blockedUser.findMany({ where: { blockerId: req.user.id }, include: { blocked: { include: { profile: true } } }, orderBy: { createdAt: 'desc' } }); return rows.map(r => this.publicUser(r.blocked)); }
  @Post(':userId/request') async request(@Req() req: any, @Param('userId') target: string) { if (target === req.user.id) throw new BadRequestException('Cannot friend yourself'); const blocked = await prisma.blockedUser.findFirst({ where: { OR: [{ blockerId: req.user.id, blockedId: target }, { blockerId: target, blockedId: req.user.id }] } }); if (blocked) throw new BadRequestException('Friend request unavailable'); if (!await prisma.user.findFirst({ where: { id: target, isActive: true, deletedAt: null } })) throw new NotFoundException(); const accepted = await prisma.friendship.findFirst({ where: { status: FriendshipStatus.ACCEPTED, OR: [{ requesterId: req.user.id, addresseeId: target }, { requesterId: target, addresseeId: req.user.id }] } }); if (accepted) return accepted; const inverse = await prisma.friendship.findUnique({ where: { requesterId_addresseeId: { requesterId: target, addresseeId: req.user.id } } }); if (inverse?.status === FriendshipStatus.PENDING) { const row = await prisma.friendship.update({ where: { id: inverse.id }, data: { status: FriendshipStatus.ACCEPTED } }); this.emitBoth(req.user.id, target, 'accepted', req.user.id); return row; } const row = await prisma.friendship.upsert({ where: { requesterId_addresseeId: { requesterId: req.user.id, addresseeId: target } }, create: { requesterId: req.user.id, addresseeId: target }, update: { status: FriendshipStatus.PENDING } }); this.emitBoth(req.user.id, target, 'request', req.user.id); return row; }
  @Post(':requestId/accept') async accept(@Req() req: any, @Param('requestId') id: string) { const row = await prisma.friendship.findFirst({ where: { id, addresseeId: req.user.id, status: FriendshipStatus.PENDING } }); if (!row) throw new NotFoundException(); const updated = await prisma.friendship.update({ where: { id }, data: { status: FriendshipStatus.ACCEPTED } }); this.emitBoth(row.requesterId, row.addresseeId, 'accepted', req.user.id); return updated; }
  @Post(':requestId/reject') async reject(@Req() req: any, @Param('requestId') id: string) { const row = await prisma.friendship.findFirst({ where: { id, addresseeId: req.user.id, status: FriendshipStatus.PENDING } }); if (!row) throw new NotFoundException(); await prisma.friendship.delete({ where: { id } }); this.emitBoth(row.requesterId, row.addresseeId, 'rejected', req.user.id); return { ok: true }; }
  @Delete(':userId') async remove(@Req() req: any, @Param('userId') userId: string) { await prisma.friendship.deleteMany({ where: { OR: [{ requesterId: req.user.id, addresseeId: userId }, { requesterId: userId, addresseeId: req.user.id }] } }); this.emitBoth(req.user.id, userId, 'removed', req.user.id); return { ok: true }; }
  @Post(':userId/block') async block(@Req() req: any, @Param('userId') userId: string) { if (userId === req.user.id) throw new BadRequestException(); if (!await prisma.user.findFirst({ where: { id: userId, isActive: true, deletedAt: null } })) throw new NotFoundException(); await prisma.blockedUser.upsert({ where: { blockerId_blockedId: { blockerId: req.user.id, blockedId: userId } }, create: { blockerId: req.user.id, blockedId: userId }, update: {} }); await prisma.matchQueueEntry.updateMany({ where: { userId: req.user.id, status: MatchQueueStatus.WAITING }, data: { status: MatchQueueStatus.CANCELLED } }); this.emitBoth(req.user.id, userId, 'blocked', req.user.id); return { ok: true }; }
  @Delete(':userId/block') async unblock(@Req() req: any, @Param('userId') userId: string) { await prisma.blockedUser.deleteMany({ where: { blockerId: req.user.id, blockedId: userId } }); this.emitBoth(req.user.id, userId, 'unblocked', req.user.id); return { ok: true }; }
}

@Controller('chats') @UseGuards(JwtAuthGuard)
class ChatsController {
  constructor(private readonly realtime: RealtimeGateway) {}
  private async member(chatId: string, userId: string) { const member = await prisma.chatMember.findUnique({ where: { chatId_userId: { chatId, userId } } }); if (!member) throw new ForbiddenException('Not a chat member'); return member; }
  private async notifyMembers(chatId: string, senderId: string, title: string, body: string) { const members = await prisma.chatMember.findMany({ where: { chatId, userId: { not: senderId } }, select: { userId: true } }); if (members.length) await prisma.notification.createMany({ data: members.map(m => ({ userId: m.userId, type: 'CHAT_MESSAGE', title, body, dataJson: JSON.stringify({ chatId }) })) }); for (const m of members) this.realtime.emitUser(m.userId, 'notifications:changed', { chatId, at: Date.now() }); }
  @Get() async list(@Req() req: any) { return prisma.chat.findMany({ where: { members: { some: { userId: req.user.id } } }, orderBy: { updatedAt: 'desc' }, include: { members: { include: { user: { select: publicUserSelect } } }, messages: { where: { deletedAt: null }, orderBy: { createdAt: 'desc' }, take: 1, include: { sender: { select: publicUserSelect }, reactions: true } } } }); }
  @Post('direct') async direct(@Req() req: any, @Body() dto: CreateDirectDto) { if (dto.userId === req.user.id) throw new BadRequestException(); const friend = await prisma.friendship.findFirst({ where: { status: FriendshipStatus.ACCEPTED, OR: [{ requesterId: req.user.id, addresseeId: dto.userId }, { requesterId: dto.userId, addresseeId: req.user.id }] } }); if (!friend) throw new ForbiddenException('Direct chats require friendship'); if (await prisma.blockedUser.findFirst({ where: { OR: [{ blockerId: req.user.id, blockedId: dto.userId }, { blockerId: dto.userId, blockedId: req.user.id }] } })) throw new ForbiddenException('Chat unavailable'); const existing = await prisma.chat.findFirst({ where: { type: ChatType.DIRECT, AND: [{ members: { some: { userId: req.user.id } } }, { members: { some: { userId: dto.userId } } }] } }); if (existing) return publicChat(existing.id); const chat = await prisma.chat.create({ data: { type: ChatType.DIRECT, createdById: req.user.id, members: { create: [{ userId: req.user.id }, { userId: dto.userId }] } } }); this.realtime.emitUser(dto.userId, 'chats:changed', { type: 'created', chatId: chat.id, at: Date.now() }); return publicChat(chat.id); }
  @Post('group') async group(@Req() req: any, @Body() dto: CreateGroupDto) { const unique = [...new Set(dto.memberIds.filter(id => id !== req.user.id))].slice(0, 99); const users = await prisma.user.findMany({ where: { id: { in: unique }, isActive: true, deletedAt: null }, select: { id: true } }); const ids = users.map(u => u.id); const chat = await prisma.chat.create({ data: { type: ChatType.GROUP, title: dto.title.trim(), createdById: req.user.id, members: { create: [{ userId: req.user.id, isAdmin: true }, ...ids.map(userId => ({ userId }))] } } }); for (const id of ids) this.realtime.emitUser(id, 'chats:changed', { type: 'group-added', chatId: chat.id, at: Date.now() }); return publicChat(chat.id); }
  @Patch(':chatId/title') async rename(@Req() req: any, @Param('chatId') chatId: string, @Body() dto: RenameGroupDto) { const m = await this.member(chatId, req.user.id); const chat = await prisma.chat.findUnique({ where: { id: chatId } }); if (!chat || chat.type !== ChatType.GROUP || !m.isAdmin) throw new ForbiddenException(); const updated = await prisma.chat.update({ where: { id: chatId }, data: { title: dto.title.trim() } }); this.realtime.emitChat(chatId, 'chats:changed', { type: 'renamed', chatId, at: Date.now() }); return updated; }
  @Post(':chatId/members') async addMember(@Req() req: any, @Param('chatId') chatId: string, @Body() dto: GroupMemberDto) { const m = await this.member(chatId, req.user.id); const chat = await prisma.chat.findUnique({ where: { id: chatId } }); if (!chat || chat.type !== ChatType.GROUP || !m.isAdmin) throw new ForbiddenException(); await prisma.chatMember.upsert({ where: { chatId_userId: { chatId, userId: dto.userId } }, create: { chatId, userId: dto.userId }, update: {} }); this.realtime.emitUser(dto.userId, 'chats:changed', { type: 'group-added', chatId, at: Date.now() }); return { ok: true }; }
  @Delete(':chatId/members/:userId') async removeMember(@Req() req: any, @Param('chatId') chatId: string, @Param('userId') userId: string) { const m = await this.member(chatId, req.user.id); const chat = await prisma.chat.findUnique({ where: { id: chatId } }); if (!chat || chat.type !== ChatType.GROUP || (!m.isAdmin && userId !== req.user.id)) throw new ForbiddenException(); await prisma.chatMember.deleteMany({ where: { chatId, userId } }); this.realtime.emitUser(userId, 'chats:changed', { type: 'group-removed', chatId, at: Date.now() }); return { ok: true }; }
  @Get(':chatId/messages') async messages(@Req() req: any, @Param('chatId') chatId: string, @Query('cursor') cursor?: string, @Query('take') takeInput?: string) { await this.member(chatId, req.user.id); const take = Math.min(Math.max(Number(takeInput || 40), 1), 80); return prisma.message.findMany({ where: { chatId, deletedAt: null }, orderBy: { createdAt: 'desc' }, take, ...(cursor ? { cursor: { id: cursor }, skip: 1 } : {}), include: { sender: { select: publicUserSelect }, replyTo: { select: { id: true, text: true, type: true, senderId: true } }, reactions: true } }); }
  @Get(':chatId/search') async searchMessages(@Req() req: any, @Param('chatId') chatId: string, @Query('q') q = '') { await this.member(chatId, req.user.id); const term = q.trim(); if (term.length < 2) return []; return prisma.message.findMany({ where: { chatId, deletedAt: null, text: { contains: term, mode: 'insensitive' } }, orderBy: { createdAt: 'desc' }, take: 50, include: { sender: { select: publicUserSelect }, reactions: true } }); }
  @Post(':chatId/messages') async send(@Req() req: any, @Param('chatId') chatId: string, @Body() dto: SendMessageDto) { await this.member(chatId, req.user.id); const type = dto.type || MessageType.TEXT; if (type === MessageType.TEXT && !dto.text?.trim()) throw new BadRequestException('Text required'); if (type !== MessageType.TEXT && type !== MessageType.SYSTEM && !dto.mediaUrl) throw new BadRequestException('Media URL required'); if (dto.replyToId && !await prisma.message.findFirst({ where: { id: dto.replyToId, chatId, deletedAt: null } })) throw new BadRequestException('Invalid reply'); const message = await prisma.message.create({ data: { chatId, senderId: req.user.id, type, text: dto.text?.trim() || null, mediaUrl: dto.mediaUrl || null, mediaName: dto.mediaName || null, mediaMime: dto.mediaMime || null, mediaSize: dto.mediaSize || null, replyToId: dto.replyToId || null }, include: { sender: { select: publicUserSelect }, replyTo: { select: { id: true, text: true, type: true, senderId: true } }, reactions: true } }); await prisma.chat.update({ where: { id: chatId }, data: { updatedAt: new Date() } }); this.realtime.emitChat(chatId, 'message:new', message); const name = message.sender.profile?.displayName || message.sender.username; await this.notifyMembers(chatId, req.user.id, name, message.text || `رسالة ${type.toLowerCase()}`); return message; }
  @Patch(':chatId/messages/:messageId') async edit(@Req() req: any, @Param('chatId') chatId: string, @Param('messageId') messageId: string, @Body() dto: EditMessageDto) { await this.member(chatId, req.user.id); const msg = await prisma.message.findFirst({ where: { id: messageId, chatId, senderId: req.user.id, deletedAt: null } }); if (!msg || msg.type !== MessageType.TEXT) throw new ForbiddenException(); const updated = await prisma.message.update({ where: { id: messageId }, data: { text: dto.text.trim(), isEdited: true, editedAt: new Date() }, include: { sender: { select: publicUserSelect }, reactions: true } }); this.realtime.emitChat(chatId, 'message:updated', updated); return updated; }
  @Delete(':chatId/messages/:messageId') async deleteMessage(@Req() req: any, @Param('chatId') chatId: string, @Param('messageId') messageId: string) { const member = await this.member(chatId, req.user.id); const msg = await prisma.message.findFirst({ where: { id: messageId, chatId, deletedAt: null } }); if (!msg || (msg.senderId !== req.user.id && !member.isAdmin)) throw new ForbiddenException(); await prisma.message.update({ where: { id: messageId }, data: { deletedAt: new Date(), text: null, mediaUrl: null } }); this.realtime.emitChat(chatId, 'message:deleted', { chatId, messageId, at: Date.now() }); return { ok: true }; }
  @Post(':chatId/messages/:messageId/reactions') async react(@Req() req: any, @Param('chatId') chatId: string, @Param('messageId') messageId: string, @Body() dto: ReactionDto) { await this.member(chatId, req.user.id); if (!await prisma.message.findFirst({ where: { id: messageId, chatId, deletedAt: null } })) throw new NotFoundException(); const key = { messageId_userId_emoji: { messageId, userId: req.user.id, emoji: dto.emoji } }; const existing = await prisma.messageReaction.findUnique({ where: key }); if (existing) await prisma.messageReaction.delete({ where: { id: existing.id } }); else await prisma.messageReaction.create({ data: { messageId, userId: req.user.id, emoji: dto.emoji } }); const reactions = await prisma.messageReaction.findMany({ where: { messageId } }); this.realtime.emitChat(chatId, 'message:reactions', { chatId, messageId, reactions }); return reactions; }
  @Post(':chatId/messages/:messageId/pin') async pin(@Req() req: any, @Param('chatId') chatId: string, @Param('messageId') messageId: string) { await this.member(chatId, req.user.id); const msg = await prisma.message.findFirst({ where: { id: messageId, chatId, deletedAt: null } }); if (!msg) throw new NotFoundException(); const updated = await prisma.message.update({ where: { id: messageId }, data: { pinnedAt: msg.pinnedAt ? null : new Date() } }); this.realtime.emitChat(chatId, 'message:pinned', { chatId, messageId, pinnedAt: updated.pinnedAt, at: Date.now() }); return updated; }
  @Post(':chatId/read') async read(@Req() req: any, @Param('chatId') chatId: string, @Body() dto: ReadDto) { await this.member(chatId, req.user.id); const now = new Date(); await prisma.chatMember.update({ where: { chatId_userId: { chatId, userId: req.user.id } }, data: { lastReadAt: now, lastReadMsgId: dto.messageId || null } }); this.realtime.emitChat(chatId, 'chat:read', { chatId, userId: req.user.id, messageId: dto.messageId || null, at: now }); return { ok: true }; }
}

@Controller('matchmaking') @UseGuards(JwtAuthGuard)
class MatchmakingController {
  constructor(private readonly realtime: RealtimeGateway) {}
  private async statusFor(userId: string) {
    const waiting = await prisma.matchQueueEntry.findFirst({ where: { userId, status: MatchQueueStatus.WAITING }, orderBy: { createdAt: 'desc' } });
    if (waiting) return { state: 'WAITING', mode: waiting.mode, queueId: waiting.id, since: waiting.createdAt };
    const player = await prisma.matchSessionPlayer.findFirst({ where: { userId, session: { status: MatchSessionStatus.ACTIVE } }, orderBy: { joinedAt: 'desc' }, include: { session: { include: { chat: { include: { members: { include: { user: { select: publicUserSelect } } }, messages: { where: { deletedAt: null }, orderBy: { createdAt: 'desc' }, take: 1, include: { sender: { select: publicUserSelect }, reactions: true } } } }, players: { include: { user: { select: publicUserSelect } } } } } } });
    if (player) return { state: 'MATCHED', mode: player.session.mode, sessionId: player.session.id, chat: player.session.chat, players: player.session.players.map(p => p.user) };
    return { state: 'IDLE' };
  }
  private async compatible(client: any, ids: string[]) { if (ids.length < 2) return true; const block = await client.blockedUser.findFirst({ where: { OR: ids.flatMap((a, i) => ids.slice(i + 1).flatMap(b => [{ blockerId: a, blockedId: b }, { blockerId: b, blockedId: a }])) } }); return !block; }
  private async tryMatch(mode: MatchMode) {
    let found: any = null;
    await prisma.$transaction(async tx => {
      await tx.$queryRaw`SELECT pg_advisory_xact_lock(741852963)`;
      const rows = await tx.matchQueueEntry.findMany({ where: { mode, status: MatchQueueStatus.WAITING, user: { isActive: true, deletedAt: null } }, orderBy: { createdAt: 'asc' }, take: mode === MatchMode.ONE_V_ONE ? 12 : 30 });
      const selected: typeof rows = [];
      for (const row of rows) {
        if (selected.length >= (mode === MatchMode.ONE_V_ONE ? 2 : 10)) break;
        const ids = [...selected.map(x => x.userId), row.userId];
        if (ids.length === 1 || await this.compatible(tx, ids)) selected.push(row);
      }
      const needed = mode === MatchMode.ONE_V_ONE ? 2 : 5;
      if (selected.length < needed) return;
      const participants = selected.slice(0, mode === MatchMode.ONE_V_ONE ? 2 : 10);
      const ids = participants.map(x => x.userId);
      const chat = await tx.chat.create({ data: { type: ChatType.MATCH, title: mode === MatchMode.ONE_V_ONE ? 'تطابق 1 ضد 1' : 'تطابق جماعي', members: { create: ids.map(userId => ({ userId })) } } });
      const session = await tx.matchSession.create({ data: { mode, status: MatchSessionStatus.ACTIVE, chatId: chat.id, minPlayers: needed, maxPlayers: mode === MatchMode.ONE_V_ONE ? 2 : 10, players: { create: ids.map(userId => ({ userId })) } } });
      await tx.matchQueueEntry.updateMany({ where: { id: { in: participants.map(x => x.id) }, status: MatchQueueStatus.WAITING }, data: { status: MatchQueueStatus.MATCHED, sessionId: session.id } });
      found = { sessionId: session.id, chatId: chat.id, userIds: ids, mode };
    }, { isolationLevel: 'Serializable' });
    if (found) { const chat = await publicChat(found.chatId); for (const userId of found.userIds) this.realtime.emitUser(userId, 'match:found', { state: 'MATCHED', mode: found.mode, sessionId: found.sessionId, chat, at: Date.now() }); }
  }
  @Get('status') async status(@Req() req: any) { return this.statusFor(req.user.id); }
  @Post('queue') async queue(@Req() req: any, @Body() dto: MatchQueueDto) { const active = await prisma.matchSessionPlayer.findFirst({ where: { userId: req.user.id, session: { status: MatchSessionStatus.ACTIVE } } }); if (active) return this.statusFor(req.user.id); await prisma.matchQueueEntry.updateMany({ where: { userId: req.user.id, status: MatchQueueStatus.WAITING }, data: { status: MatchQueueStatus.CANCELLED } }); const row = await prisma.matchQueueEntry.create({ data: { userId: req.user.id, mode: dto.mode } }); this.realtime.emitUser(req.user.id, 'match:queue', { state: 'WAITING', mode: dto.mode, queueId: row.id, at: Date.now() }); await this.tryMatch(dto.mode); return this.statusFor(req.user.id); }
  @Delete('queue') async cancel(@Req() req: any) { await prisma.matchQueueEntry.updateMany({ where: { userId: req.user.id, status: MatchQueueStatus.WAITING }, data: { status: MatchQueueStatus.CANCELLED } }); this.realtime.emitUser(req.user.id, 'match:cancelled', { state: 'IDLE', at: Date.now() }); return { state: 'IDLE' }; }
  @Post('session/:id/leave') async leave(@Req() req: any, @Param('id') id: string) { const player = await prisma.matchSessionPlayer.findUnique({ where: { sessionId_userId: { sessionId: id, userId: req.user.id } }, include: { session: true } }); if (!player || player.session.status !== MatchSessionStatus.ACTIVE) throw new NotFoundException(); await prisma.chatMember.deleteMany({ where: { chatId: player.session.chatId, userId: req.user.id } }); await prisma.matchSessionPlayer.delete({ where: { id: player.id } }); const remaining = await prisma.matchSessionPlayer.count({ where: { sessionId: id } }); if (remaining < 2) await prisma.matchSession.update({ where: { id }, data: { status: MatchSessionStatus.FINISHED, endedAt: new Date() } }); this.realtime.emitChat(player.session.chatId, 'match:player-left', { userId: req.user.id, remaining, at: Date.now() }); return { ok: true }; }
}

@Controller('rooms') @UseGuards(JwtAuthGuard)
class RoomsController {
  constructor(private readonly realtime: RealtimeGateway) {}
  @Get() async list() { return prisma.socialRoom.findMany({ where: { visibility: RoomVisibility.PUBLIC, isActive: true }, orderBy: { updatedAt: 'desc' }, take: 50, include: { creator: { select: publicUserSelect }, _count: { select: { members: true } } } }); }
  @Post() async create(@Req() req: any, @Body() dto: CreateRoomDto) { const capacity = dto.capacity || 50; const chat = await prisma.chat.create({ data: { type: ChatType.ROOM, title: dto.name.trim(), createdById: req.user.id, members: { create: { userId: req.user.id, isAdmin: true } } } }); const room = await prisma.socialRoom.create({ data: { name: dto.name.trim(), description: dto.description?.trim() || null, capacity, creatorId: req.user.id, chatId: chat.id, members: { create: { userId: req.user.id } } }, include: { creator: { select: publicUserSelect }, _count: { select: { members: true } } } }); return { room, chat: await publicChat(chat.id) }; }
  @Post(':id/join') async join(@Req() req: any, @Param('id') id: string) { const room = await prisma.socialRoom.findFirst({ where: { id, isActive: true, visibility: RoomVisibility.PUBLIC }, include: { _count: { select: { members: true } } } }); if (!room) throw new NotFoundException(); const existing = await prisma.roomMember.findUnique({ where: { roomId_userId: { roomId: id, userId: req.user.id } } }); if (!existing && room._count.members >= room.capacity) throw new ConflictException('Room is full'); if (!existing) await prisma.$transaction([prisma.roomMember.create({ data: { roomId: id, userId: req.user.id } }), prisma.chatMember.upsert({ where: { chatId_userId: { chatId: room.chatId, userId: req.user.id } }, create: { chatId: room.chatId, userId: req.user.id }, update: {} }), prisma.socialRoom.update({ where: { id }, data: { updatedAt: new Date() } })]); this.realtime.emitChat(room.chatId, 'room:member-joined', { userId: req.user.id, at: Date.now() }); return publicChat(room.chatId); }
  @Delete(':id/leave') async leave(@Req() req: any, @Param('id') id: string) { const room = await prisma.socialRoom.findUnique({ where: { id } }); if (!room) throw new NotFoundException(); if (room.creatorId === req.user.id) throw new BadRequestException('Creator must close the room'); await prisma.$transaction([prisma.roomMember.deleteMany({ where: { roomId: id, userId: req.user.id } }), prisma.chatMember.deleteMany({ where: { chatId: room.chatId, userId: req.user.id } })]); this.realtime.emitChat(room.chatId, 'room:member-left', { userId: req.user.id, at: Date.now() }); return { ok: true }; }
  @Delete(':id') async close(@Req() req: any, @Param('id') id: string) { const room = await prisma.socialRoom.findUnique({ where: { id } }); if (!room) throw new NotFoundException(); if (room.creatorId !== req.user.id) throw new ForbiddenException(); await prisma.socialRoom.update({ where: { id }, data: { isActive: false } }); this.realtime.emitChat(room.chatId, 'room:closed', { roomId: id, at: Date.now() }); return { ok: true }; }
}

@Controller('notifications') @UseGuards(JwtAuthGuard)
class NotificationsController {
  constructor(private readonly realtime: RealtimeGateway) {}
  @Get() async list(@Req() req: any) { return prisma.notification.findMany({ where: { userId: req.user.id }, orderBy: { createdAt: 'desc' }, take: 100 }); }
  @Post(':id/read') async read(@Req() req: any, @Param('id') id: string) { const row = await prisma.notification.findFirst({ where: { id, userId: req.user.id } }); if (!row) throw new NotFoundException(); const updated = await prisma.notification.update({ where: { id }, data: { readAt: new Date() } }); this.realtime.emitUser(req.user.id, 'notifications:changed', { type: 'read', notificationId: id, at: Date.now() }); return updated; }
  @Post('read-all') async readAll(@Req() req: any) { await prisma.notification.updateMany({ where: { userId: req.user.id, readAt: null }, data: { readAt: new Date() } }); this.realtime.emitUser(req.user.id, 'notifications:changed', { type: 'read-all', at: Date.now() }); return { ok: true }; }
}

@Module({ imports: [PassportModule, JwtModule.register({})], providers: [AuthService, JwtStrategy, RealtimeGateway], controllers: [HealthController, AuthController, UsersController, FriendsController, ChatsController, MatchmakingController, RoomsController, NotificationsController, Batch2Controller] })
class AppModule {}

async function bootstrap() {
  if (!process.env.JWT_ACCESS_SECRET || process.env.JWT_ACCESS_SECRET.length < 32) throw new Error('JWT_ACCESS_SECRET must be at least 32 characters');
  const app = await NestFactory.create(AppModule); app.setGlobalPrefix('api/v1');
  app.useGlobalPipes(new ValidationPipe({ whitelist: true, forbidNonWhitelisted: true, transform: true }));
  app.enableCors({ origin: process.env.CORS_ORIGIN ? process.env.CORS_ORIGIN.split(',').map(v => v.trim()) : true, credentials: true });
  await app.listen(Number(process.env.PORT || 3000), '0.0.0.0');
}
bootstrap();
import 'reflect-metadata';
import { BadRequestException, Body, ConflictException, Controller, Delete, Get, Injectable, Module, NotFoundException, Param, Patch, Post, Query, Req, UnauthorizedException, UseGuards, ValidationPipe } from '@nestjs/common';
import { NestFactory } from '@nestjs/core';
import { JwtModule, JwtService } from '@nestjs/jwt';
import { AuthGuard, PassportModule } from '@nestjs/passport';
import { PassportStrategy } from '@nestjs/passport';
import { ExtractJwt, Strategy } from 'passport-jwt';
import { FriendshipStatus, PrismaClient, UserRole } from '@prisma/client';
import * as argon2 from 'argon2';
import { IsEmail, IsOptional, IsString, Length, Matches, MaxLength, MinLength } from 'class-validator';
import { createHash, randomBytes } from 'crypto';
import { OnGatewayConnection, OnGatewayInit, WebSocketGateway } from '@nestjs/websockets';
import { Server, Socket } from 'socket.io';

const prisma = new PrismaClient();
const hashToken = (value: string) => createHash('sha256').update(value).digest('hex');

class RegisterDto {
  @IsEmail() email!: string;
  @IsString() @Matches(/^[a-zA-Z0-9_]{3,24}$/) username!: string;
  @IsString() @MinLength(8) @MaxLength(72) password!: string;
  @IsString() @Length(1, 60) displayName!: string;
}
class LoginDto { @IsEmail() email!: string; @IsString() password!: string; }
class RefreshDto { @IsString() refreshToken!: string; }
class ForgotPasswordDto { @IsEmail() email!: string; }
class ResetPasswordDto { @IsString() token!: string; @IsString() @MinLength(8) @MaxLength(72) password!: string; }
class UpdateProfileDto {
  @IsOptional() @IsString() @Length(1, 60) displayName?: string;
  @IsOptional() @IsString() @MaxLength(280) bio?: string;
  @IsOptional() @IsString() @MaxLength(500) avatarUrl?: string;
}

@Injectable()
class AuthService {
  constructor(private readonly jwt: JwtService) {}
  private async issueTokens(userId: string, role: UserRole) {
    const accessTokenTtlSeconds = Number(process.env.ACCESS_TOKEN_TTL_SECONDS || 900);
    const accessToken = await this.jwt.signAsync({ sub: userId, role }, { secret: process.env.JWT_ACCESS_SECRET, expiresIn: accessTokenTtlSeconds });
    const rawRefresh = randomBytes(48).toString('base64url');
    const days = Number(process.env.REFRESH_TOKEN_TTL_DAYS || 30);
    await prisma.refreshToken.create({ data: { userId, tokenHash: hashToken(rawRefresh), expiresAt: new Date(Date.now() + days * 86400000) } });
    return { accessToken, refreshToken: rawRefresh };
  }
  safeUser(user: any) { const { passwordHash, ...safe } = user; return safe; }
  async register(dto: RegisterDto) {
    const email = dto.email.trim().toLowerCase(); const username = dto.username.trim().toLowerCase();
    const existing = await prisma.user.findFirst({ where: { OR: [{ email }, { username }] } });
    if (existing) throw new ConflictException('Email or username already in use');
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
    const email = emailInput.trim().toLowerCase(); const user = await prisma.user.findUnique({ where: { email } });
    if (!user || !user.isActive || user.deletedAt) return { ok: true };
    await prisma.passwordResetToken.deleteMany({ where: { userId: user.id, usedAt: null } });
    const raw = randomBytes(32).toString('base64url');
    await prisma.passwordResetToken.create({ data: { userId: user.id, tokenHash: hashToken(raw), expiresAt: new Date(Date.now() + 15 * 60000) } });
    return process.env.NODE_ENV === 'production' ? { ok: true } : { ok: true, developmentResetToken: raw };
  }
  async resetPassword(raw: string, password: string) {
    const token = await prisma.passwordResetToken.findUnique({ where: { tokenHash: hashToken(raw) } });
    if (!token || token.usedAt || token.expiresAt <= new Date()) throw new BadRequestException('Invalid or expired reset token');
    const passwordHash = await argon2.hash(password);
    await prisma.$transaction([prisma.user.update({ where: { id: token.userId }, data: { passwordHash } }), prisma.passwordResetToken.update({ where: { id: token.id }, data: { usedAt: new Date() } }), prisma.refreshToken.updateMany({ where: { userId: token.userId, revokedAt: null }, data: { revokedAt: new Date() } })]);
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
class RealtimeGateway implements OnGatewayInit, OnGatewayConnection {
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
    } catch (_) { client.disconnect(true); }
  }
  emitFriendState(userId: string, type: string, actorId?: string) { this.server?.to(`user:${userId}`).emit('friends:changed', { type, actorId: actorId || null, at: Date.now() }); }
}

@Controller('health')
class HealthController { @Get() async health() { await prisma.$queryRaw`SELECT 1`; return { ok: true, service: 'aliqo-backend' }; } }

@Controller('auth')
class AuthController {
  constructor(private auth: AuthService) {}
  @Post('register') register(@Body() dto: RegisterDto) { return this.auth.register(dto); }
  @Post('login') login(@Body() dto: LoginDto) { return this.auth.login(dto); }
  @Post('refresh') refresh(@Body() dto: RefreshDto) { return this.auth.refresh(dto.refreshToken); }
  @Post('logout') logout(@Body() dto: RefreshDto) { return this.auth.logout(dto.refreshToken); }
  @Post('forgot-password') forgot(@Body() dto: ForgotPasswordDto) { return this.auth.forgotPassword(dto.email); }
  @Post('reset-password') reset(@Body() dto: ResetPasswordDto) { return this.auth.resetPassword(dto.token, dto.password); }
}

@Controller('users')
@UseGuards(JwtAuthGuard)
class UsersController {
  constructor(private readonly realtime: RealtimeGateway) {}
  @Get('me') async me(@Req() req: any) {
    const user = await prisma.user.findUnique({ where: { id: req.user.id }, include: { profile: true } });
    if (!user) throw new NotFoundException(); const { passwordHash, ...safe } = user; return safe;
  }
  @Patch('me/profile') async update(@Req() req: any, @Body() dto: UpdateProfileDto) {
    const profile = await prisma.profile.update({ where: { userId: req.user.id }, data: dto });
    const friendships = await prisma.friendship.findMany({ where: { status: FriendshipStatus.ACCEPTED, OR: [{ requesterId: req.user.id }, { addresseeId: req.user.id }] }, select: { requesterId: true, addresseeId: true } });
    const friendIds = new Set(friendships.map(row => row.requesterId === req.user.id ? row.addresseeId : row.requesterId));
    this.realtime.emitFriendState(req.user.id, 'profile', req.user.id);
    for (const friendId of friendIds) this.realtime.emitFriendState(friendId, 'profile', req.user.id);
    return profile;
  }
  @Get('search') async search(@Req() req: any, @Query('q') q = '') {
    const term = q.trim().toLowerCase(); if (term.length < 2) return [];
    return prisma.user.findMany({ where: { id: { not: req.user.id }, isActive: true, deletedAt: null, username: { contains: term, mode: 'insensitive' }, blocksReceived: { none: { blockerId: req.user.id } }, blocksCreated: { none: { blockedId: req.user.id } } }, select: { id: true, username: true, profile: { select: { displayName: true, avatarUrl: true, bio: true, isOnline: true, lastSeenAt: true } } }, take: 20 });
  }
  @Delete('me') async remove(@Req() req: any) {
    await prisma.$transaction([prisma.refreshToken.updateMany({ where: { userId: req.user.id, revokedAt: null }, data: { revokedAt: new Date() } }), prisma.passwordResetToken.deleteMany({ where: { userId: req.user.id } }), prisma.user.update({ where: { id: req.user.id }, data: { isActive: false, deletedAt: new Date(), email: `deleted-${req.user.id}@invalid.local`, username: `deleted_${req.user.id.slice(-12)}` } })]); return { ok: true };
  }
}

@Controller('friends')
@UseGuards(JwtAuthGuard)
class FriendsController {
  constructor(private readonly realtime: RealtimeGateway) {}
  private publicUser(user: any) { return { id: user.id, username: user.username, profile: user.profile }; }
  private emitBoth(a: string, b: string, type: string, actorId: string) { this.realtime.emitFriendState(a, type, actorId); this.realtime.emitFriendState(b, type, actorId); }
  @Get() async list(@Req() req: any) {
    const rows = await prisma.friendship.findMany({ where: { status: FriendshipStatus.ACCEPTED, OR: [{ requesterId: req.user.id }, { addresseeId: req.user.id }] }, include: { requester: { include: { profile: true } }, addressee: { include: { profile: true } } }, orderBy: { updatedAt: 'desc' } });
    const blocks = await prisma.blockedUser.findMany({ where: { OR: [{ blockerId: req.user.id }, { blockedId: req.user.id }] }, select: { blockerId: true, blockedId: true } });
    const blockedIds = new Set(blocks.map(b => b.blockerId === req.user.id ? b.blockedId : b.blockerId));
    return rows.map(r => r.requesterId === req.user.id ? r.addressee : r.requester).filter(u => !blockedIds.has(u.id)).map(u => this.publicUser(u));
  }
  @Get('requests') async requests(@Req() req: any) { const rows = await prisma.friendship.findMany({ where: { addresseeId: req.user.id, status: FriendshipStatus.PENDING }, include: { requester: { include: { profile: true } } }, orderBy: { createdAt: 'desc' } }); return rows.map(r => ({ id: r.id, createdAt: r.createdAt, user: this.publicUser(r.requester) })); }
  @Get('blocked') async blocked(@Req() req: any) { const rows = await prisma.blockedUser.findMany({ where: { blockerId: req.user.id }, include: { blocked: { include: { profile: true } } }, orderBy: { createdAt: 'desc' } }); return rows.map(r => this.publicUser(r.blocked)); }
  @Post(':userId/request') async request(@Req() req: any, @Param('userId') target: string) {
    if (target === req.user.id) throw new BadRequestException('Cannot friend yourself');
    const blocked = await prisma.blockedUser.findFirst({ where: { OR: [{ blockerId: req.user.id, blockedId: target }, { blockerId: target, blockedId: req.user.id }] } }); if (blocked) throw new BadRequestException('Friend request unavailable');
    const targetUser = await prisma.user.findFirst({ where: { id: target, isActive: true, deletedAt: null } }); if (!targetUser) throw new NotFoundException();
    const accepted = await prisma.friendship.findFirst({ where: { status: FriendshipStatus.ACCEPTED, OR: [{ requesterId: req.user.id, addresseeId: target }, { requesterId: target, addresseeId: req.user.id }] } }); if (accepted) return accepted;
    const inverse = await prisma.friendship.findUnique({ where: { requesterId_addresseeId: { requesterId: target, addresseeId: req.user.id } } });
    if (inverse?.status === FriendshipStatus.PENDING) { const row = await prisma.friendship.update({ where: { id: inverse.id }, data: { status: FriendshipStatus.ACCEPTED } }); this.emitBoth(req.user.id, target, 'accepted', req.user.id); return row; }
    const row = await prisma.friendship.upsert({ where: { requesterId_addresseeId: { requesterId: req.user.id, addresseeId: target } }, create: { requesterId: req.user.id, addresseeId: target }, update: { status: FriendshipStatus.PENDING } }); this.emitBoth(req.user.id, target, 'request', req.user.id); return row;
  }
  @Post(':requestId/accept') async accept(@Req() req: any, @Param('requestId') id: string) { const row = await prisma.friendship.findFirst({ where: { id, addresseeId: req.user.id, status: FriendshipStatus.PENDING } }); if (!row) throw new NotFoundException(); const updated = await prisma.friendship.update({ where: { id }, data: { status: FriendshipStatus.ACCEPTED } }); this.emitBoth(row.requesterId, row.addresseeId, 'accepted', req.user.id); return updated; }
  @Post(':requestId/reject') async reject(@Req() req: any, @Param('requestId') id: string) { const row = await prisma.friendship.findFirst({ where: { id, addresseeId: req.user.id, status: FriendshipStatus.PENDING } }); if (!row) throw new NotFoundException(); await prisma.friendship.delete({ where: { id } }); this.emitBoth(row.requesterId, row.addresseeId, 'rejected', req.user.id); return { ok: true }; }
  @Delete(':userId') async remove(@Req() req: any, @Param('userId') userId: string) { await prisma.friendship.deleteMany({ where: { OR: [{ requesterId: req.user.id, addresseeId: userId }, { requesterId: userId, addresseeId: req.user.id }] } }); this.emitBoth(req.user.id, userId, 'removed', req.user.id); return { ok: true }; }
  @Post(':userId/block') async block(@Req() req: any, @Param('userId') userId: string) { if (userId === req.user.id) throw new BadRequestException(); const target = await prisma.user.findFirst({ where: { id: userId, isActive: true, deletedAt: null } }); if (!target) throw new NotFoundException(); await prisma.blockedUser.upsert({ where: { blockerId_blockedId: { blockerId: req.user.id, blockedId: userId } }, create: { blockerId: req.user.id, blockedId: userId }, update: {} }); this.emitBoth(req.user.id, userId, 'blocked', req.user.id); return { ok: true }; }
  @Delete(':userId/block') async unblock(@Req() req: any, @Param('userId') userId: string) { await prisma.blockedUser.deleteMany({ where: { blockerId: req.user.id, blockedId: userId } }); this.emitBoth(req.user.id, userId, 'unblocked', req.user.id); return { ok: true }; }
}

@Module({ imports: [PassportModule, JwtModule.register({})], providers: [AuthService, JwtStrategy, RealtimeGateway], controllers: [HealthController, AuthController, UsersController, FriendsController] })
class AppModule {}

async function bootstrap() {
  if (!process.env.JWT_ACCESS_SECRET || process.env.JWT_ACCESS_SECRET.length < 32) throw new Error('JWT_ACCESS_SECRET must be at least 32 characters');
  const app = await NestFactory.create(AppModule); app.setGlobalPrefix('api/v1');
  app.useGlobalPipes(new ValidationPipe({ whitelist: true, forbidNonWhitelisted: true, transform: true }));
  app.enableCors({ origin: process.env.CORS_ORIGIN ? process.env.CORS_ORIGIN.split(',').map(v => v.trim()) : true, credentials: true });
  const port = Number(process.env.PORT || 3000); await app.listen(port, '0.0.0.0');
}
bootstrap();
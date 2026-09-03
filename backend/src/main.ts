import 'reflect-metadata';
import { Body, Controller, Delete, Get, Injectable, Module, Param, Patch, Post, Query, Req, UnauthorizedException, BadRequestException, ConflictException, NotFoundException, UseGuards, ValidationPipe } from '@nestjs/common';
import { NestFactory } from '@nestjs/core';
import { JwtModule, JwtService } from '@nestjs/jwt';
import { AuthGuard } from '@nestjs/passport';
import { PassportModule } from '@nestjs/passport';
import { PassportStrategy } from '@nestjs/passport';
import { ExtractJwt, Strategy } from 'passport-jwt';
import { PrismaClient, FriendshipStatus, UserRole } from '@prisma/client';
import * as argon2 from 'argon2';
import { IsEmail, IsOptional, IsString, Length, Matches, MaxLength, MinLength } from 'class-validator';
import { randomBytes, createHash } from 'crypto';

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
class UpdateProfileDto {
  @IsOptional() @IsString() @Length(1, 60) displayName?: string;
  @IsOptional() @IsString() @MaxLength(280) bio?: string;
  @IsOptional() @IsString() avatarUrl?: string;
}
class ForgotPasswordDto { @IsEmail() email!: string; }
class ResetPasswordDto { @IsString() token!: string; @IsString() @MinLength(8) @MaxLength(72) password!: string; }

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

  async register(dto: RegisterDto) {
    const email = dto.email.trim().toLowerCase();
    const username = dto.username.trim().toLowerCase();
    const existing = await prisma.user.findFirst({ where: { OR: [{ email }, { username }] } });
    if (existing) throw new ConflictException('Email or username already in use');
    const role = email === process.env.PRIMARY_ADMIN_EMAIL?.trim().toLowerCase() ? UserRole.PRIMARY_ADMIN : UserRole.USER;
    const user = await prisma.user.create({
      data: { email, username, passwordHash: await argon2.hash(dto.password), role, profile: { create: { displayName: dto.displayName.trim() } } },
      include: { profile: true }
    });
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

  async logout(raw: string) {
    await prisma.refreshToken.updateMany({ where: { tokenHash: hashToken(raw), revokedAt: null }, data: { revokedAt: new Date() } });
    return { ok: true };
  }

  async forgotPassword(emailInput: string) {
    const email = emailInput.trim().toLowerCase();
    const user = await prisma.user.findUnique({ where: { email } });
    if (!user) return { ok: true };
    const raw = randomBytes(32).toString('base64url');
    await prisma.passwordResetToken.create({ data: { userId: user.id, tokenHash: hashToken(raw), expiresAt: new Date(Date.now() + 15 * 60000) } });
    return process.env.NODE_ENV === 'production' ? { ok: true } : { ok: true, developmentResetToken: raw };
  }

  async resetPassword(raw: string, password: string) {
    const token = await prisma.passwordResetToken.findUnique({ where: { tokenHash: hashToken(raw) } });
    if (!token || token.usedAt || token.expiresAt <= new Date()) throw new BadRequestException('Invalid or expired reset token');
    await prisma.$transaction([
      prisma.user.update({ where: { id: token.userId }, data: { passwordHash: await argon2.hash(password) } }),
      prisma.passwordResetToken.update({ where: { id: token.id }, data: { usedAt: new Date() } }),
      prisma.refreshToken.updateMany({ where: { userId: token.userId, revokedAt: null }, data: { revokedAt: new Date() } })
    ]);
    return { ok: true };
  }

  safeUser(user: any) { const { passwordHash, ...safe } = user; return safe; }
}

@Injectable()
class JwtStrategy extends PassportStrategy(Strategy) {
  constructor() { super({ jwtFromRequest: ExtractJwt.fromAuthHeaderAsBearerToken(), ignoreExpiration: false, secretOrKey: process.env.JWT_ACCESS_SECRET || 'dev-only-change-me' }); }
  async validate(payload: any) {
    const user = await prisma.user.findUnique({ where: { id: payload.sub } });
    if (!user || !user.isActive || user.deletedAt) throw new UnauthorizedException();
    return { id: user.id, role: user.role };
  }
}
const JwtAuthGuard = AuthGuard('jwt');

@Controller('health')
class HealthController {
  @Get()
  async health() {
    await prisma.$queryRaw`SELECT 1`;
    return { ok: true, service: 'aliqo-backend' };
  }
}

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
  @Get('me') async me(@Req() req: any) {
    const user = await prisma.user.findUnique({ where: { id: req.user.id }, include: { profile: true } });
    if (!user) throw new NotFoundException();
    const { passwordHash, ...safe } = user; return safe;
  }
  @Patch('me/profile') async update(@Req() req: any, @Body() dto: UpdateProfileDto) {
    return prisma.profile.update({ where: { userId: req.user.id }, data: dto });
  }
  @Get('search') async search(@Req() req: any, @Query('q') q = '') {
    const term = q.trim().toLowerCase();
    if (term.length < 2) return [];
    return prisma.user.findMany({
      where: { id: { not: req.user.id }, isActive: true, deletedAt: null, username: { contains: term, mode: 'insensitive' }, blocksReceived: { none: { blockerId: req.user.id } }, blocksCreated: { none: { blockedId: req.user.id } } },
      select: { id: true, username: true, profile: { select: { displayName: true, avatarUrl: true, bio: true, isOnline: true } } }, take: 20
    });
  }
  @Delete('me') async remove(@Req() req: any) {
    await prisma.$transaction([
      prisma.refreshToken.updateMany({ where: { userId: req.user.id, revokedAt: null }, data: { revokedAt: new Date() } }),
      prisma.user.update({ where: { id: req.user.id }, data: { isActive: false, deletedAt: new Date(), email: `deleted-${req.user.id}@invalid.local`, username: `deleted_${req.user.id.slice(-12)}` } })
    ]);
    return { ok: true };
  }
}

@Controller('friends')
@UseGuards(JwtAuthGuard)
class FriendsController {
  @Get() async list(@Req() req: any) {
    const rows = await prisma.friendship.findMany({ where: { status: FriendshipStatus.ACCEPTED, OR: [{ requesterId: req.user.id }, { addresseeId: req.user.id }] }, include: { requester: { include: { profile: true } }, addressee: { include: { profile: true } } } });
    return rows.map(r => r.requesterId === req.user.id ? r.addressee : r.requester).map(({ passwordHash, ...u }) => u);
  }
  @Post(':userId/request') async request(@Req() req: any, @Param('userId') target: string) {
    if (target === req.user.id) throw new BadRequestException('Cannot friend yourself');
    const blocked = await prisma.blockedUser.findFirst({ where: { OR: [{ blockerId: req.user.id, blockedId: target }, { blockerId: target, blockedId: req.user.id }] } });
    if (blocked) throw new BadRequestException('Friend request unavailable');
    const targetUser = await prisma.user.findFirst({ where: { id: target, isActive: true, deletedAt: null } });
    if (!targetUser) throw new NotFoundException();
    const inverse = await prisma.friendship.findUnique({ where: { requesterId_addresseeId: { requesterId: target, addresseeId: req.user.id } } });
    if (inverse?.status === FriendshipStatus.PENDING) return prisma.friendship.update({ where: { id: inverse.id }, data: { status: FriendshipStatus.ACCEPTED } });
    return prisma.friendship.upsert({ where: { requesterId_addresseeId: { requesterId: req.user.id, addresseeId: target } }, create: { requesterId: req.user.id, addresseeId: target }, update: { status: FriendshipStatus.PENDING } });
  }
  @Post(':requestId/accept') async accept(@Req() req: any, @Param('requestId') id: string) {
    const row = await prisma.friendship.findFirst({ where: { id, addresseeId: req.user.id, status: FriendshipStatus.PENDING } });
    if (!row) throw new NotFoundException();
    return prisma.friendship.update({ where: { id }, data: { status: FriendshipStatus.ACCEPTED } });
  }
  @Delete(':userId') async remove(@Req() req: any, @Param('userId') userId: string) {
    await prisma.friendship.deleteMany({ where: { OR: [{ requesterId: req.user.id, addresseeId: userId }, { requesterId: userId, addresseeId: req.user.id }] } });
    return { ok: true };
  }
  @Post(':userId/block') async block(@Req() req: any, @Param('userId') userId: string) {
    if (userId === req.user.id) throw new BadRequestException();
    await prisma.$transaction([
      prisma.friendship.deleteMany({ where: { OR: [{ requesterId: req.user.id, addresseeId: userId }, { requesterId: userId, addresseeId: req.user.id }] } }),
      prisma.blockedUser.upsert({ where: { blockerId_blockedId: { blockerId: req.user.id, blockedId: userId } }, create: { blockerId: req.user.id, blockedId: userId }, update: {} })
    ]);
    return { ok: true };
  }
  @Delete(':userId/block') async unblock(@Req() req: any, @Param('userId') userId: string) {
    await prisma.blockedUser.deleteMany({ where: { blockerId: req.user.id, blockedId: userId } });
    return { ok: true };
  }
}

@Module({
  imports: [PassportModule, JwtModule.register({})],
  providers: [AuthService, JwtStrategy],
  controllers: [HealthController, AuthController, UsersController, FriendsController]
})
class AppModule {}

async function bootstrap() {
  if (!process.env.JWT_ACCESS_SECRET || process.env.JWT_ACCESS_SECRET.length < 32) throw new Error('JWT_ACCESS_SECRET must be set to a strong secret');
  const app = await NestFactory.create(AppModule);
  app.setGlobalPrefix('api/v1');
  app.useGlobalPipes(new ValidationPipe({ whitelist: true, forbidNonWhitelisted: true, transform: true }));
  app.enableCors({ origin: process.env.CORS_ORIGIN?.split(',') || false, credentials: true });
  await app.listen(Number(process.env.PORT || 3000), '0.0.0.0');
}
bootstrap();

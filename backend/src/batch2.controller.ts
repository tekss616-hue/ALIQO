import { BadRequestException, Body, Controller, Delete, Get, NotFoundException, Param, Post, Req, UseGuards } from '@nestjs/common';
import { AuthGuard } from '@nestjs/passport';
import { DevicePlatform, MatchSessionStatus, PrismaClient } from '@prisma/client';
import { IsEnum, IsInt, IsOptional, IsString, Max, MaxLength, Min, MinLength, Matches } from 'class-validator';
import { Batch2Integrations } from './batch2-integrations';
import { batch2Events } from './batch2-events';

const rpsPrisma = new PrismaClient();
const RPS_TOTAL_ROUNDS = 10;
const RPS_RECONNECT_SECONDS = 45;
const XP_WIN = 100;
const XP_DRAW = 50;
const XP_LOSS = 25;
type RpsMove = 'ROCK' | 'PAPER' | 'SCISSORS';
type RpsGame = { round:number; scores:Record<string,number>; choices:Record<string,RpsMove>; finished:boolean; winnerId?:string; progressRecorded?:boolean; endedByDisconnect?:boolean };
type ProgressRow = { wins:number; losses:number; draws:number; matchesPlayed:number; xp:number; winStreak:number; bestWinStreak:number };
const rpsGames = new Map<string,RpsGame>();
const rpsRematchRequests = new Map<string,Set<string>>();
const rpsOfflineSince = new Map<string,number>();

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
class RpsMoveDto { @IsString() move!: string; }

@Controller() @UseGuards(AuthGuard('jwt'))
export class Batch2Controller {
  private readonly integrations = new Batch2Integrations();
  private progressReady?: Promise<void>;

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

  private async ensureProgressTable(){
    if(!this.progressReady){
      this.progressReady=(async()=>{
        await rpsPrisma.$executeRawUnsafe(`CREATE TABLE IF NOT EXISTS "PlayerProgress" (
          "userId" TEXT PRIMARY KEY REFERENCES "User"("id") ON DELETE CASCADE,
          "wins" INTEGER NOT NULL DEFAULT 0,
          "losses" INTEGER NOT NULL DEFAULT 0,
          "draws" INTEGER NOT NULL DEFAULT 0,
          "matchesPlayed" INTEGER NOT NULL DEFAULT 0,
          "xp" INTEGER NOT NULL DEFAULT 0,
          "winStreak" INTEGER NOT NULL DEFAULT 0,
          "bestWinStreak" INTEGER NOT NULL DEFAULT 0,
          "updatedAt" TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP
        )`);
      })().catch(error=>{this.progressReady=undefined;throw error});
    }
    await this.progressReady;
  }

  private levelFor(xp:number){return Math.max(1,Math.floor(Math.max(0,xp)/500)+1)}
  private achievementsFor(p:ProgressRow){
    const level=this.levelFor(p.xp);
    const defs=[
      ['FIRST_MATCH','أول مواجهة',p.matchesPlayed,1],
      ['FIRST_WIN','أول انتصار',p.wins,1],
      ['STREAK_3','ثلاثة انتصارات متتالية',p.bestWinStreak,3],
      ['WIN_10','10 انتصارات',p.wins,10],
      ['PLAY_25','25 مباراة',p.matchesPlayed,25],
      ['STREAK_5','خمسة انتصارات متتالية',p.bestWinStreak,5],
      ['LEVEL_5','الوصول للمستوى 5',level,5],
      ['WIN_50','50 انتصارًا',p.wins,50],
      ['PLAY_100','100 مباراة',p.matchesPlayed,100],
      ['LEVEL_10','الوصول للمستوى 10',level,10],
    ] as const;
    return defs.map(([code,title,value,target])=>({code,title,unlocked:value>=target,progress:Math.min(value,target),target}));
  }

  private async progressFor(userId:string){
    await this.ensureProgressTable();
    await rpsPrisma.$executeRawUnsafe('INSERT INTO "PlayerProgress" ("userId") VALUES ($1) ON CONFLICT ("userId") DO NOTHING',userId);
    const rows=await rpsPrisma.$queryRawUnsafe<ProgressRow[]>('SELECT "wins","losses","draws","matchesPlayed","xp","winStreak","bestWinStreak" FROM "PlayerProgress" WHERE "userId"=$1',userId);
    const p=rows[0]||{wins:0,losses:0,draws:0,matchesPlayed:0,xp:0,winStreak:0,bestWinStreak:0};
    return {...p,level:this.levelFor(p.xp),winRate:p.matchesPlayed?Math.round((p.wins/p.matchesPlayed)*100):0,achievements:this.achievementsFor(p)};
  }

  private async playerProfile(userId:string,viewerId:string){
    const user=await rpsPrisma.user.findFirst({where:{id:userId,isActive:true,deletedAt:null},select:{id:true,username:true,createdAt:true,profile:true}});
    if(!user)throw new NotFoundException('Player not found');
    if(userId!==viewerId){
      const blocked=await rpsPrisma.blockedUser.findFirst({where:{OR:[{blockerId:viewerId,blockedId:userId},{blockerId:userId,blockedId:viewerId}]},select:{id:true}});
      if(blocked)throw new NotFoundException('Player not found');
    }
    return {...user,progress:await this.progressFor(userId)};
  }

  private async recordResult(players:string[],winnerId?:string){
    await this.ensureProgressTable();
    await rpsPrisma.$transaction(async tx=>{
      for(const userId of players){
        await tx.$executeRawUnsafe('INSERT INTO "PlayerProgress" ("userId") VALUES ($1) ON CONFLICT ("userId") DO NOTHING',userId);
        if(!winnerId){
          await tx.$executeRawUnsafe('UPDATE "PlayerProgress" SET "draws"="draws"+1,"matchesPlayed"="matchesPlayed"+1,"xp"="xp"+$2,"winStreak"=0,"updatedAt"=CURRENT_TIMESTAMP WHERE "userId"=$1',userId,XP_DRAW);
        }else if(userId===winnerId){
          await tx.$executeRawUnsafe('UPDATE "PlayerProgress" SET "wins"="wins"+1,"matchesPlayed"="matchesPlayed"+1,"xp"="xp"+$2,"winStreak"="winStreak"+1,"bestWinStreak"=GREATEST("bestWinStreak","winStreak"+1),"updatedAt"=CURRENT_TIMESTAMP WHERE "userId"=$1',userId,XP_WIN);
        }else{
          await tx.$executeRawUnsafe('UPDATE "PlayerProgress" SET "losses"="losses"+1,"matchesPlayed"="matchesPlayed"+1,"xp"="xp"+$2,"winStreak"=0,"updatedAt"=CURRENT_TIMESTAMP WHERE "userId"=$1',userId,XP_LOSS);
        }
      }
    });
  }

  private async rpsPlayers(sessionId:string,userId:string) {
    const session = await rpsPrisma.matchSession.findUnique({ where:{ id:sessionId }, include:{ players:true } });
    if (!session || session.status !== MatchSessionStatus.ACTIVE) throw new NotFoundException('Match session not active');
    if (session.players.length !== 2 || !session.players.some(p=>p.userId===userId)) throw new BadRequestException('Invalid RPS session');
    return session.players.map(p=>p.userId);
  }
  private gameFor(sessionId:string, players:string[]) {
    let game=rpsGames.get(sessionId);
    if(!game){ game={round:1,scores:Object.fromEntries(players.map(id=>[id,0])),choices:{},finished:false,progressRecorded:false,endedByDisconnect:false}; rpsGames.set(sessionId,game); }
    return game;
  }
  private beats(a:RpsMove,b:RpsMove){ return (a==='ROCK'&&b==='SCISSORS')||(a==='PAPER'&&b==='ROCK')||(a==='SCISSORS'&&b==='PAPER'); }
  private rpsView(game:RpsGame,me:string,players:string[]){
    const other=players.find(id=>id!==me)!; const mine=game.choices[me]||null; const theirs=game.choices[other]||null; const revealed=!!mine&&!!theirs;
    let roundResult:'WIN'|'LOSE'|'DRAW'|null=null;
    if(revealed){roundResult=mine===theirs?'DRAW':this.beats(mine,theirs)?'WIN':'LOSE';}
    return { phase:game.finished?'FINISHED':revealed?'RESULT':mine?'WAITING':'PLAY', round:game.round, totalRounds:RPS_TOTAL_ROUNDS, myScore:game.scores[me]||0, opponentScore:game.scores[other]||0, myMove:mine, opponentMove:revealed?theirs:null, roundResult, readyForNext:false, finished:game.finished, wonMatch:game.finished&&game.winnerId===me, endedByDisconnect:!!game.endedByDisconnect };
  }
  private decoratedView(sessionId:string,game:RpsGame,me:string,players:string[],opponentOnline=true,reconnectSeconds=0){
    const other=players.find(id=>id!==me)!;
    const rematches=rpsRematchRequests.get(sessionId);
    return {...this.rpsView(game,me,players),rematchRequestedByMe:!!rematches?.has(me),rematchRequestedByOpponent:!!rematches?.has(other),opponentOnline,opponentReconnectSeconds:reconnectSeconds};
  }

  @Post('devices/register') async registerDevice(@Req() req:any,@Body() dto:RegisterDeviceDto){try{return await this.integrations.registerDevice(req.user.id,dto)}catch(error){this.translate(error)}}
  @Get('devices') async devices(@Req() req:any){return this.integrations.activeDevices(req.user.id)}
  @Delete('devices/:id') async revokeDevice(@Req() req:any,@Param('id') id:string){return this.integrations.revokeDevice(req.user.id,id)}
  @Get('media/capabilities') mediaCapabilities(){return this.integrations.mediaCapabilities()}
  @Post('media/prepare') async prepareMedia(@Req() req:any,@Body() dto:PrepareMediaDto){try{return await this.integrations.prepareMedia(req.user.id,dto)}catch(error){this.translate(error)}}
  @Post('media/:id/complete') async completeMedia(@Req() req:any,@Param('id') id:string){try{const row=await this.integrations.markUploaded(req.user.id,id);return{id:row.id,status:row.status,chatId:row.chatId,fileName:row.fileName,mimeType:row.mimeType,byteSize:row.byteSize,publicUrl:row.publicUrl,uploadedAt:row.uploadedAt}}catch(error){this.translate(error)}}
  @Post('chats/:chatId/media-message') async attachMedia(@Req() req:any,@Param('chatId') chatId:string,@Body() dto:AttachMediaDto){try{const result=await this.integrations.attachUploadedMessage(req.user.id,chatId,dto.uploadId,dto.replyToId,dto.caption);batch2Events.emitSecureMediaAttached({chatId,senderId:req.user.id,recipientIds:result.recipientIds,message:result.message});return result.message}catch(error){this.translate(error)}}

  @Get('players/me/profile') async myPlayerProfile(@Req() req:any){return this.playerProfile(req.user.id,req.user.id)}
  @Get('players/:userId/profile') async publicPlayerProfile(@Req() req:any,@Param('userId') userId:string){return this.playerProfile(userId,req.user.id)}

  @Get('rps/session/:sessionId/state') async rpsState(@Req() req:any,@Param('sessionId') sessionId:string){
    const players=await this.rpsPlayers(sessionId,req.user.id);const game=this.gameFor(sessionId,players);const other=players.find(id=>id!==req.user.id)!;
    const profile=await rpsPrisma.profile.findUnique({where:{userId:other},select:{isOnline:true,lastSeenAt:true}});
    let reconnectSeconds=0;
    if(profile?.isOnline){rpsOfflineSince.delete(`${sessionId}:${other}`)}else{
      const key=`${sessionId}:${other}`;const start=profile?.lastSeenAt?.getTime()||rpsOfflineSince.get(key)||Date.now();if(!rpsOfflineSince.has(key))rpsOfflineSince.set(key,start);
      const elapsed=Math.max(0,Math.floor((Date.now()-start)/1000));reconnectSeconds=Math.max(0,RPS_RECONNECT_SECONDS-elapsed);
      if(elapsed>=RPS_RECONNECT_SECONDS&&!game.finished){game.finished=true;game.winnerId=req.user.id;game.endedByDisconnect=true;if(!game.progressRecorded){game.progressRecorded=true;await this.recordResult(players,game.winnerId)}}
    }
    return this.decoratedView(sessionId,game,req.user.id,players,profile?.isOnline===true,reconnectSeconds)
  }
  @Post('rps/session/:sessionId/move') async rpsMove(@Req() req:any,@Param('sessionId') sessionId:string,@Body() dto:RpsMoveDto){
    const move=dto.move.toUpperCase() as RpsMove;if(!['ROCK','PAPER','SCISSORS'].includes(move))throw new BadRequestException('Invalid move');
    const players=await this.rpsPlayers(sessionId,req.user.id);const game=this.gameFor(sessionId,players);if(game.finished)throw new BadRequestException('Game finished');if(game.choices[req.user.id])return this.decoratedView(sessionId,game,req.user.id,players);
    game.choices[req.user.id]=move;const other=players.find(id=>id!==req.user.id)!;const otherMove=game.choices[other];
    if(otherMove&&move!==otherMove){const winner=this.beats(move,otherMove)?req.user.id:other;game.scores[winner]=(game.scores[winner]||0)+1;}
    return this.decoratedView(sessionId,game,req.user.id,players);
  }
  @Post('rps/session/:sessionId/next') async rpsNext(@Req() req:any,@Param('sessionId') sessionId:string){
    const players=await this.rpsPlayers(sessionId,req.user.id);const game=this.gameFor(sessionId,players);
    if(game.finished)return this.decoratedView(sessionId,game,req.user.id,players);
    if(Object.keys(game.choices).length<2)return this.decoratedView(sessionId,game,req.user.id,players);
    if(game.round>=RPS_TOTAL_ROUNDS){
      game.finished=true;
      const [a,b]=players;const aScore=game.scores[a]||0;const bScore=game.scores[b]||0;
      game.winnerId=aScore===bScore?undefined:(aScore>bScore?a:b);
      if(!game.progressRecorded){game.progressRecorded=true;await this.recordResult(players,game.winnerId);}
      return this.decoratedView(sessionId,game,req.user.id,players);
    }
    game.round+=1;game.choices={};return this.decoratedView(sessionId,game,req.user.id,players);
  }
  @Post('rps/session/:sessionId/rematch') async rpsRematch(@Req() req:any,@Param('sessionId') sessionId:string){
    const players=await this.rpsPlayers(sessionId,req.user.id);const old=this.gameFor(sessionId,players);if(!old.finished)throw new BadRequestException('Game not finished');
    const requests=rpsRematchRequests.get(sessionId)||new Set<string>();requests.add(req.user.id);rpsRematchRequests.set(sessionId,requests);
    if(requests.size<2)return this.decoratedView(sessionId,old,req.user.id,players);
    const game:RpsGame={round:1,scores:Object.fromEntries(players.map(id=>[id,0])),choices:{},finished:false,progressRecorded:false,endedByDisconnect:false};
    rpsGames.set(sessionId,game);rpsRematchRequests.delete(sessionId);for(const id of players)rpsOfflineSince.delete(`${sessionId}:${id}`);
    return this.decoratedView(sessionId,game,req.user.id,players);
  }
}

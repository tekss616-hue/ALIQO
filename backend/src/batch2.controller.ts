import { BadRequestException, Body, Controller, Delete, Get, NotFoundException, Param, Post, Req, UseGuards } from '@nestjs/common';
import { AuthGuard } from '@nestjs/passport';
import { DevicePlatform, MatchSessionStatus, PrismaClient } from '@prisma/client';
import { IsEnum, IsInt, IsOptional, IsString, Max, MaxLength, Min, MinLength, Matches } from 'class-validator';
import { Batch2Integrations } from './batch2-integrations';
import { batch2Events } from './batch2-events';

const rpsPrisma = new PrismaClient();
const RPS_TOTAL_ROUNDS = 10;
type RpsMove = 'ROCK' | 'PAPER' | 'SCISSORS';
type RpsGame = { round:number; scores:Record<string,number>; choices:Record<string,RpsMove>; nextReady:Record<string,boolean>; finished:boolean; winnerId?:string };
const rpsGames = new Map<string,RpsGame>();

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

  private async rpsPlayers(sessionId:string,userId:string) {
    const session = await rpsPrisma.matchSession.findUnique({ where:{ id:sessionId }, include:{ players:true } });
    if (!session || session.status !== MatchSessionStatus.ACTIVE) throw new NotFoundException('Match session not active');
    if (session.players.length !== 2 || !session.players.some(p=>p.userId===userId)) throw new BadRequestException('Invalid RPS session');
    return session.players.map(p=>p.userId);
  }
  private gameFor(sessionId:string, players:string[]) {
    let game=rpsGames.get(sessionId);
    if(!game){ game={round:1,scores:Object.fromEntries(players.map(id=>[id,0])),choices:{},nextReady:{},finished:false}; rpsGames.set(sessionId,game); }
    return game;
  }
  private beats(a:RpsMove,b:RpsMove){ return (a==='ROCK'&&b==='SCISSORS')||(a==='PAPER'&&b==='ROCK')||(a==='SCISSORS'&&b==='PAPER'); }
  private rpsView(game:RpsGame,me:string,players:string[]){
    const other=players.find(id=>id!==me)!; const mine=game.choices[me]||null; const theirs=game.choices[other]||null; const revealed=!!mine&&!!theirs;
    let roundResult:'WIN'|'LOSE'|'DRAW'|null=null;
    if(revealed){roundResult=mine===theirs?'DRAW':this.beats(mine,theirs)?'WIN':'LOSE';}
    return { phase:game.finished?'FINISHED':revealed?'RESULT':mine?'WAITING':'PLAY', round:game.round, totalRounds:RPS_TOTAL_ROUNDS, myScore:game.scores[me]||0, opponentScore:game.scores[other]||0, myMove:mine, opponentMove:revealed?theirs:null, roundResult, readyForNext:!!game.nextReady[me], finished:game.finished, wonMatch:game.finished&&game.winnerId===me };
  }

  @Post('devices/register') async registerDevice(@Req() req:any,@Body() dto:RegisterDeviceDto){try{return await this.integrations.registerDevice(req.user.id,dto)}catch(error){this.translate(error)}}
  @Get('devices') async devices(@Req() req:any){return this.integrations.activeDevices(req.user.id)}
  @Delete('devices/:id') async revokeDevice(@Req() req:any,@Param('id') id:string){return this.integrations.revokeDevice(req.user.id,id)}
  @Get('media/capabilities') mediaCapabilities(){return this.integrations.mediaCapabilities()}
  @Post('media/prepare') async prepareMedia(@Req() req:any,@Body() dto:PrepareMediaDto){try{return await this.integrations.prepareMedia(req.user.id,dto)}catch(error){this.translate(error)}}
  @Post('media/:id/complete') async completeMedia(@Req() req:any,@Param('id') id:string){try{const row=await this.integrations.markUploaded(req.user.id,id);return{id:row.id,status:row.status,chatId:row.chatId,fileName:row.fileName,mimeType:row.mimeType,byteSize:row.byteSize,publicUrl:row.publicUrl,uploadedAt:row.uploadedAt}}catch(error){this.translate(error)}}
  @Post('chats/:chatId/media-message') async attachMedia(@Req() req:any,@Param('chatId') chatId:string,@Body() dto:AttachMediaDto){try{const result=await this.integrations.attachUploadedMessage(req.user.id,chatId,dto.uploadId,dto.replyToId,dto.caption);batch2Events.emitSecureMediaAttached({chatId,senderId:req.user.id,recipientIds:result.recipientIds,message:result.message});return result.message}catch(error){this.translate(error)}}

  @Get('rps/session/:sessionId/state') async rpsState(@Req() req:any,@Param('sessionId') sessionId:string){const players=await this.rpsPlayers(sessionId,req.user.id);return this.rpsView(this.gameFor(sessionId,players),req.user.id,players)}
  @Post('rps/session/:sessionId/move') async rpsMove(@Req() req:any,@Param('sessionId') sessionId:string,@Body() dto:RpsMoveDto){
    const move=dto.move.toUpperCase() as RpsMove;if(!['ROCK','PAPER','SCISSORS'].includes(move))throw new BadRequestException('Invalid move');
    const players=await this.rpsPlayers(sessionId,req.user.id);const game=this.gameFor(sessionId,players);if(game.finished)throw new BadRequestException('Game finished');if(game.choices[req.user.id])return this.rpsView(game,req.user.id,players);
    game.choices[req.user.id]=move;const other=players.find(id=>id!==req.user.id)!;const otherMove=game.choices[other];
    if(otherMove&&move!==otherMove){const winner=this.beats(move,otherMove)?req.user.id:other;game.scores[winner]=(game.scores[winner]||0)+1;}
    return this.rpsView(game,req.user.id,players);
  }
  @Post('rps/session/:sessionId/next') async rpsNext(@Req() req:any,@Param('sessionId') sessionId:string){
    const players=await this.rpsPlayers(sessionId,req.user.id);const game=this.gameFor(sessionId,players);
    if(game.finished)return this.rpsView(game,req.user.id,players);
    if(Object.keys(game.choices).length<2)return this.rpsView(game,req.user.id,players);
    game.nextReady[req.user.id]=true;
    if(!players.every(id=>game.nextReady[id]))return this.rpsView(game,req.user.id,players);
    if(game.round>=RPS_TOTAL_ROUNDS){
      game.finished=true;
      const [a,b]=players;const aScore=game.scores[a]||0;const bScore=game.scores[b]||0;
      game.winnerId=aScore===bScore?undefined:(aScore>bScore?a:b);
      return this.rpsView(game,req.user.id,players);
    }
    game.round+=1;game.choices={};game.nextReady={};return this.rpsView(game,req.user.id,players);
  }
  @Post('rps/session/:sessionId/rematch') async rpsRematch(@Req() req:any,@Param('sessionId') sessionId:string){const players=await this.rpsPlayers(sessionId,req.user.id);const game:RpsGame={round:1,scores:Object.fromEntries(players.map(id=>[id,0])),choices:{},nextReady:{},finished:false};rpsGames.set(sessionId,game);return this.rpsView(game,req.user.id,players)}
}

import { DevicePlatform, MediaUploadStatus, PrismaClient } from '@prisma/client';
import { createHash, randomBytes } from 'crypto';
import { MediaStorageService, MediaKind } from './media-storage';
import { PushProvider } from './push-provider';

const prisma = new PrismaClient();
const sha256 = (value:string) => createHash('sha256').update(value).digest('hex');

export type RegisterDeviceInput={token:string;platform?:DevicePlatform;appVersion?:string};
export type PrepareMediaInput={chatId:string;fileName:string;mimeType:string;byteSize:number;sha256?:string};

export class Batch2Integrations {
  constructor(private readonly media=new MediaStorageService(),private readonly push=new PushProvider()){}

  async registerDevice(userId:string,input:RegisterDeviceInput){
    const token=input.token.trim();
    if(token.length<16||token.length>4096) throw new Error('INVALID_DEVICE_TOKEN');
    const tokenHash=sha256(token);
    const row=await prisma.deviceToken.upsert({where:{tokenHash},create:{userId,tokenHash,tokenValue:token,platform:input.platform||DevicePlatform.ANDROID,appVersion:input.appVersion?.slice(0,40)},update:{userId,tokenValue:token,platform:input.platform||DevicePlatform.ANDROID,appVersion:input.appVersion?.slice(0,40),lastSeenAt:new Date(),revokedAt:null}});
    return {id:row.id,platform:row.platform,lastSeenAt:row.lastSeenAt};
  }

  async revokeDevice(userId:string,id:string){
    const result=await prisma.deviceToken.updateMany({where:{id,userId,revokedAt:null},data:{revokedAt:new Date()}});
    return {ok:result.count>0};
  }

  async activeDevices(userId:string){
    return prisma.deviceToken.findMany({where:{userId,revokedAt:null},select:{id:true,platform:true,appVersion:true,lastSeenAt:true,createdAt:true},orderBy:{lastSeenAt:'desc'}});
  }

  async prepareMedia(userId:string,input:PrepareMediaInput){
    const member=await prisma.chatMember.findUnique({where:{chatId_userId:{chatId:input.chatId,userId}}});
    if(!member) throw new Error('NOT_CHAT_MEMBER');
    const kind=this.media.classify(input.mimeType);
    this.media.validate(kind,input.mimeType,input.byteSize);
    const objectKey=this.media.objectKey(input.chatId,userId,input.fileName,randomBytes(12).toString('hex'));
    const expiresAt=new Date(Date.now()+15*60_000);
    const row=await prisma.mediaUpload.create({data:{userId,chatId:input.chatId,status:MediaUploadStatus.PENDING,objectKey,fileName:input.fileName.slice(0,240),mimeType:input.mimeType.slice(0,120),byteSize:input.byteSize,sha256:input.sha256?.toLowerCase(),expiresAt}});
    const signed=await this.media.signUpload({objectKey,mimeType:row.mimeType,byteSize:row.byteSize,expiresAt});
    return {uploadId:row.id,kind,objectKey,expiresAt,...signed};
  }

  async markUploaded(userId:string,uploadId:string,publicUrl:string){
    const row=await prisma.mediaUpload.findFirst({where:{id:uploadId,userId}});
    if(!row) throw new Error('UPLOAD_NOT_FOUND');
    if(row.expiresAt<=new Date()){await prisma.mediaUpload.update({where:{id:uploadId},data:{status:MediaUploadStatus.EXPIRED}});throw new Error('UPLOAD_EXPIRED');}
    if(row.status!==MediaUploadStatus.PENDING) throw new Error('UPLOAD_STATE_INVALID');
    if(!this.media.validatePublicUrl(publicUrl,row.objectKey)) throw new Error('INVALID_MEDIA_URL');
    return prisma.mediaUpload.update({where:{id:uploadId},data:{status:MediaUploadStatus.UPLOADED,publicUrl,uploadedAt:new Date()}});
  }

  async consumeUploaded(userId:string,chatId:string,uploadId:string){
    const row=await prisma.mediaUpload.findFirst({where:{id:uploadId,userId,chatId,status:MediaUploadStatus.UPLOADED}});
    if(!row||!row.publicUrl) throw new Error('UPLOAD_NOT_READY');
    const updated=await prisma.mediaUpload.updateMany({where:{id:row.id,status:MediaUploadStatus.UPLOADED},data:{status:MediaUploadStatus.ATTACHED,attachedAt:new Date()}});
    if(updated.count!==1) throw new Error('UPLOAD_ALREADY_CONSUMED');
    return row;
  }

  async pushToUser(userId:string,title:string,body:string,data:Record<string,string>={}){
    const devices=await prisma.deviceToken.findMany({where:{userId,revokedAt:null},select:{id:true,tokenValue:true}});
    const results=[] as {id:string;ok:boolean;reason?:string}[];
    for(const device of devices){
      const result=await this.push.send(device.tokenValue,{title,body,data});
      results.push({id:device.id,...result});
      if(!result.ok&&result.reason==='TOKEN_INVALID') await prisma.deviceToken.update({where:{id:device.id},data:{revokedAt:new Date()}});
    }
    return results;
  }
}

export const inferMessageType=(kind:MediaKind)=>kind==='image'?'IMAGE':kind==='video'?'VIDEO':kind==='audio'?'VOICE':'FILE';

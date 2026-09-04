import { EventEmitter } from 'events';

export type SecureMediaAttachedEvent = {
  chatId: string;
  senderId: string;
  recipientIds: string[];
  message: any;
};

class Batch2EventBus extends EventEmitter {
  emitSecureMediaAttached(event: SecureMediaAttachedEvent) {
    this.emit('secure-media:attached', event);
  }

  onSecureMediaAttached(listener: (event: SecureMediaAttachedEvent) => void) {
    this.on('secure-media:attached', listener);
    return () => this.off('secure-media:attached', listener);
  }
}

export const batch2Events = new Batch2EventBus();

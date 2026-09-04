FROM node:20-bookworm-slim AS build
WORKDIR /app

RUN apt-get update -y \
 && apt-get install -y --no-install-recommends openssl ca-certificates \
 && rm -rf /var/lib/apt/lists/*

COPY package*.json ./
RUN npm install

COPY prisma ./prisma
RUN npx prisma generate

COPY tsconfig.json ./
COPY src ./src
RUN node -e "const fs=require('fs'); const p='src/main.ts'; let s=fs.readFileSync(p,'utf8'); const before='await tx.$queryRaw`SELECT pg_advisory_xact_lock(741852963)`;'; const after='await tx.$executeRaw`SELECT pg_advisory_xact_lock(741852963)`;'; if(!s.includes(before)) throw new Error('matchmaking advisory lock source not found'); s=s.replace(before,after); fs.writeFileSync(p,s);"
RUN npm run build

FROM node:20-bookworm-slim AS runtime
WORKDIR /app
ENV NODE_ENV=production

RUN apt-get update -y \
 && apt-get install -y --no-install-recommends openssl ca-certificates \
 && rm -rf /var/lib/apt/lists/*

COPY package*.json ./
RUN npm install --omit=dev && npm cache clean --force
COPY --from=build /app/node_modules/.prisma /app/node_modules/.prisma
COPY --from=build /app/node_modules/@prisma /app/node_modules/@prisma
COPY --from=build /app/dist ./dist
COPY prisma ./prisma

EXPOSE 3000
CMD ["sh", "-c", "npx prisma db push && node dist/main.js"]

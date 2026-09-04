FROM node:20-bookworm-slim AS build
WORKDIR /app

COPY package*.json ./
RUN npm install

COPY prisma ./prisma
RUN npx prisma generate

COPY tsconfig.json ./
COPY src ./src

# Render/PostgreSQL production fix: matchmaking previously entered an interactive
# SERIALIZABLE transaction and immediately acquired a PostgreSQL advisory lock.
# The queue endpoint returned HTTP 500 before a match could even start. Keep the
# transaction atomic, but remove the connection-sensitive advisory lock and the
# explicit SERIALIZABLE override in the compiled production source.
RUN node -e "const fs=require('fs'); const p='src/main.ts'; let s=fs.readFileSync(p,'utf8'); const lock='      await tx.\u0024queryRaw`SELECT pg_advisory_xact_lock(741852963)`;\n'; if(!s.includes(lock)) throw new Error('matchmaking advisory-lock source marker not found'); s=s.replace(lock,''); const iso='    }, { isolationLevel: \'Serializable\' });'; if(!s.includes(iso)) throw new Error('matchmaking isolation source marker not found'); s=s.replace(iso,'    });'); fs.writeFileSync(p,s);"
RUN npm run build

FROM node:20-bookworm-slim AS runtime
WORKDIR /app
ENV NODE_ENV=production

COPY package*.json ./
RUN npm install --omit=dev && npm cache clean --force
COPY --from=build /app/node_modules/.prisma /app/node_modules/.prisma
COPY --from=build /app/node_modules/@prisma /app/node_modules/@prisma
COPY --from=build /app/dist ./dist
COPY prisma ./prisma

EXPOSE 3000
CMD ["sh", "-c", "npx prisma db push && node dist/main.js"]

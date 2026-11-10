FROM node:22-alpine AS build

WORKDIR /app

COPY package*.json ./
RUN npm ci

COPY . .
# Обычный образ сохраняет Telegram-вход; стенд может явно выбрать demo.
ARG FRONTEND_MODE=production
RUN if [ "$FRONTEND_MODE" = "demo" ]; then npm run build:demo; \
    elif [ "$FRONTEND_MODE" = "production" ]; then npm run build; \
    else echo "Unsupported FRONTEND_MODE: $FRONTEND_MODE" >&2; exit 1; fi


FROM nginx:alpine

COPY --from=build /app/dist /usr/share/nginx/html

EXPOSE 80

CMD ["nginx", "-g", "daemon off;"]

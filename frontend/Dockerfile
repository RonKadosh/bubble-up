FROM node:20-alpine AS builder
WORKDIR /app
COPY package.json package-lock.json* ./
RUN npm install
COPY . .
# Off by default — production/CI builds omit it, so the dev-only password page
# (/login/testing) is stripped. The local docker-compose passes this as `true`.
ARG VITE_DEV_LOGIN=false
ENV VITE_DEV_LOGIN=$VITE_DEV_LOGIN
RUN npm run build

FROM nginx:alpine
COPY --from=builder /app/dist /usr/share/nginx/html
COPY nginx.conf /etc/nginx/conf.d/default.conf
EXPOSE 80
CMD ["nginx", "-g", "daemon off;"]

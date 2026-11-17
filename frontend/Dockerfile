# -----------------------------
# Stage 1 : Build React App
# -----------------------------
FROM node:22-alpine AS build

WORKDIR /app

# Copy dependency files
COPY package*.json ./

# Install dependencies
RUN npm ci

# Copy source code
COPY . .

# Build argument for API Gateway URL (consumed at build time by Vite)
ARG VITE_API_GATEWAY_URL=http://localhost:8080
ENV VITE_API_GATEWAY_URL=$VITE_API_GATEWAY_URL

# Build production files
RUN npm run build


# -----------------------------
# Stage 2 : Nginx
# -----------------------------
FROM nginx:alpine

# Remove default nginx files
RUN rm -rf /usr/share/nginx/html/*

# Copy production build
COPY --from=build /app/dist /usr/share/nginx/html

# Copy nginx configuration
COPY nginx.conf /etc/nginx/conf.d/default.conf

EXPOSE 80

CMD ["nginx","-g","daemon off;"]
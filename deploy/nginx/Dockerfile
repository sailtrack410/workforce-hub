FROM node:20-alpine AS admin-build

WORKDIR /workspace/admin-web
COPY admin-web/package*.json ./
RUN npm ci
COPY admin-web/ ./
RUN npm run build

FROM node:20-alpine AS employee-build

WORKDIR /workspace/employee-web
COPY employee-web/package*.json ./
RUN npm ci
COPY employee-web/ ./
COPY plugins/hello-plugin/frontend/ /workspace/plugins/hello-plugin/frontend/
RUN npm run build
RUN npm run build:plugin-poc

FROM nginx:1.27-alpine

COPY deploy/nginx/default.conf /etc/nginx/conf.d/default.conf
COPY --from=employee-build /workspace/employee-web/dist /usr/share/nginx/html
COPY --from=admin-build /workspace/admin-web/dist /usr/share/nginx/html/admin

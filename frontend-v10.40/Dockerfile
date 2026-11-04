FROM node:24-alpine AS build
WORKDIR /app
COPY package.json package-lock.json ./
RUN npm ci
COPY . .
ENV VITE_API_URL=/api
# Аналитика (v10.40). Значения можно переопределить: docker build --build-arg VITE_ANALYTICS_ENV=staging …
ARG VITE_ANALYTICS_ENV=production
ARG VITE_ANALYTICS_TEST_USER_IDS=
ARG VITE_YANDEX_METRIKA_ID=114072239
ENV VITE_ANALYTICS_ENV=$VITE_ANALYTICS_ENV \
    VITE_ANALYTICS_TEST_USER_IDS=$VITE_ANALYTICS_TEST_USER_IDS \
    VITE_YANDEX_METRIKA_ID=$VITE_YANDEX_METRIKA_ID
RUN npm run build

FROM nginx:1.27-alpine
COPY nginx.conf /etc/nginx/conf.d/default.conf
COPY --from=build /app/dist /usr/share/nginx/html
EXPOSE 80

# Playwright E2E 运行镜像（浏览器已内置，零宿主依赖）
# 注意：@playwright/test 版本必须与镜像 tag 匹配
FROM mcr.microsoft.com/playwright:v1.49.1-jammy
WORKDIR /work
COPY package.json ./
RUN npm install --no-audit --no-fund
COPY . .

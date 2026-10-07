import { defineConfig } from '@vben/vite-config';

export default defineConfig(async () => {
  return {
    application: {},
    vite: {
      server: {
        proxy: {
          // 后端（Spring Boot，/api/v1 前缀）—— 优先匹配更长前缀
          '/api/v1': {
            changeOrigin: true,
            target: 'http://localhost:8080',
            ws: true,
          },
          // 其余 /api 走本地 nitro mock（device/product 过渡期）
          '/api': {
            changeOrigin: true,
            rewrite: (path) => path.replace(/^\/api/, ''),
            target: 'http://localhost:5320/api',
            ws: true,
          },
        },
      },
    },
  };
});

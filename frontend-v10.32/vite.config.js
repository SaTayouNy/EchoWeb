import { defineConfig, loadEnv } from 'vite';

// VITE_API_URL — адрес API для браузера (например, /api или https://api.example.ru).
// VITE_DEV_PROXY — куда проксировать /api при `npm run dev` (например, http://localhost:8000),
// чтобы локально не настраивать CORS.
export default defineConfig(({ mode }) => {
  const env = loadEnv(mode, process.cwd(), '');
  const proxy = {
    '/tmdb-images': {
      target: 'https://image.tmdb.org',
      changeOrigin: true,
      rewrite: (path) => path.replace(/^\/tmdb-images/, '/t/p'),
    },
  };
  if (env.VITE_DEV_PROXY) {
    proxy['/api'] = {
      target: env.VITE_DEV_PROXY,
      changeOrigin: true,
      rewrite: (path) => path.replace(/^\/api/, ''),
    };
  }
  return {
    base: './',
    server: { proxy },
  };
});

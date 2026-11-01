import tailwindcss from '@tailwindcss/vite'
import vue from '@vitejs/plugin-vue'
import AutoImport from 'unplugin-auto-import/vite'
import Components from 'unplugin-vue-components/vite'
import { ElementPlusResolver } from 'unplugin-vue-components/resolvers'
import { defineConfig } from 'vite'

// 网关地址：默认本机 9000；Docker 部署时用 19000
const gatewayTarget = process.env.VITE_GATEWAY_TARGET || 'http://localhost:9000'

// https://vite.dev/config/
export default defineConfig({
  plugins: [
    vue(),
    tailwindcss(),
    // Element Plus 按需引入：模板里直接用 <el-xxx> 即可，无需逐个 import
    AutoImport({
      resolvers: [ElementPlusResolver()],
      dts: 'src/auto-imports.d.ts',
    }),
    Components({
      resolvers: [ElementPlusResolver()],
      dts: 'src/components.d.ts',
    }),
  ],
  server: {
    port: 5173,
    proxy: {
      // 开发期由 Vite 代理转发到网关，由网关统一鉴权与路由，避免浏览器跨域。
      // 默认连本机 9000；用 Docker 部署时网关映射到 19000，
      // 可通过 VITE_GATEWAY_TARGET 覆盖，无需改代码。
      '/api': {
        target: gatewayTarget,
        changeOrigin: true,
      },
      '/images': {
        target: gatewayTarget,
        changeOrigin: true,
      },
      // 用户头像与商品图片同一套静态映射方案（#51）
      '/avatars': {
        target: gatewayTarget,
        changeOrigin: true,
      },
    },
  },
})

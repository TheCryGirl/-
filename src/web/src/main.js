import { createApp } from 'vue'
import App from './App.vue'
import router from './router'
import './style.css'

// Element Plus 组件与样式由 unplugin 按需注入（见 vite.config.js），
// 中文语言包在 App.vue 的 el-config-provider 中提供，不再全量 app.use(ElementPlus)
createApp(App).use(router).mount('#app')

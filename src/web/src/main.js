import { createApp } from 'vue'
import App from './App.vue'
import router from './router'
import { revealDirective } from './directives/reveal'
import './style.css'
// CORAL 视觉重塑层：必须在 style.css 之后，才能覆盖旧令牌与组件皮肤
import './styles/coral.css'

// Element Plus 组件与样式由 unplugin 按需注入（见 vite.config.js），
// 中文语言包在 App.vue 的 el-config-provider 中提供，不再全量 app.use(ElementPlus)
const app = createApp(App)
app.directive('reveal', revealDirective)
app.use(router).mount('#app')
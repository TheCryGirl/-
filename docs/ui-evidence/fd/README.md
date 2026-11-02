# fd UI 改造交付说明（F01–F04）

- 任务书：[docs/ui-plan/fd.md](../../ui-plan/fd.md)
- 分支：`feature/ui-fd-login`（基于 main `877e6e8`，**未合并**）
- 提交：F01 `8ac10a3`；F02–F04 `14ec585`
- 范围：仅 fd 负责的登录、注册、收藏、浏览足迹四页；未改 API、store、router、公共组件、依赖与锁文件

## 一、页面与改动

| 编号 | 页面 | 改动文件 | 主要内容 |
| --- | --- | --- | --- |
| F01 | 登录 | `src/web/src/views/Login.vue` | `el-form` / `el-form-item`、密码可见切换、提交 loading、就地错误提示；原字段、校验文案、`userApi.login`、`setLogin`、`redirectTarget` 全部保留 |
| F02 | 注册 | `src/web/src/views/Register.vue` | 五个原字段换 `el-form-item`，原校验规则逐项显示；提交 loading 与防重复；修正 HTTP 200 业务失败被误报成功的问题 |
| F03 | 收藏 | `src/web/src/views/Favorites.vue` | 保留原卡片网格与失效商品处理；加载/空/失败三态；取消收藏确认与逐项忙状态；分页统一用 `usePagination` + `el-pagination` |
| F04 | 浏览足迹 | `src/web/src/views/BrowseHistory.vue` | 保留原列表顺序与失效商品处理；三态；单条删除与清空均走确认；数量选择改 `el-select` |

四个页面统一使用 Element Plus 默认视觉风格（主色 `#409EFF`、4px 圆角），与已合并的 F01 试点及 `AdminUsers` 页面保持一致，未引入自定义主题或图标依赖。

## 二、统一行为

- **三态分离**：加载中、成功但为空、请求失败分别渲染，失败不会伪装成空列表。
- **确认操作**：删除、取消收藏、清空均使用公共 `confirmAction`；取消或 Esc 不发写请求、不提示失败。
- **成功判断**：`Result<Void>` 以 `isOk(payload)` 判断，不以 HTTP 200 代替业务成功。
- **忙状态**：按 `productId` 记录当前操作项，不使用数组下标，避免列表顺序变化后串行。
- **原行为保留**：字段、校验规则与文案、收藏/历史排序、失效商品规则、商品跳转 `productId`、登录返回目标均未改变。
- **testid**：`login-*`、`register-*`、`favorites-remove-<productId>`、`history-remove-<productId>` 等，便于验收脚本定位。

## 三、本地运行与构建

```bash
cd src/web
pnpm install --frozen-lockfile
pnpm dev        # http://localhost:5173
pnpm run build  # 生产构建
```

生产构建已通过（Vite 8.3.4，F01 984ms，F02–F04 971ms；分页复用 `usePagination` 后复验 1.00s，退出码 0）。本地联调使用仓库外的临时 mock 网关（9000）模拟统一响应，未提交任何 mock 代码、演示账号或令牌。

## 四、证据入口

- [F01 登录页试点](F01-登录页试点.md)
- [F02 注册页](F02-注册页.md)
- [F03 收藏页](F03-收藏页.md)
- [F04 浏览足迹](F04-浏览足迹.md)
- 截图目录：[images](images)

协作边界与未测项分别见 [协作说明](协作说明.md) 与 [验收核对](验收核对.md)。

# P5-07 OA 前端复核

初次main 56497cb的CI前端门禁发现queryKeys元测试按变量名`permVersion`扫描；CentralAccessReview实际已有权限版本，但局部名`permissionVersion`不符合约定。

仅把已有prop解构为本地`permVersion`别名，公开prop、缓存数组值、API和行为不变。不增加EXEMPT豁免。首次本地机械改prop同时造成编译错误，已保留prop改为解构别名；最终重新运行`pnpm --dir oa-console test`，14文件125测试通过，`pnpm --dir oa-console build`通过。原P503/P506真实浏览器证据仍适用，纯局部名变更无界面差异。

最终Git/CI由auth仓库phase-5/CI_RESULT记录，不能将初次失败run宣称PASS。OA用户既有进度/部署文件及tmp/.local未修改、未暂存。

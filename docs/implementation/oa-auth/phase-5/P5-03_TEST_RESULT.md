# P5-03 OA审批依据

PASS。基线10c1348，分支feat/iam-p5-portal-pilots。Workbench对CENTRAL_ACCESS待办先展示固定申请依据，再办理；依据读取失败不显示审批按钮。继续使用已有逐任务指派校验接口及complete命令，不改变OA或auth业务权威。

- OA console build PASS；CentralAccessReview 3项组件测试PASS。
- 真实Casdoor JWT/OA/PG/Kafka/Flowable/auth/SpiceDB：auth仓deploy/governance-p4-e2e.py可选--p5-playwright-module启动隔离console15276。e2e-44c5f7f6f2，OA浏览器3项及整链62项PASS。真实点击批准后收到可信回调并完成投影，外部页面及直接查询均拒绝审批依据。
- 1440审批依据/外部拒绝截图已实际查看，证据在auth仓docs/implementation/oa-auth/phase-5/evidence/p5-03。测试实例精确允许15276 Origin，不更改共享环境。
- Code Hygiene为IMPLEMENTATION_COMPLETE_WITH_LIMITATIONS，唯一限制FORMAT_TOOL_NOT_AVAILABLE，沿用已有风格。

本片仅改WorkbenchPage、新CentralAccessReview及对应测试。用户既有CODEX_PROGRESS、identity-authz-governance进度/部署文件、tmp/.local保留。P5整体状态由auth仓原63节点DAG管理，当前下一片P5-04，P6前停止。本地提交不是生产部署。

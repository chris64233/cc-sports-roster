# cc-sports-roster

管理运动员资格、俱乐部名单和注册窗口。

## 开发环境

- JDK 21
- Spring Boot 4.1.1
- Maven Wrapper 3.9.9
- H2

## 本地运行

启动服务：

    ./mvnw spring-boot:run

运行测试：

    ./mvnw clean test

## 主要业务规则

### 赛季名单注册（round 001）

- 赛季定义注册窗口、名单人数上限、外籍球员上限和本土培养球员下限。
- 球队提交完整名单时，所有球员必须存在、不重复、在提交日未处于禁赛期，
  名单不超过总人数与外籍上限，并达到本土培养下限；提交只能发生在注册窗口内。
- 同一球员同一赛季只能注册给一支球队；整份名单原子通过，并发争抢最多一个成功。
- 窗口内允许凭名单版本号替换名单，版本陈旧返回冲突，校验失败时原名单保持不变。
- 相同幂等键 + 相同内容重放返回原结果，内容不同返回冲突。

### 赛季内球员转会与名单名额原子交换（round 002）

转会申请指定原球队（fromTeam）、目标球队（toTeam）、期望生效日和一组带交换方向的
球员，允许双方在一笔申请中交换多名球员。

**状态机**：`CREATED → AWAITING_CONFIRMATION → CONFIRMED → EXECUTED`；
任一方拒绝进入 `REJECTED`，执行时规则不满足或名单已变化进入 `FAILED`，三者均为终态。

**执行前置条件**（全部满足才执行，否则申请置 FAILED 并给出全部违规明细）：

1. **转会窗口**：期望生效日必须落在赛季注册/转会窗口内。
2. **禁赛阻断**：涉及球员在生效日均不处于禁赛期（`suspensionUntil >= 生效日` 即阻断）。
3. **双方确认**：原球队与目标球队都对申请作出 CONFIRM 决定；任一 REJECT 申请即 REJECTED。
4. **名单版本未变化**：申请创建时冻结双方名单版本（JPA `@Version`），执行时若任一名单
   版本与冻结版本不一致（期间发生过名单替换等），整笔转会失败。
5. **队籍一致**：每名球员当前必须注册在其声明方向的交出方球队。

**原子交换与规则判定**：

- 执行在单个数据库事务内完成：先从原球队释放球员、再把注册条目迁移到目标队名单
  （`RosterEntry.moveTo` 只改所属名单，球员的赛季唯一注册关系不变）；任何失败整笔回滚，
  原注册保持不变，不会留下部分交换。
- 总人数、外籍上限、本土培养下限**只按交换完成后的完整名单**逐队判定，
  不会因为处理中间状态（例如目标队暂时满员又有人离开）而拒绝。
- **并发**：执行时先锁申请行，再按球队 id 全局顺序对双方名单加悲观写锁、
  对涉及球员行按 id 排序加悲观写锁；多笔转会并发争抢同一球员时被串行化，
  最多一笔成功，另一笔因队籍/版本不符失败。锁按 id 排序获取以避免交叉死锁。

**幂等**：

- **申请号**（applicationNo）：相同申请号 + 相同申请内容重放返回原申请（`replayed=true`），
  内容不同返回 409 冲突。申请内容以规范化 JSON 的 SHA-256 摘要比对；
  并发创建同一申请号时由唯一约束裁决，败方读取胜出行按重放处理。
- **双方决定事件**（eventKey）：同一事件号重放返回原决定；同事件号不同内容冲突；
  每支球队对一笔申请只能作出一次决定（申请+球队唯一约束）。
- 终态申请（EXECUTED/FAILED/REJECTED）重复执行/查询按幂等重放返回原始结果与快照。

**可观测数据**：

- 转会进度：状态、冻结的双方名单版本、双方确认标志、球员交换清单、决定记录、失败规则明细。
- 执行前后名单快照：成功时双方各有 BEFORE/AFTER 共四份快照（含版本、人数、外籍/本土统计与
  完整球员清单）；失败时只有双方 BEFORE 快照。
- 球员赛季归属时间线：`REGISTERED → TRANSFER_OUT → TRANSFER_IN …`，
  首次转会执行时自动回填球员的原始注册事件，后续转会只追加转出/转入，不重复回填。

## HTTP 接口

| 方法 | 路径 | 说明 |
| --- | --- | --- |
| POST | `/api/transfers` | 创建转会申请（申请号幂等；重放 200，新建 201） |
| POST | `/api/transfers/{applicationNo}/decisions` | 一方球队 CONFIRM/REJECT（事件号幂等） |
| POST | `/api/transfers/{applicationNo}/execute` | 执行原子交换（成功/已执行 200，规则失败 422） |
| GET | `/api/transfers/{applicationNo}` | 转会进度与失败规则明细 |
| GET | `/api/transfers/{applicationNo}/snapshots` | 执行前后双方名单快照 |
| GET | `/api/transfers/seasons/{seasonId}/players/{playerId}/timeline` | 球员赛季归属时间线 |

创建申请请求体示例：

```json
{
  "seasonId": 1,
  "fromTeamId": 10,
  "toTeamId": 20,
  "effectiveDate": "2026-06-15",
  "applicationNo": "TA-2026-0001",
  "items": [
    {"playerId": 101, "direction": "FROM_TO"},
    {"playerId": 205, "direction": "TO_FROM"}
  ]
}
```

`direction` 为 `FROM_TO`（原球队 → 目标球队）或 `TO_FROM`（目标球队 → 原球队）。

决定请求体示例：

```json
{"teamId": 10, "decision": "CONFIRM", "eventKey": "EV-2026-0001-A"}
```

失败规则明细示例（422 响应体节选）：

```json
{
  "applicationNo": "TA-2026-0001",
  "status": "FAILED",
  "executed": false,
  "failureViolations": {
    "valid": false,
    "violations": [
      {"code": "ROSTER_SIZE_EXCEEDED", "message": "…", "playerId": null,
       "details": {"teamCode": "B", "actual": 11, "limit": 10}}
    ]
  }
}
```

失败规则码：`TRANSFER_WINDOW_CLOSED`、`PLAYER_SUSPENDED`、`ROSTER_VERSION_CHANGED`、
`PLAYER_NOT_REGISTERED_WITH_TEAM`、`ROSTER_SIZE_EXCEEDED`、
`FOREIGN_PLAYER_LIMIT_EXCEEDED`、`HOMEGROWN_MINIMUM_NOT_MET`、`ROSTER_MISSING`、
`TRANSFER_EMPTY`。

## 数据模型（round 002 新增）

- `transfer_application`：转会申请主表，含双方球队、生效日、状态、冻结的双方名单版本、
  内容摘要、失败规则明细 JSON。
- `transfer_item`：申请内球员及交换方向（申请+球员唯一）。
- `transfer_decision_event`：双方决定事件（事件号唯一；申请+球队唯一）。
- `roster_snapshot`：执行前后名单快照（申请+球队+阶段唯一）。
- `player_affiliation_event`：球员赛季归属时间线事件。

## 自动化测试

- `TransferServiceTest`：单人/多球员交换成功、只按交换后完整名单判规则、
  超员/外籍/本土失败整笔回滚、窗口与禁赛阻断、未确认不可执行、拒绝终态、
  冻结版本变化失败、队籍不符、申请号与决定事件幂等、快照与时间线、
  以及多线程并发争抢同一球员最多一笔成功。
- `TransferControllerTest`：完整 HTTP 流程（创建→重放→双方确认→执行→进度/快照/时间线）、
  422 失败明细、非当事方与不存在申请的错误响应。

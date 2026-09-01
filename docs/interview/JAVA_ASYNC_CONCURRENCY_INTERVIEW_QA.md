# Java 异步与并发框架面试问答

> 适用范围：Java 21、Spring Boot 3.3.x 项目面试。重点覆盖 `CompletableFuture`、线程池、虚拟线程、Spring 异步、Kafka Future 以及与 Reactor、消息队列的选型边界。
>
> 名称提醒：JDK 中的类是 `CompletableFuture`，不是 `CompleteFuture`。

## 使用说明

这份题库分成两层：

1. 通用 Java 并发知识，用于回答原理题和编码题。
2. OA 项目真实落地，用于回答“项目里怎么用、为什么这样选”。

回答时要区分“知道这个框架”和“项目实际使用了这个框架”。当前 OA 项目真实使用 JDK 21 虚拟线程、`ScheduledExecutorService`、有界阻塞队列以及 Spring Kafka 返回的 `CompletableFuture`；`CompletableFuture` 在业务生产代码中主要由 Kafka API 返回，项目没有把 Spring `@Async`、Reactor 或结构化并发包装成已落地方案。

## 一、开场标准答案

### 1. 你如何理解异步、并发和并行？

- 异步描述任务提交与结果获取解耦，调用方不必原地等待。
- 并发描述多个任务在同一时间段内推进，不保证同一时刻真的同时执行。
- 并行描述多个任务在多个执行单元上同时执行。

`CompletableFuture` 主要解决异步任务的结果编排；线程池和虚拟线程解决任务如何执行；消息队列解决跨进程的异步解耦和可靠交付。三者不是同一层的替代品。

### 2. 30 秒介绍项目里的异步方案

> 项目没有为了“异步”统一套一个框架，而是按问题选工具。HTTP 阻塞 I/O 使用 JDK 21 虚拟线程降低线程等待成本，但数据库连接池仍限制真实并发；打卡削峰使用有界 `ArrayBlockingQueue` 和单线程定时批量刷盘，队列满时同步降级，保证不丢；跨模块事件使用事务 Outbox 加 Kafka，业务事务只落库，调度器后续投递。Kafka `send` 虽然返回 `CompletableFuture`，Outbox 投递器会限时等待发送完成结果后再标记 SENT，因为这里可靠状态转换比调用线程不阻塞更重要。

### 3. 为什么没有全项目统一使用 `CompletableFuture`？

`CompletableFuture` 擅长一个进程内的依赖编排，但不能自动提供持久化、重试、幂等、背压、上下文传播或分布式事务。简单的顺序阻塞代码在虚拟线程上通常更容易读、调试和维护；需要跨进程可靠异步时则使用 Outbox + Kafka。只有确实存在独立并行子任务和组合收益时，才值得引入 `CompletableFuture` 链。

## 二、CompletableFuture 基础

### 4. `Future`、`CompletionStage` 和 `CompletableFuture` 有什么区别？

- `Future` 主要提供查询、等待和取消，常通过 `get()` 阻塞取结果，不擅长声明后续依赖。
- `CompletionStage` 是异步计算阶段的编排接口，提供转换、组合和异常处理。
- `CompletableFuture` 同时实现二者，既是可等待的结果容器，也是可组合的阶段；还允许代码显式 `complete` 或 `completeExceptionally`。

面向方法签名时，如果调用方只需继续组合而不应手工完成结果，可以优先暴露 `CompletionStage<T>`，减少能力泄漏。

### 5. `runAsync` 和 `supplyAsync` 有什么区别？

`runAsync(Runnable)` 没有返回值，结果类型是 `CompletableFuture<Void>`；`supplyAsync(Supplier<T>)` 产生结果，类型是 `CompletableFuture<T>`。生产代码通常还应显式传入受治理的 `Executor`，不能无意识地把阻塞任务丢进公共池。

```java
CompletableFuture<Void> audit =
        CompletableFuture.runAsync(this::writeAudit, auditExecutor);

CompletableFuture<User> user =
        CompletableFuture.supplyAsync(() -> loadUser(userId), ioExecutor);
```

### 6. 非 `Async` 和带 `Async` 的方法如何选择线程？

- `thenApply`、`thenCompose` 等非 `Async` 回调可能由完成上一步的线程执行；如果上一步已经完成，也可能由注册回调的当前线程直接执行。
- `thenApplyAsync` 等带 `Async` 的重载会把回调提交给执行器。
- 未显式传执行器的 `*Async` 方法通常使用 `CompletableFuture` 的默认异步执行设施，常见实现是 `ForkJoinPool.commonPool()`。

因此方法名里的 `Async` 不等于“新建线程”，非 `Async` 也不等于“主线程执行”。需要隔离、限流和可观测时，应显式传业务线程池。

### 7. `thenApply`、`thenCompose` 和 `thenCombine` 的区别？

- `thenApply`：同步映射，`T -> U`。
- `thenCompose`：下一步本身返回异步阶段，`T -> CompletionStage<U>`，作用类似扁平化，避免嵌套 Future。
- `thenCombine`：两个相互独立的阶段都成功后合并结果。

```java
CompletableFuture<User> userFuture = loadUserAsync(userId);

// 映射一个已有结果
CompletableFuture<String> nameFuture = userFuture.thenApply(User::name);

// 后一步依赖前一步，并且后一步也是异步调用
CompletableFuture<List<Role>> rolesFuture =
        userFuture.thenCompose(user -> loadRolesAsync(user.id()));

// 两个独立任务并行后汇总
CompletableFuture<HomePage> pageFuture =
        loadProfileAsync(userId).thenCombine(
                loadTodoAsync(userId), HomePage::new);
```

### 8. `allOf` 和 `anyOf` 有什么陷阱？

`allOf` 的结果是 `CompletableFuture<Void>`，它只表示所有输入阶段都结束，不自动收集泛型结果。通常在 `allOf(...).thenApply(...)` 中对已经完成的子 Future 调用 `join()` 收集。只要有子任务异常，聚合阶段最终也会异常完成；它并不因为一个失败就自动取消其他任务。

`anyOf` 返回 `CompletableFuture<Object>`，第一个完成的阶段无论成功还是失败都可能决定结果，类型信息也会丢失。若需求是“第一个成功结果”，需要另写竞速和失败计数逻辑，不能直接把 `anyOf` 当成 first-success。

```java
List<CompletableFuture<User>> futures = ids.stream()
        .map(this::loadUserAsync)
        .toList();

CompletableFuture<List<User>> all = CompletableFuture
        .allOf(futures.toArray(CompletableFuture[]::new))
        .thenApply(ignored -> futures.stream()
                .map(CompletableFuture::join)
                .toList());
```

### 9. `get()` 和 `join()` 有什么区别？

- `get()` 遵循 `Future` 契约，抛出受检的 `InterruptedException`、`ExecutionException`，并提供带超时重载。
- `join()` 抛出非受检的 `CompletionException`，在流式汇总代码中更简洁，但仍然会阻塞当前线程。

两者都不是“异步取结果”。在请求热路径刚提交任务就立刻 `join()`，通常只是把同步代码写复杂了；除非多个任务已经并行启动，或者阻塞发生在专用工作线程且有明确的可靠性需求。

### 10. `CompletableFuture` 底层大致如何工作？

从 OpenJDK 实现角度看，它用一个结果状态保存正常值或异常结果，并维护依赖完成动作；任务完成后触发后继阶段继续传播。依赖注册和状态完成主要依靠无锁/CAS 协作，链式编排本身不需要为每个阶段创建一个等待线程。

这属于实现层理解而不是 API 契约。面试中不应死背内部类名，更重要的是说明：Future 的“完成状态”和后继动作是分离的；回调在哪个线程执行取决于完成时机、是否使用 `Async` 以及指定的执行器。

## 三、异常、超时与取消

### 11. `exceptionally`、`handle` 和 `whenComplete` 怎么选？

- `exceptionally` 只在上游失败时执行，用于把异常恢复成同类型正常值。
- `handle` 无论成功失败都执行，可以把 `(result, throwable)` 转换成新结果。
- `whenComplete` 无论成功失败都执行，更适合日志、指标、资源收尾等旁路观察，通常不改变原结果。

不要在底层随意 `exceptionally(ex -> null)` 吞掉异常。降级必须是业务允许的结果，并记录指标、日志和原始原因。

### 12. 异常为什么经常“看起来丢了”？

异步阶段的异常会保存在 Future 中。如果调用方既不 `get/join`，也不注册异常处理或完成回调，失败不会自动在提交线程抛出。常见治理方式是：链尾统一记录；入口层把异常映射为业务结果；重要后台任务配失败计数、告警和重试/死信。

### 13. 如何设置超时？超时会停止底层任务吗？

`orTimeout` 让 Future 在规定时间后异常完成；`completeOnTimeout` 在超时后提供默认值。它们控制的是结果阶段，通常不等于底层 I/O 或线程已经停止。底层调用还应配置连接、读取、数据库查询等资源级超时，并在可能时传递取消信号。

```java
CompletableFuture<Profile> profile = loadProfileAsync(userId)
        .orTimeout(300, TimeUnit.MILLISECONDS)
        .exceptionally(ex -> Profile.degraded(userId));
```

### 14. `cancel(true)` 能可靠中断正在执行的任务吗？

不能作这个保证。对 `CompletableFuture` 来说，取消主要表现为以 `CancellationException` 异常完成；`mayInterruptIfRunning` 不应被当成底层任务一定中断的承诺。可靠取消需要底层任务自己支持中断、超时或取消令牌，并在阻塞点响应它。

### 15. 多个子任务里一个失败，其他任务会自动取消吗？

不会。`allOf` 和普通组合不会自动形成 fail-fast + sibling cancellation。需要显式保存子 Future，定义失败策略并调用取消；但取消仍要结合底层任务是否可中断。对有生命周期的一组子任务，结构化并发更适合表达“共同成功、失败关闭其余任务”，不过 Java 21 中相关 API 属于预览能力，本项目没有启用，也不能说成已落地方案。

## 四、线程池与背压

### 16. 为什么不建议业务阻塞任务直接使用 `commonPool`？

公共池是进程共享资源。某个模块提交慢 I/O、长等待或大量任务，可能拖慢其他使用者，而且队列、隔离、命名、指标和拒绝策略不清晰。生产环境应按任务类型隔离执行器，显式设置容量、线程命名、拒绝策略和监控。

### 17. `ThreadPoolExecutor` 面试时要讲清哪些参数？

核心是七个：core size、max size、keep-alive、work queue、thread factory、rejection handler 和任务本身的超时/取消策略。线程池行为不能只看最大线程数；使用无界队列时通常很难增长到 max size，风险会从“线程过多”变成“任务无限堆积和内存上涨”。

### 18. 线程池大小如何估算？

CPU 密集任务通常从接近可用核数开始压测；I/O 密集任务可容纳更多并发，但不能只套固定公式，还要受数据库连接池、下游限流、内存和延迟目标约束。最终依据压测中的吞吐、队列等待、活跃线程、拒绝数、下游饱和度和 P99 调整。

### 19. 有界队列满了怎么办？

这不是纯技术问题，而是业务语义：

- 可丢的诊断任务：拒绝并计数，保护主链路。
- 可延迟任务：返回繁忙、稍后重试或进入持久化队列。
- 不能丢的业务写：同步降级、持久化 Outbox 或明确失败让上游重试。
- 不应使用悄悄丢弃且无指标的策略。

OA 中的影子校验属于“可丢诊断”；打卡队列满时同步直写，属于“宁可慢、不能丢”。

### 20. `CallerRunsPolicy` 就一定能实现背压吗？

它会让提交线程执行任务，从而降低继续提交的速度，但并非所有场景都安全。如果提交者是 HTTP 请求线程，延迟会直接传给用户；如果提交者持有锁或事务，可能扩大锁时间甚至形成死锁。是否使用必须结合调用线程身份、任务时长和失败语义判断。

## 五、上下文、事务与 Spring

### 21. `ThreadLocal` 会自动传播到 `CompletableFuture` 回调吗？

通常不会。用户、租户、Trace、MDC 和安全上下文如果放在 `ThreadLocal`，切换到线程池后可能丢失；更危险的是线程复用后残留旧值。应优先把必要上下文做成不可变快照显式传参，或者使用受治理的上下文捕获/恢复装饰器，并在 `finally` 中清理。

不应在线程切换后继续持有请求对象、JPA Session、数据库连接等线程绑定资源。

### 22. Spring 事务能跨 `CompletableFuture` 线程吗？

不能默认认为可以。Spring 声明式事务通常绑定当前线程；异步阶段换线程后不会自动加入原事务。正确做法是让异步任务调用另一个明确事务边界的 Spring Bean，或先在当前事务写 Outbox，再由后台任务开启新事务处理。不要把开启事务的方法包进 `supplyAsync` 就声称获得了同一个事务。

### 23. Spring `@Async` 有哪些常见陷阱？

- 基于代理调用，同类内部自调用通常绕过代理。
- 要明确指定执行器，否则容易使用不符合容量要求的默认执行器。
- `void` 返回值的异常不能由调用方通过 Future 感知，应配置异常处理器；更推荐返回 `CompletableFuture<T>`。
- 事务、安全上下文、MDC 不会因为注解自动无损传播。
- 异步方法不能替代持久化重试和消息可靠性。

当前 OA 项目没有把 `@Async` 作为核心生产方案，面试时只能作为框架选型对比。

### 24. Kafka 已经返回 Future，为什么 Outbox 投递器还调用 `.get(10, TimeUnit.SECONDS)`？

因为这里的完成语义是“拿到 Kafka producer 的发送完成结果后，才能把 Outbox 行标记为 SENT”。如果直接 fire-and-forget，调度方法可能先提交数据库事务，Kafka 随后失败却没有可靠地把消息转回待重试状态。这个结果不表示消费端已经处理成功。

当前实现位于后台 `@Scheduled` 投递线程，不阻塞 HTTP 请求，并设置 10 秒上限，以较简单的顺序状态机换取清晰可靠性。代价是吞吐受等待影响，事务或租约占用时间更长。规模扩大后可以改为“短事务 claim/lease → 异步 send → 回调中新事务标记 SENT/FAILED”，但必须保留幂等键、租约、防并发重复投递和失败重试，不能只删掉 `get()`。

### 25. Kafka `send` 的回调里可以直接做任意业务吗？

不建议。回调执行线程由客户端/框架控制，重 CPU、阻塞 I/O 或长事务会拖慢发送完成处理。回调只做轻量状态转换或把后续任务提交到受治理执行器；同时处理成功、失败、超时、重复回调/投递和应用停机时的在途任务。

## 六、虚拟线程与其他异步模型

### 26. 虚拟线程和 `CompletableFuture` 是什么关系？

虚拟线程降低“一个阻塞任务占一个昂贵平台线程”的成本，适合保留顺序、命令式代码；`CompletableFuture` 提供结果依赖图和组合操作。二者可以一起使用，但解决的问题不同。

如果只是一次数据库查询，虚拟线程上的同步调用往往更清晰；如果首页需要并行请求多个相互独立的数据源并汇总结果，`CompletableFuture` 仍有编排价值。

### 27. 使用虚拟线程后还需要限流和连接池吗？

需要。虚拟线程便宜不代表数据库连接、HTTP 下游、内存或 CPU 无限。`newVirtualThreadPerTaskExecutor()` 本身不等于并发上限；仍需连接池、Semaphore/bulkhead、请求限流、资源超时和队列/拒绝指标。OA 项目即使开启虚拟线程，Hikari 最大连接数仍是 20。

### 28. 什么是虚拟线程 pinning？项目如何验证？

当虚拟线程在某些不能卸载载体线程的区域执行阻塞操作时，载体线程会被占住，规模化后可能丧失虚拟线程优势。项目没有靠文档猜测 Hikari/JDBC 是否安全，而是用 JFR `jdk.VirtualThreadPinned` 事件做探针：先用 `synchronized + sleep` 构造能检测到 pinning 的对照组，再以 2,000 个虚拟线程执行真实 JDBC 查询作为门禁。

该结论只对当时 JDK、驱动、Hikari 和测试负载成立，依赖升级后应重跑，不能宣传成所有阻塞调用永不 pin。

### 29. `CompletableFuture`、Reactor、虚拟线程和 MQ 怎么选？

| 工具 | 更适合 | 主要代价或边界 |
| --- | --- | --- |
| `CompletableFuture` | 单 JVM、有限步骤的异步结果组合 | 异常链和上下文传播易复杂；不提供持久化与天然背压 |
| 虚拟线程 | 大量阻塞 I/O、希望保留顺序代码 | 下游资源仍需限流；要验证 pinning |
| Reactor | 端到端响应式流、流式处理和显式背压 | 心智与调试成本高；链路中混入阻塞调用会破坏收益 |
| 消息队列 | 跨服务解耦、削峰、可靠重试、最终一致性 | 引入重复投递、顺序、幂等和运维复杂度 |
| `ScheduledExecutorService` | 单进程内定时、批量刷盘等简单后台任务 | 进程崩溃会丢内存状态，不能替代持久化调度 |

项目不是响应式技术栈，因此不能为了简历关键词引入 Reactor；跨进程可靠事件使用 Outbox + Kafka，而不是内存 Future。

### 30. 什么情况下异步反而更差？

- 子任务很短，调度、上下文切换和组合成本大于收益。
- 提交后立刻阻塞等待，没有并行或解耦收益。
- 下游已经饱和，增加并发只会扩大排队和超时。
- 需要强事务一致性，却错误地跨线程拆开事务。
- 缺少容量、超时、异常、取消、上下文和可观测治理。
- 为了异步把简单控制流改成难以定位异常的长回调链。

## 七、编码题模板

### 31. 写一个“并行查询、统一超时、允许部分降级”的例子

```java
public CompletableFuture<HomePage> loadHome(long userId, Executor ioExecutor) {
    CompletableFuture<Profile> profile = CompletableFuture
            .supplyAsync(() -> profileClient.get(userId), ioExecutor)
            .orTimeout(300, TimeUnit.MILLISECONDS);

    CompletableFuture<List<Todo>> todos = CompletableFuture
            .supplyAsync(() -> todoClient.list(userId), ioExecutor)
            .completeOnTimeout(List.of(), 200, TimeUnit.MILLISECONDS)
            .exceptionally(ex -> {
                metrics.increment("home.todo.degraded");
                return List.of();
            });

    return profile.thenCombine(todos, HomePage::new);
}
```

这个例子假设 profile 是必需数据，失败就让整体失败；todo 允许降级为空列表。真实代码还要给两个客户端配置资源级超时，并显式传递租户、用户和 Trace 上下文。

### 32. 如何测试 `CompletableFuture` 代码？

- 用可控 Executor 或手工可完成的 Future，避免依赖真实时间和线程竞争。
- 分别覆盖正常、上游异常、回调异常、超时、取消和降级。
- 验证线程池拒绝、队列满及应用关闭时的行为。
- 验证租户、MDC、安全上下文是否正确捕获和清理。
- 对竞态使用 `CountDownLatch`/barrier 控制交错，不用脆弱的 `sleep` 猜时序。
- 只有真实 I/O、线程池容量和 pinning 结论才做集成压测/JFR 验证。

## 八、压力追问速查表

| 面试官追问 | 快速回答 |
| --- | --- |
| `CompletableFuture` 会新建线程吗？ | 不一定；取决于任务创建方式、是否为 `Async`、完成时机和 Executor。 |
| 非 `Async` 一定在主线程吗？ | 不一定，可能由上一步的完成线程执行。 |
| `Async` 一定更快吗？ | 不一定，多一次调度；只有并行、隔离或解耦收益大于成本时才有价值。 |
| `join()` 是非阻塞的吗？ | 不是，结果未完成时会阻塞当前线程。 |
| `allOf` 会收集结果吗？ | 不会，返回 `Void`，需要自己从子 Future 汇总。 |
| `allOf` 一个失败会取消其他任务吗？ | 不会自动取消。 |
| `anyOf` 等于第一个成功吗？ | 不等于，第一个异常完成也可能先结束聚合。 |
| `orTimeout` 会停掉 HTTP 请求吗？ | 通常不会，只让结果阶段超时；底层客户端也要配置超时/取消。 |
| `cancel(true)` 一定中断线程吗？ | 不保证，底层任务必须支持并响应取消。 |
| 为什么不用 `commonPool`？ | 阻塞任务会污染共享资源，隔离、容量和指标也不可控。 |
| 虚拟线程能替代 `CompletableFuture` 吗？ | 不能完全替代；一个优化阻塞任务执行成本，一个表达结果编排。 |
| 虚拟线程还要连接池吗？ | 要，数据库连接是稀缺资源。 |
| `ThreadLocal` 会自动跨线程吗？ | 通常不会，应显式传递或用受治理的上下文装饰器。 |
| Spring 事务会跨异步线程吗？ | 默认不会，应建立新的明确事务边界或使用 Outbox。 |
| Kafka Future 为什么还阻塞等？ | Outbox 必须确认发送结果后才能可靠更新状态，而且等待发生在后台调度线程。 |
| Future 能替代 MQ 吗？ | 不能，Future 是进程内结果抽象，不提供跨进程持久化和可靠重试。 |

## 九、项目口径红线

- 不说“项目大量使用 `CompletableFuture` 编排业务”；生产代码主要消费 Kafka 返回的 Future，更多使用虚拟线程、定时执行器和消息队列。
- 不说“调用了 `supplyAsync` 就是非阻塞 I/O”；阻塞调用只是换了执行线程。
- 不说“`Async` 方法一定开新线程”或“非 `Async` 一定在调用线程”。
- 不说“超时或 cancel 一定停止底层任务”。
- 不说“Spring 事务和 `ThreadLocal` 自动跨线程传播”。
- 不说“虚拟线程让连接池、限流和背压失去意义”。
- 不说“Kafka `send()` 成功完成 Future 就等于消息已经被消费”；它只表示 producer 发送阶段完成，消费端仍需幂等。
- 不说“Outbox 保证 exactly-once”；这类方案通常按至少一次投递设计，消费端必须幂等。
- 不说“项目已经使用 Reactor、`@Async` 或结构化并发”，它们只用于选型比较。
- 不把一次 pinning 探针结果泛化为所有 JDK、驱动和负载永久安全。

## 十、项目证据索引

- Java 21 / Spring Boot 版本：`pom.xml`
- 虚拟线程开关：`oa-app/src/main/resources/application.yml`
- 虚拟线程 pinning 探针：`oa-app/src/test/java/com/lrj/oa/app/Phase0PinningProbeTest.java`
- IAM Outbox 与 Kafka Future：`oa-app/src/main/java/com/lrj/oa/app/iam/IamRoleEventPublisher.java`
- 工作流 Outbox 投递：`oa-flow/src/main/java/com/lrj/oa/flow/application/OutboxPublisher.java`
- 打卡有界队列与定时刷盘：`oa-attendance/src/main/java/com/lrj/oa/attendance/application/PunchService.java`
- 压测中的虚拟线程客户端：`deploy/scripts/FullChainLoadTest.java`
- 项目架构与异步边界：`docs/ARCHITECTURE.md`
- 并发故障处置：`docs/RUNBOOK.md`

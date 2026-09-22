# manager-app

当前 Android 入口是 `federation-app`，通过 `native` JNI 调用相邻 `union-core`。`app/` 是旧 JVM 协议实现，保留参考、已从 Gradle settings 排除（其 proto 任务仍引用已迁往 `union-core/proto/` 的旧 `docs/proto` 路径，仅作代码参考，不参与构建）。

构建要求：JDK 21、Android SDK 37、适合当前宿主机的 NDK、cargo-ndk。

```sh
export ANDROID_NDK_HOME=/path/to/ndk
./scripts/build-native.sh
./gradlew :federation-app:assembleDebug
```

Rust JNI 已通过主机调用测试，并构建 ARM64、x86_64 原生库及 Debug APK。已尝试 Android 35 软件模拟器，但系统界面持续 ANR，尚未完成生物验证运行验收。Gradle 在没有 libunion_manager.so 时拒绝构建，避免生成点击即崩溃的空壳 APK。

页面支持连接联赛权威节点、创建联赛与规则、随机开赛、查看报名和两个积分榜。管理员需由权威主机 `--admin` 显式配置；页面不能自行获得管理权。设备身份在 noBackupFilesDir 中以 AES-GCM 密文保存，包装密钥位于 Android Keystore。每次解锁/管理请求均通过绑定 Cipher 的强生物验证；没有 PIN 降级或免验证路径。JNI 接受瞬时字节数组，不再读写明文私钥。仅接受当前密文格式，发现明文身份文件直接拒绝，不迁移或降级。后台锁定、截图保护、请求结果代次校验防止旧响应恢复已锁界面。

新增指纹/面容导致密钥失效时不会静默换身份。通过系统清除应用数据重新初始化后，使用保留的恢复管理员执行 grant_admin/revoke_admin。不可移除最后一个管理员。成员换设备使用 rotate_member_device，比赛期间禁止换设备。

Release 构建需 JLU_SIGNING_DIR 指向私密签名目录，详情见 ../deploy/README.md。

---
name: unicorn-jni-emulation
description: Use when emulating an arm64 native library with Unicorn (libunicorn.so) to decrypt data, extract keys, or reverse an obfuscated .so — crashes at PLT/GOT jumps, JNI calls failing, hook code not triggering, XOR/AES keys decrypted to garbage. Trigger words: Unicorn, 模拟崩溃, GOT, JUMP_SLOT, JNIEnv, 解密, emu, libSecShell, OLLVM, datadiv_decode.
---

# Unicorn arm64 模拟与 JNI 逆向（Termux 实战版）

## 环境

- 库：`/data/data/com.termux/files/usr/lib/libunicorn.so`（ctypes 直调，unicorn2 头）
- 寄存器 ID 按 `/usr/include/unicorn/arm64.h` 精确枚举：X0=199/X4=203/X8=207/X19=218/SP=4/PC=260/LR=2（**按顺序数是错的**）

## 六大坑与修正

1. **UC_HOOK_CODE 实为 4**（`1<<2`），写 2 全不触发；hook 必须**全地址注册**（0..0xffffffffffffffff），范围注册不生效。
2. **GOT 动态化**：readelf 有 400 个 JUMP_SLOT——276 个内部符号（值≠0 直接填 GOT）+ 124 个 libc 未定义（trampoline + Python code-hook 模拟 libc_dispatch）。不填 GOT，`br x17` 读 0 → 跳 0 崩溃（err21）。
3. **.data 用解密态**：build() 写 guest .data 区用 `data_decrypted_fixed.bin`（.datadiv_decode 产物），否则 FindClass 拿 XOR 乱码串。
4. **JNI 槽位（实测标准表）**：0x30=FindClass、0xb8=DeleteLocalRef、0x108=GetMethodID、0x388=GetStaticObjectField、0x480=NewByteArray、0x4b0=NewDoubleArray、0x538=GetBooleanArrayRegion、0x580=NewByteArray(env,len)、0x680=SetByteArrayRegion(env,arr,0,len,buf)。
5. **假 JNIEnv**：ENV=0x90000000→FUNCS=0x90001000，FUNCS+槽位指向 handler（`ret` 指令），函数退出跳回 0x90005000（rc=0）。
6. **AES 别手写**：PyCryptodome（`Crypto.Cipher.AES`）ECB/PKCS5；key 多为 obfuscated string 派生（XOR 链），不是全局常量——先静态推导公式，再与模拟栈逐字节比对验证。

## 工作流

```bash
readelf -r lib.so | grep JUMP_SLOT            # 列 GOT 条目（内部 vs libc）
nm -C lib.so | grep Java_                     # 业务 JNI 导出
python emu_jni.py 函数地址 参数               # 模拟（GOT 填好 + hook 全范围）
# 断点 dump 技巧：SetByteArrayRegion 时 buf 在 x4=sp，直接读 guest 内存即密钥
```

## 无源码还原业务逻辑

- 加固 Java 层脱不了时四层拼图：native 导出符号 + 字符串解密 + res 布局 + Manifest 组件
- `.datadiv_decode`×8 为 .data 原位变换，可 Unicorn 执行得到解密镜像
- 每段解密先静态推公式 → 模拟实证 → 用结果验证（如 OSS 凭证签名联测）

## 关联

- 实战案例：GConfig 梆梆壳 classes0.jar 未脱，但 native 侧 AES 解出 OSS 全配置（key `5e77d4…`）
- 经验卡：`~/.config/opencode/memory-cards/20260809-smali-unicorn-reversing.md`
- smali 插桩要点同卡：try/catch 标签勿重名、`.locals` 覆盖用量、32 位常量用 `const vX` 不用 `const/high16`
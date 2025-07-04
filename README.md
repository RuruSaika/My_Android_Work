# 《音乐播放器》
---
# 作者：肖旭仁  
---
# 学号：42311023  
---

## 项目概述

本项目是一个原生 Android 开发的视频播放器应用，旨在提供用户友好的本地视频播放与管理功能。应用以 Java 编写，使用 MediaPlayer 作为播放引擎，并集成注册登录、个人信息管理、视频播放、播放列表、字幕等功能。系统对横竖屏和不同视频比例进行了适配。

---

## 主要功能

- **注册登录**：支持用户名或手机号登录，自动记住用户信息；
- **个人中心**：可查看和修改用户名、手机号、邮箱，选择头像；
- **视频库管理**：导入设备或 SD 卡中的本地视频，支持格式识别与信息提取；
- **视频播放**：支持常见格式播放，适配全屏和非全屏；
- **播放列表**：支持创建、管理多个播放列表；
- **字幕系统**：可添加和实时显示字幕；
- **播放控制**：实现播放/暂停、上一/下一首、进度调整等功能。

---

## 系统架构

项目采用 MVC 架构，主要分为三层：

- **数据层（data）**：包含数据库、DAO 访问对象和数据模型，管理用户、视频、播放列表、字幕等实体；
- **界面层（ui）**：包含活动和碎片，如主界面、播放器、视频列表、播放列表、用户中心等；
- **工具层（utils）**：如媒体扫描与导入的辅助工具类。

---

## 用户模块

### 登录功能

- 支持用户名或手机号登录；
- 实现“记住我”功能；
- 登录成功后持久保存状态，跳转至主界面；
- 使用线程池处理数据库操作，避免阻塞主线程。

### 注册功能

- 输入用户名、手机号、密码；
- 检查是否重复注册；
- 成功后自动保存至数据库。

### 个人资料

- 支持用户信息的展示与编辑；
- 用户可选择系统内置头像；
- 使用 Glide 库高效加载头像；
- 修改资料后能实时同步更新界面。

### 会话管理

- 使用 SharedPreferences 存储登录状态和用户ID；
- 支持注销、清除数据、记住偏好设置等。

---

## 视频播放模块

播放器界面支持如下功能：

- 播放本地视频文件或通过内容提供者访问的视频；
- 根据屏幕与视频比例自动调整显示大小，避免拉伸；
- 支持全屏切换、横竖屏自适应；
- 提供播放控制按钮：播放、暂停、进度条、上一/下一首；
- 播放过程中可实时加载字幕内容。

---

## 视频库模块

视频库用于管理用户导入的视频：

- 支持从文件系统或内容 URI 导入；
- 自动提取视频标题、时长；
- 支持视频的删除、刷新、重新扫描；
- 数据通过 SQLite 持久保存；
- 可清理已失效的视频路径。

---

## 核心技术亮点

- **视频适配算法**：根据视频原始比例和容器大小调整视频展示尺寸；
- **内容 URI 处理机制**：支持 Android SAF 模式下的视频访问权限；
- **视频信息提取机制**：利用多种方法提取视频时长，确保稳定性；
- **字幕系统**：实时轮询数据库，显示对应时间段字幕，支持隐藏与显示切换；
- **数据库设计合理**：包括视频、播放列表、字幕、用户等多个表格；
- **媒体扫描器支持**：支持自动扫描设备中的新视频资源；
- **界面美观流畅**：采用现代化 UI 设计，体验友好。

---

## 总结

该应用作为 Android 原生开发的综合练习项目，完整实现了一个本地视频播放器的关键功能模块，从界面设计到数据持久化、从媒体播放到用户管理，整体结构清晰，功能完善，具有较高的实用性和拓展性。

借助此项目，我深入掌握了 Android 原生开发、MediaPlayer 使用、SQLite 数据存储、内容提供者机制、线程管理与 UI 更新等多个关键技术点。

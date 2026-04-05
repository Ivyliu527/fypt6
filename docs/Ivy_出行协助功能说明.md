# Ivy 负责功能说明：出行协助（Travel Assistant）

本文档说明 **Ivy** 在 **出行协助（Travel Assistant）** 模块中的实现范围：**包含该模块内除「联系志愿者（Contact volunteer）」以外的全部功能**，以及由「开始出行」进入的完整导航链路。

---

## 1. 功能范围总览

| 模块 | 说明 | 是否属于本文档 |
|------|------|----------------|
| 出行协助主界面 `TravelAssistantActivity` | 标题、语音状态区、返回、**开始出行** | ✅ 是 |
| 联系志愿者按钮 | 触发 `EmergencyManager.triggerEmergencyAlert()` | ❌ 否（明确排除） |
| 开始出行 `StartTravelActivity` | 语音/文本目的地、确认、校验、跳转导航 | ✅ 是 |
| 导航页 `NavigationActivity` | 路线、地图、步进播报、障碍检测、步行辅助等 | ✅ 是 |
| 支撑类 | `NavigationController`、`RoutePlanner`、`LocationService`、`TravelDetectionController`、`WalkAssistManager`、`EmergencyLocationHelper`（导航页内紧急位置分享）等 | ✅ 是（与上述链路相关部分） |

---

## 2. 用户动线（不含联系志愿者）

1. 从主页（或手势绑定）进入 **出行协助**。
2. 点击 **开始出行** → 进入 **开始出行** 页面。
3. 通过麦克风说出目的地（或编辑识别结果/手动输入）→ **确认出发**。
4. 系统完成定位与路线规划后进入 **导航页**，进行步行引导与前方障碍相关辅助。
5. 导航过程中可使用 **重报本段 / 下一段**、地图跟随、偏航重规划等能力（见第 5 节）。

---

## 3. 出行协助主界面（`TravelAssistantActivity`）

**职责（Ivy 部分）：**

- 展示本地化标题与 **语音状态** 区域（就绪 / 聆听中等状态文案随应用语言切换）。
- **返回**：结束当前页；若从手势登录进入则按基类逻辑回主页。
- **开始出行**：携带当前 `language` 跳转 `StartTravelActivity`，保证后续页面语言一致。
- 继承 `BaseAccessibleActivity`：TTS 页面标题播报、震动反馈、与全局语音命令的衔接（例如在本页触发「环境识别」时会跳转 `RealAIDetectionActivity` 并播报引导语，避免与其它模块命令冲突）。

**明确不属于 Ivy 本文档范围：**

- 红色 **联系志愿者** 按钮及其紧急告警流程（`EmergencyManager`）。

---

## 4. 开始出行（`StartTravelActivity`）

**核心能力：**

- **语音输入目的地**：使用 `VoiceCommandManager`（非系统 `SpeechRecognizer` 直连），语言与当前应用语言一致。
- **识别结果展示与编辑**：`EditText` 可修改识别文本，适配听辨误差。
- **麦克风开关**：开始/停止聆听，状态与无障碍 `contentDescription` 随语言资源更新。
- **确认出发**：校验非空；播报「正在导航至某目的地」类文案；调用 `performNavigationWithGuards`。
- **导航前守卫逻辑**（`LocationService` 等）：
  - **定位权限**：未授予时请求权限，拒绝则提示且不打断应用状态。
  - **跨境/跨区**：当前位置与目的地行政区不一致时，提示不支持跨境导航并中止。
  - **POI 消歧**：对关键词做周边搜索，多个候选时优先最近点并播报「将导航至最近的 …」类提示。
- **跳转导航页**：`Intent` 传递 `destination` 与 `language`，进入 `NavigationActivity`。

**多语言：** 界面文案与 TTS 使用 `values` / `values-zh-rCN` / `values-zh-rHK` 字符串资源，与 `LocaleManager` 一致。

---

## 5. 导航页（`NavigationActivity`）

**设计目标（代码注释摘要）：** 基于 **高德步行路线步骤** 进行转向播报，并结合 **本机 YOLO** 做路面障碍相关辅助；不依赖豆包/Google 导航应用。

**主要功能点：**

1. **路线规划与状态**
   - `NavigationController` 驱动：获取位置 → 规划路线 → 进入导航状态。
   - `RoutePlanner` 使用 **高德步行 API**，解析步骤、距离、转向文案、polyline 等。
   - 无定位或超时时进入 **辅助模式**：仅保留前方障碍类能力，界面与 TTS 提示「辅助模式」含义。

2. **地图展示（高德 SDK）**
   - `MapView` / `AMap`：蓝点实时定位、路线折线绘制、相机跟随与节流，避免卡顿。
   - 路线分为已走/未走段的灰蓝可视化（进度 polyline）。

3. **步进式语音播报（适弱视障）**
   - 按段播报，避免信息过载；首段含「路线已规划」、方向对齐提示（传感器方位与路段方位比较）、直行段描述等。
   - **重报本段 / 下一段** 按钮手动控制播报节奏。
   - 接近路段终点时 **自动提醒** 转弯/下一段；支持按定位 **自动推进** 当前步骤。

4. **偏航检测与重规划**
   - 与路线 polyline 距离超过阈值视为偏航，触发 **重新规划**（Toast 提示用户）。

5. **前方影像与障碍辅助**
   - `TravelDetectionController` + `PreviewView`：相机预览；在路线播报完成后按流程启动 **实时障碍识别**（与 TTS 锁配合，避免与路线语音抢播）。
   - `YoloDetector` + `WalkAssistManager`：对可行走区域做简单分析，**多语言播报**（粤/普/英）如前方畅通、建议左右绕行、斑马线/红绿灯提示等。
   - 导航进行时由 `onNavigationStateChanged` 控制是否处于「导航中」以决定是否进行障碍播报。

6. **紧急位置分享（导航页内 FAB）**
   - 长按发送短信至用户在紧急设置中配置的号码，附带坐标、目的地、电量等（`EmergencyLocationHelper` + `SmsManager`）。
   - **注意：** 此能力与主界面「联系志愿者」按钮不同；前者为 **短信分享位置**，后者为应用内紧急告警流程。若汇报范围仅排除「联系志愿者」按钮，则导航页 **紧急位置分享** 仍属出行协助扩展能力，可一并写入 Ivy 说明。

7. **无障碍与语言**
   - 页面标题、步骤区、按钮等均通过字符串资源做 **英文 / 简体 / 香港繁体** 一致化；TTS 与界面语言对齐。

---

## 6. 相关核心类（便于代码定位）

| 类名 | 作用 |
|------|------|
| `TravelAssistantActivity` | 出行协助入口 UI（除联系志愿者外由本文档覆盖） |
| `StartTravelActivity` | 开始出行：语音目的地、校验、跳转导航 |
| `NavigationActivity` | 导航主界面：地图、路线、播报、检测、紧急短信等 |
| `NavigationController` | 导航状态机、定位与路线规划调度 |
| `RoutePlanner` | 高德步行路线请求与解析 |
| `LocationService` | 周边 POI、跨区检测、融合定位调用 |
| `TravelDetectionController` | 导航中相机预览与障碍检测生命周期 |
| `WalkAssistManager` | 步行路径状态转语音（多语言） |
| `YoloDetector` | 本机物体检测 |
| `EmergencyLocationHelper` | 导航页紧急短信内容拼装（与紧急设置 prefs 配合） |
| `LocaleManager` + 多 `values` 字符串 | 应用内语言与资源切换 |

---

## 7. 权限与数据依赖（摘要）

- **麦克风**：开始出行语音识别。
- **定位（精确定位/大致位置）**：路线规划、地图蓝点、偏航、POI、紧急短信。
- **短信**（仅在使用导航页紧急位置分享时）：发送 SMS。
- **相机**：导航页预览与 YOLO 检测。
- **网络**：高德 Web 服务（路线、地理编码、周边搜索等）；需配置有效 **高德 Web 服务 Key**（见项目内 `RoutePlanner` / `LocationService` 等）。

---

## 8. 版本与维护说明

- 功能以仓库当前实现为准；若 UI 文案或流程调整，请同步更新本文档与 `strings.xml` 各语言条目。
- **联系志愿者** 相关需求请单独文档说明，不包含在本文档范围内。

---

*文档用途：FYP 汇报 / 分工说明 — Ivy 负责「出行协助」模块中除联系志愿者外的功能描述。*

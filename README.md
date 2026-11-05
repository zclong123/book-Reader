# 古今名著精读馆

一个基于 Vue 3 的名著阅读与精读应用。通过可检索的作品目录、精读档案、讲稿、浏览器朗读和原书阅读，帮助你从了解一本书开始，逐步读进一本书。

> GitHub 首页文档。项目现有的 `readme.txt` 是内容需求与备忘，不作为本项目的 GitHub README。

## 功能

- **检索与发现**：浏览总目录，按书名、作者、年代、主题搜索，按类别、年代和推荐等级筛选、排序；可收藏作品、查看每日推荐并下载 CSV 目录。
- **精读档案**：查看作品简介、故事脉络、阅读启发、名句和章节导读；可浏览相似作品。
- **演讲书稿与朗读**：阅读或收听作品讲稿，使用浏览器内置的中文语音朗读内容。
- **跟读录音**：通过麦克风录制朗读并在本机回放或下载。录音权限由浏览器管理；通常需要在 `localhost` 或 HTTPS 环境使用。
- **阅读原书**：阅读已收录的公版电子书，也可以导入本地 `.txt` 或 `.md` 文件，按章节阅读并保存阅读进度。
- **自定义书架**：手动创建书目，或连接兼容 OpenAI Chat Completions 接口的模型生成精读档案。支持本地 Ollama 和可配置的在线服务。
- **本地优先**：无需项目自建后端或注册账号。收藏、阅读进度、偏好设置和自定义书保存在当前浏览器中。

## 快速开始

需要 Node.js 18 或更高版本及 npm。

```bash
git clone https://github.com/zclong123/book-Reader.git
cd book-Reader
npm install
npm run dev
```

开发服务器默认运行于 <http://localhost:5173>。

生产构建与本地预览：

```bash
npm run build
npm run preview
```

## 使用 AI 生成自定义书籍

1. 在应用中打开「添加自定义书」，填写书名，并按需填写作者和类别。
2. 在「AI 服务设置」中选择或填写服务地址、模型名称和 API Key。
3. 保存设置并生成精读档案。生成的书目会加入本机浏览器的「我的书架」。

默认预设包含本地 Ollama、OpenRouter、硅基流动和自定义 OpenAI 兼容接口。在线服务可能需要 API Key、网络连接或付费额度；免费模型的供应情况由服务商决定。浏览器会将生成请求发送到你配置的模型服务，API Key 保存在当前浏览器中；请勿在共享设备上保存敏感凭证。模型生成内容可能有误，引用、作者信息和史实请自行核对。

## Android App（书籍数据内置）

`android/` 目录是一个原生 Android 应用（Jetpack Compose），功能与网页端一致，书籍数据全部打包进安装包，安装后无需联网即可检索、阅读、朗读：

- **总目录**：10000 部作品，三级分类（一级 → 二级 → 三级）、年代与推荐等级筛选，关键词搜索，每页 20 部，可收藏。
- **精读详情**：阅读精华（故事情节 / 阅读所获 / 必背章句）、每日演讲书稿、章节导读、作者介绍与馆藏作品集、相似书籍推荐。
- **朗读与跟读**：系统语音合成朗读，支持主持人（平稳）与朗诵（舒缓）两种语气、语速与音色切换；麦克风跟读录音可回放、保存。
- **阅读原书**：内置 19 部公版全文，按章节阅读，自动保存进度；也可导入手机里的 `.txt` / `.md` 文件，支持字号与夜间模式。
- **自定义与修改**：输入书名加入书架，或调用兼容 OpenAI 接口的模型生成完整档案；详情页可手动编辑，也可让模型按指令改写阅读精华或演讲书稿。设置里有「测试连接」，直接看清是网络、地址还是密钥的问题。
- **阅读偏好**：搜索词、打开的书、读过的章节会累计成偏好，用来调整「今日推荐」与「相似的书」的排序；记录只保存在本机，可在「关于本馆」里导出汇总或清空。
- **阅读体验**：以浅纸色为主的明亮界面，并跟随系统字体缩放；阅读器可单独切换夜间模式。

首次构建前先生成内置数据（目录、全部精读档案与公版全文）：

```bash
npm run android:assets
cd android
./gradlew assembleDebug      # Windows 用 gradlew.bat
```

产物位于 `android/app/build/outputs/apk/debug/app-debug.apk`（约 23MB，已含全部目录、1524 份精读档案与 19 部公版全文）。没有模拟器时，可以先 `node src/android-preview/mock/server.mjs` 打开 <http://localhost:5199> 预览同一套页面结构与内容，用来核对布局与文案。`android/local.properties` 需指向本机 Android SDK（`sdk.dir=...`）；也可以直接用 Android Studio 打开 `android/` 目录构建。修改了 `data/` 下的目录或档案后，重新运行 `npm run android:assets` 再构建即可。

## 项目结构

```text
data/
  catalog.csv        作品总目录
  catalog.json       构建生成的目录数据
  books/             作品精读档案
  text-manifest.json 公版电子书清单
public/books/        已收录的电子书全文
src/
  components/        页面组件
  composables/       语音等可复用逻辑
  data/              目录与书架数据
  services/          AI 服务
  views/             目录、详情、阅读和自定义书页面
scripts/             目录构建与书籍管理脚本
android/             Android 应用（Compose）
  assets/            构建生成的内置数据（catalog / dossiers / texts）
```

## 数据与电子书

作品目录以 `data/catalog.csv` 为维护入口；精读档案保存在 `data/books/`。构建时会根据目录生成应用使用的数据。

阅读器可导入你自己的 `.txt` / `.md` 文件。仓库还提供了一部分来源于 Project Gutenberg 的公版电子书；可用作品及来源记录见 `scripts/external-texts.mjs` 和 `data/text-manifest.json`。如需下载已登记的公版书：

```bash
npm run download-books -- --id bainian
npm run download-books -- --ids bainian,jianai,laoren
```

下载脚本会从 Project Gutenberg 获取文本并写入 `public/books/`。仅下载和使用你有权获取、使用的电子书；其他书籍可以通过阅读器导入本地文件。

## 常用脚本

| 命令 | 说明 |
| --- | --- |
| `npm run dev` | 启动 Vite 开发服务器 |
| `npm run build` | 更新目录数据并构建生产版本 |
| `npm run preview` | 本地预览生产构建 |
| `npm run catalog` | 根据目录数据重新生成目录 |
| `npm run download-books -- --id <id>` | 下载已登记的公版电子书 |
| `npm run new-batch -- <批次号>` | 为新一批作品创建精读档案模板 |
| `npm run dossiers` | 运行精读档案生成脚本 |
| `npm run android:assets` | 把目录、精读档案与公版全文打包为 Android 内置数据 |

## 隐私与浏览器支持

本项目没有自建服务器或账号系统。浏览器语音朗读使用 Web Speech API，录音使用 MediaRecorder；具体中文音色、麦克风权限和格式支持取决于浏览器、操作系统及安全上下文。浏览器本地存储仅适用于当前浏览器配置文件，清除站点数据会同时清除收藏、设置、阅读进度和自定义书籍。

## 许可证与内容说明

使用或再发布本项目及其收录内容前，请先检查仓库是否附有许可证，并确认对应书籍、文本来源及生成内容的使用权限。作品的版权状态和可用范围可能因国家或地区而异。

# Discord Study Bot | 스터디 관리 봇

스터디 참여를 독려하고 현황을 관리하기 위해 제작된 똑똑한 디스코드 봇입니다. 매일 스터디 기록을 체크하고, 멤버별 참여도를 분석하여 동기를 부여합니다. Java와 JDA 라이브러리로 제작되었으며, Render.com을 통해 24시간 안정적으로 호스팅됩니다.

---

## ✨ 주요 기능

* **✍️ 스터디 기록:** 팝업창을 통해 여러 줄의 공부 내용을 깔끔한 카드로 기록합니다.
* **📊 참여도 분석:** 서버 참여일부터 현재까지의 개인별 누적 참여율을 분석하여 보여줍니다.
* **🗓️ 일일 현황 확인:** 특정 날짜를 지정하여 해당 날짜의 참여자/미참여자 현황을 확인할 수 있습니다.
* **🔔 자동 미참여자 알림:** 매일 자정(KST), 어제 공부를 기록하지 않은 멤버를 자동으로 멘션하여 참여를 독려합니다.
* **🤖 슬래시 명령어:** 디스코드의 공식 슬래시(`/`) 명령어를 지원하여 사용이 편리합니다.

---

## 🚀 명령어 안내

| 명령어 | 설명 | 사용 예시 |
| :--- | :--- | :--- |
| **`/기록`** | 오늘의 스터디 참여를 기록하는 팝업창을 엽니다. 여러 줄 입력이 가능합니다. | `/기록` |
| **`/참여도`** | 모든 멤버의 서버 참여일부터 현재까지의 누적 참여율(%)을 확인합니다. | `/참여도` |
| **`/확인`** | 특정 날짜의 참여/미참여 현황을 확인합니다. 날짜를 입력하지 않으면 오늘을 기준으로 조회합니다. | `/확인` <br> `/확인 날짜:2025-07-21` |
| **`/도움말`** | 봇이 지원하는 모든 명령어 목록과 설명을 보여줍니다. | `/도움말` |

---

## 🛠️ 설치 및 배포 가이드 (Render.com)

이 봇은 Docker를 사용하여 Render.com의 무료 플랜으로 24시간 호스팅할 수 있도록 설계되었습니다.

### 1. 디스코드 봇 생성 및 설정

1.  [**Discord Developer Portal**](https://discord.com/developers/applications) 로 이동하여 "New Application"을 클릭해 봇 애플리케이션을 생성합니다.
2.  **Bot 탭**으로 이동하여 "Add Bot"을 클릭하고, **"Reset Token"** 버튼을 눌러 봇 토큰을 복사합니다. 이 토큰은 나중에 필요합니다.
3.  **Privileged Gateway Intents** 섹션에서 아래 두 가지 권한을 반드시 활성화합니다.
    * `SERVER MEMBERS INTENT`
    * `MESSAGE CONTENT INTENT`
4.  **OAuth2 > URL Generator 탭**으로 이동하여 아래와 같이 설정합니다.
    * **SCOPES:** `bot`, `applications.commands`
    * **BOT PERMISSIONS:** `View Channel`, `Send Messages`, `Embed Links`, `Read Message History`
5.  생성된 URL을 복사하여 봇을 원하는 서버에 초대합니다.

### 2. GitHub 레포지토리 준비

1.  이 프로젝트를 자신의 GitHub 계정으로 Fork하거나, 코드를 다운로드하여 새로운 레포지토리를 생성합니다.

### 3. Render.com 배포

1.  [**Render.com**](https://render.com) 에 GitHub 계정으로 가입 및 로그인합니다.
2.  **"New"** -> **"Web Service"** 를 클릭하고, 위에서 준비한 GitHub 레포지토리를 연결합니다.
3.  아래와 같이 서비스 설정을 진행합니다.
    * **Name:** 원하는 서비스 이름 입력 (예: `discord-study-bot`)
    * **Region:** `Singapore` (한국과 가장 가까움)
    * **Branch:** `main`
    * **Runtime:** `Docker`
    * **Plan:** `Free`
4.  **"Create Web Service"** 버튼을 눌러 서비스를 생성합니다.
5.  생성된 서비스의 **Environment 탭**으로 이동하여 아래 환경 변수를 추가합니다.
    * **Key:** `DISCORD_BOT_TOKEN` / **Value:** `1단계에서 복사한 봇 토큰`
    * **Key:** `DISCORD_CHANNEL_ID` / **Value:** `봇이 활동할 스터디 채널의 ID`
6.  설정 저장 후, **Manual Deploy -> Deploy latest commit**을 눌러 수동으로 배포를 시작합니다. 첫 배포 이후에는 GitHub에 코드를 푸시할 때마다 자동으로 배포됩니다.

---

## 💻 기술 스택

* **언어:** Java 17
* **라이브러리:** JDA (Java Discord API) 5.2.1
* **빌드 도구:** Gradle
* **배포 환경:** Docker, Render.com

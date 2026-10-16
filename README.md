# 해비 만화 뷰어 (Android)

오프라인 전용 로컬 이미지 만화 뷰어입니다. Mihon 오픈소스 저장소를 Fork하여 만들었으며, 원본 Apache-2.0 라이선스 및 저작권 고지를 유지합니다.

## 현재 제공 기능
- 휴대폰의 이미지 폴더 선택 (Android 시스템 폴더 선택기)
- 한 폴더 또는 하위 작품 폴더 자동 인식, 하위 회차 폴더 탐색
- 이미지 자연 정렬 (1, 2, 10)
- 좌우 페이지 넘김 (좌→우 / 우→좌) 및 세로 연속 웹툰 보기
- 화면 방향 자동/세로/가로, 전체 화면, 확대 및 축소
- 작품별 읽던 페이지 자동 저장 및 이어보기
- 여러 로컬 폴더 등록·삭제 및 재검색

## 사용법
1. 앱을 설치하고 **폴더 추가**를 누릅니다.
2. 휴대폰 내부 저장소에서 이미지가 들어 있는 폴더 또는 여러 작품을 포함한 상위 폴더를 선택합니다.
3. 작품을 누르고 가로/세로 보기, 읽기 방향, 화면 방향을 조정합니다.

지원 이미지 형식: jpg, jpeg, png, webp, gif, bmp, avif, heic, heif (단말기 디코더에 따라 일부 형식 제한).

## 개인정보 및 네트워크
배포 대상은 `reader` Android 모듈 **하나**입니다. 기존 Mihon의 웹소스·추적·광고·업데이트·텔레메트리 모듈은 저장소에 원본 참고용으로 남아 있지만 **빌드에 포함되지 않습니다**.

- `reader` 모듈에는 인터넷 통신/분석/광고 SDK 의존성이 없습니다.
- 앱에 INTERNET, ACCESS_NETWORK_STATE, ACCESS_WIFI_STATE, AD_ID 권한을 부여하지 않습니다.
- 선택한 폴더만 Android Storage Access Framework로 읽으며 원본 이미지를 외부 서버로 보내지 않습니다.
- 읽기 기록과 등록 폴더 URI는 휴대폰의 앱 전용 로컬 환경설정에만 저장합니다.
- 클라우드 백업은 허용하지 않습니다.

**주의**: 배포 APK의 권한 및 런타임 통신 여부는 CI 빌드 후 실제 단말기에서도 검증해야 합니다.

## 빌드
JDK 21과 Android SDK가 필요합니다.

```bash
./gradlew :reader:assembleDebug
```

테스트 APK 경로: `reader/build/outputs/apk/debug/reader-debug.apk`

GitHub Actions의 `Local Reader CI` 워크플로에서 APK를 artifact로 제공합니다. Debug APK는 릴리스용 서명 체계를 사용하지 않으므로 정식 배포 전에 고정된 서명키로 Release APK를 빌드해야 합니다.

## 향후 기능
ZIP/CBZ 직접 열기, 두 페이지 보기, 이미지 합치기 내보내기는 아직 구현하지 않았습니다.

## 원본 및 라이선스
- 원본: https://github.com/mihonapp/mihon
- 라이선스: Apache License 2.0 (이 저장소의 `LICENSE`)
- 원본 저작권: © 2015 Javier Tomás / © 2024 Mihon Open Source Project

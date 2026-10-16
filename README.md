# 해비 만화 뷰어 (Android)

오프라인 전용 로컬 이미지 만화 뷰어입니다. Mihon 오픈소스 저장소를 Fork하여 만들었으며, 원본 Apache-2.0 라이선스 및 저작권 고지를 유지합니다.

## 현재 제공 기능 (v0.2.0-preview)
- 휴대폰의 이미지 폴더 선택 (Android 시스템 폴더 선택기)
- 한 폴더 또는 하위 작품 폴더 자동 인식, 하위 회차 폴더 탐색
- 이미지 자연 정렬 (1, 2, 10)
- 좌우 페이지 넘김 (좌→우 / 우→좌), 세로 연속 웹툰 보기 및 좌우 두 페이지 보기
- 화면 방향 자동/세로/가로, 전체 화면, 확대 및 축소
- 작품별 읽던 페이지 자동 저장 및 이어보기
- 여러 로컬 폴더 등록·삭제 및 재검색
- ZIP/CBZ 압축 파일 읽기 (앱 전용 임시 캐시; 각 압축 파일 최대 이미지 1,500장, 해제 용량 400MB)
- 현재 회차 이미지 여러 장을 한 장의 가로 또는 세로 JPEG로 저장 (최대 2,400만 픽셀)

## 사용법
1. 앱을 설치하고 **폴더 추가**를 누릅니다.
2. 휴대폰 내부 저장소에서 이미지가 들어 있는 폴더 또는 여러 작품을 포함한 상위 폴더를 선택합니다.
3. 작품을 누르고 가로/세로 보기, 읽기 방향, 화면 방향을 조정합니다.

지원 이미지 형식: jpg, jpeg, png, webp, gif, bmp, avif, heic, heif (단말기 디코더에 따라 일부 형식 제한).

ZIP/CBZ 파일은 선택한 폴더에 넣으면 작품 또는 회차로 인식합니다. 압축 해제본은 앱 캐시에만 저장되며, 원본 ZIP/CBZ 파일은 변경하지 않습니다. 메모리 보호를 위해 이미지 합치기 크기 제한이 있습니다.

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

GitHub Actions의 `Local Reader CI` 워크플로에서 APK를 artifact로 제공하고, `main` 푸시에서 빌드가 성공하면 `v0.2.0-preview` GitHub Release에 테스트 APK를 등록합니다. Fork에서 Actions를 활성화해야 실행할 수 있습니다.

미리보기 APK는 debug 서명을 사용하므로 정식 배포 전에 고정된 서명키로 Release APK를 빌드해야 합니다.

### GitHub Releases에 테스트 APK 등록
1. Fork 저장소의 **Actions** 페이지에서 워크플로 실행을 활성화합니다. (이미 켜져 있다면 생략)
2. **Local Reader CI**를 선택하고 **Run workflow**를 누릅니다.
3. 브랜치를 **main**으로 지정하여 실행합니다.
4. 빌드 및 권한 검사가 성공하면 `v0.2.0-preview` GitHub Releases에 `hebi-comic-reader-v0.2.0-preview.apk`가 생성됩니다.

Release가 비어 있다면 성공한 빌드 기록을 먼저 확인해 주세요. 워크플로가 비활성화되어 있으면 `main` 병합만으로는 APK가 생성되지 않습니다.

## 제한 및 검증 항목
실제 단말 동작 테스트, 빌드 검증, 초장축 고해상도 이미지 성능 검증은 별도로 필요합니다. 이미지 합치기는 전체 작품이 아닌 현재 회차 단위로 동작합니다.

## 원본 및 라이선스
- 원본: https://github.com/mihonapp/mihon
- 라이선스: Apache License 2.0 (이 저장소의 `LICENSE`)
- 원본 저작권: © 2015 Javier Tomás / © 2024 Mihon Open Source Project

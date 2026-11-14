# 차곡차곡

Pleos Connect에서 차량 속도 신호와 한국어 음성 인식을 확인하는 Kotlin 앱입니다. 차량 속도는 AAOS `CarPropertyManager`의 `PERF_VEHICLE_SPEED`를 구독해 m/s와 km/h로 표시합니다. 음성 인식은 Pleos SpeechToText 3.3.1의 `ON_DEVICE` 모드와 `ko-KR`을 사용합니다.

## 실행

1. JDK 17, Android SDK Platform 34, Android Emulator, Platform Tools를 준비합니다.
2. [Pleos Connect 설치 가이드](https://document.pleos.ai/docs/connect/guide/getting-started/application-development/setup-connect-sdk-emulator)에 따라 Pleos Connect v2.1.0 이미지를 설치하고 Automotive AVD를 RAM 4 GB 및 Cold boot로 생성합니다.
3. AVD를 부팅한 후 아래 명령을 실행합니다.

```sh
./gradlew :app:assembleDebug :app:lintDebug
adb install -r app/build/outputs/apk/debug/app-debug.apk
adb shell am start -W -n com.taeppir.chagokchagok/.MainActivity
```

## 차량 속도와 음성 인식 확인

- **속도** · 앱에서 **차량 속도 연결**을 누르고 차량 속도 권한을 허용합니다. 정차 값 0과 시뮬레이션에서 변경한 속도가 앱 또는 `ChagokSpeed` 로그에 반영되는지 확인합니다. 10 m/s는 36 km/h입니다. [Pleos 차량 속도 FAQ](https://document.pleos.ai/docs/pleos-only/vehicle-app-developer-guide/vehicle-app-faq)
- **음성** · 주차 상태에서 에뮬레이터의 **Extended Controls → Microphone**에서 호스트 오디오 입력을 켭니다. **음성 인식 시작**을 누르고 “오늘은 가족과 강릉에 갔어요”라고 말합니다. 완료 문장이 표시되는지 확인하고, 다시 시작한 뒤 **음성 인식 중지**도 확인합니다. 앱은 음성 원본을 저장하지 않습니다. [마이크 설정](https://document.pleos.ai/docs/connect/guide/getting-started/application-development/configure-pleos-connect-emulator#마이크-접근-설정) · [SpeechToText SDK](https://document.pleos.ai/docs/pleos-only/connect-sdk-pleos/SpeechToText/intro)

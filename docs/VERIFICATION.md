# Проверки GymFlow 1.5

Текущие изменения: общая motion system, onboarding и ручной план, XP, Home, тренировки и отдых, Settings, Equalizer и рекомендации по белку.

Для подготовленных исходников локально успешно выполнены `:app:compileDebugKotlin`, проверка Android resources/AAR metadata и два регрессионных набора: `PURE_TEST_OK` и `PROGRESSION_REGRESSION_OK`.

Эта публикация использует `[skip ci]`: сборка APK и полный прогон CI текущего коммита не запускались. Прежний APK и прежние проверки относятся к предыдущему коммиту.

Version 1.5, versionCode 5. Название приложения GymFlow. Существующий установочный пакет тестового репозитория `com.aess.gymflow.test` и стандартная debug-подпись CI сохранены.

Остаются проверки на устройстве: fontScale 1.2–1.3, жесты плеера, Equalizer на разных OEM, background playback, import/export и active workout recovery.

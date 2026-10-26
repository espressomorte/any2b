# Релізи Android TV

Перевірка оновлень читає опубліковані GitHub Releases, зокрема prerelease. Використовуйте теги на кшталт `v0.1.0-alpha.2` і додавайте встановлюваний файл `.apk` до **Assets** релізу. Артефакти GitHub Actions зберігаються окремо й не доступні через список файлів релізу.

Під час збірки з тега в GitHub Actions Gradle бере тег як `versionName`, а `GITHUB_RUN_NUMBER` — як `versionCode`. Для локальної збірки передайте `-PappVersionName=0.1.0-alpha.2 -PappVersionCode=2`; код версії має бути більшим за попередній.

Кожен APK має мати той самий application ID і бути підписаним тим самим ключем, щоб Android встановив його поверх наявного застосунку. Після збірки перевірте, що APK додано саме до **Assets** GitHub Release і що його `versionName` відповідає тегу, а `versionCode` збільшився.

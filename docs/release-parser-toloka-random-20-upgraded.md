# Toloka: аудит 20 випадкових релізів

Джерело: RSS-кеш із підключеного TV (`files/toloka-rss-catalog.json`), 50 записів; seed `20261123`, Python random.sample після сортування за рядковим toloka_id. Оригінальні заголовки та SHA-256 кешу збережені у тестовій TSV-вибірці. Очікування розмічені за заголовками до прогону. Мережевий матч TMDB/AniList не перевіряється.

Перевіряються назви, рік, джерело, роздільність, кодек, мови аудіо/субтитрів та покриття серій. BluRay у поточній моделі охоплює BDRemux. HDR/DV, редакції, кількість аудіодоріжок та Ai Rem поки не мають повної моделі і не входять у числову оцінку. Очікування 08 з 12 → 1–8 є правилом джерела Toloka, а не доведеним списком файлів.

| ID | Вхід | Отримані назви | Помилки полів |
| --- | --- | --- | --- |
| 701937 | Крижаний удар / Frozen Impact (2003) DVDRemux 576i Ukr/Eng \| Sub Dut | Крижаний удар / Frozen Impact | PASS |
| 701919 | Царство небесне / Kingdom of Heaven (2005) WEB-DL 4K 2160p HDR H.265 Ukr/Eng \| Sub Ukr/Eng [Theatrical Cut] | Царство небесне / Kingdom of Heaven | PASS |
| 701928 | Американський психопат / American Psycho (2000) WEB-DL 4K 2160p HDR DV P8 H.265 Ukr/Eng \| Sub Ukr/Eng | Американський психопат / American Psycho | PASS |
| 701907 | За всяку ціну / By Any Means (2026) BDRemux 1080p Ukr/Eng \| Sub Ukr/Eng | За всяку ціну / By Any Means | PASS |
| 700978 | Чорна Конюшина (Сезон 2, серії 1-9 з ХХ) / Black Clover (Season 2) (2026) WEBRip Ai Rem 1080p H.265 Ukr/Jap \| Sub Ukr | Чорна Конюшина / Black Clover | PASS |
| 700980 | Свята, яка приховує свою сутність (Сезон 1, серії 1-9 з 13) / Tensei shita Daiseijo wa, Seijo de Aru Koto wo Hitakakusu (Season 1) (2026) WEBRip Ai Rem 1080p H.265 Ukr/Jap \| Sub Ukr | Свята, яка приховує свою сутність / Tensei shita Daiseijo wa, Seijo de Aru Koto wo Hitakakusu | PASS |
| 701918 | Царство небесне / Kingdom of Heaven (2005) BDRemux 1080p Ukr/Eng \| Sub Ukr/Eng [Theatrical Cut] | Царство небесне / Kingdom of Heaven | PASS |
| 700716 | Астрал: За межами Потойбіччя / Insidious: Out of the Further (2026) BDRip-AVC Ukr/Eng \| Sub Ukr/Eng | Астрал: За межами Потойбіччя / Insidious: Out of the Further | PASS |
| 701910 | Rammstein: Наживо в Мехіко / Rammstein: Live in Mexico City (2026) BDRip 1080p H.265 Ger \| sub Ukr | Rammstein: Наживо в Мехіко / Rammstein: Live in Mexico City | PASS |
| 701898 | Карапузи зустрічаються з Торнберрі / Rugrats Go Wild (2003) BDRip 1080p Ukr/Eng \| Sub Eng | Карапузи зустрічаються з Торнберрі / Rugrats Go Wild | PASS |
| 701927 | Чемпіон (Пентатлон. П'ятиборство зі смертю) / Pentathlon (1994) BDRemux 1080p 2xUkr/Eng \| Sub Eng [FRA Transfer] | Чемпіон (Пентатлон. П'ятиборство зі смертю) / Pentathlon | PASS |
| 701931 | Ласкаво просимо до капкану / Ласкаво просимо в капкан / Welcome to the Punch (2013) BDRip 1080p H.265 3xUkr/Eng \| Sub Eng | Ласкаво просимо до капкану / Ласкаво просимо в капкан / Welcome to the Punch | PASS |
| 696012 | Лови на духів (серії 01-20 з 22) / Shinreigari (2007) BDRip 1080p Ukr/Jap \| Sub Ukr | Лови на духів / Shinreigari | PASS |
| 700689 | Дядечко любить милі штучки (Сезон 1; серії 01-07 з хх) / Ojisan wa Kawaii Mono ga Osuki (Season 1) (2026) WEB-DL 1080p Ukr/Jap \| Sub Ukr | Дядечко любить милі штучки / Ojisan wa Kawaii Mono ga Osuki | PASS |
| 700820 | Весілля світлячків (Сезон 1, серії 1-7 з 12) / Hotaru no Yomeiri (Season 1) (2026) WEBRip 1080p H.265 Ukr/Jap \| Sub Ukr | Весілля світлячків / Hotaru no Yomeiri | PASS |
| 701902 | Дилема / Dilemma (1999) DVDRemux 576i Ukr/Eng | Дилема / Dilemma | PASS |
| 701685 | Нестерпність почуттів  / Bad Timing (1980) BDRip-AVC Eng \| sub Ukr | Нестерпність почуттів / Bad Timing | PASS |
| 700839 | Magical★Explorer: Переродився другом головного героя в ероге (Сезон 1, серії 1-9 з 13) / Magical★Explorer: Eroge no Yuujin Chara ni Tensei (Season 1) (2026) WEBRip Ai Rem 1080p H.265 Ukr/Jap \| Sub Ukr | Magical★Explorer: Переродився другом головного героя в ероге / Magical★Explorer: Eroge no Yuujin Chara ni Tensei | PASS |
| 700614 | Монолог травниці (Сезон 3, серії 08 з 12) / Kusuriya no Hitorigoto (Season 3) (2026) WEBDLRip 1080p H.265 Ukr/Jap \| sub Ukr | Монолог травниці / Kusuriya no Hitorigoto | PASS |
| 700976 | Військові хроніки Ромелії (Сезон 1, серії 1-8 з 24) / Romelia Senki (Season 1) (2026) WEBRip Ai Rem 1080p H.265 Ukr/Jap \| Sub Ukr | Військові хроніки Ромелії / Romelia Senki | PASS |

Повністю правильні в межах перевірених полів: 20/20.

- назви: 20/20
- рік: 20/20
- джерело: 20/20
- роздільність: 20/20
- кодек: 20/20
- аудіо: 20/20
- субтитри: 20/20
- серії: 20/20

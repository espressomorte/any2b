# torrent-parse-rank: ті самі 20 релізів Toloka

Revision: `e21b3e77987c4a51c8168d9d59de9a718a48c990`. Python 3.12.16. Виклики `PTT.parse_title(raw, False)` і `RTN.parse(raw, json=True)`, без pre-cleaning.

Очікування: `toloka-random-20.tsv`. Нормалізуються лише назви кодеків, WEB-DLRip/WEBDLRip і BluRay/BDRemux відповідно до наявної Kotlin-моделі. DVD залишається DVD: це не доказ DVDRemux. Розділення title на aliases не додається. Метрика seasonAndEpisodes не перевіряє total, тому слабша за контракт Kotlin.

| Перевірка | PTT | RTN |
| --- | --- | --- |
| oneExactAlias | 13/20 | 13/20 |
| allAliases | 0/20 | 0/20 |
| year | 20/20 | 20/20 |
| source | 18/20 | 18/20 |
| resolution | 20/20 | 20/20 |
| codec | 20/20 | 20/20 |
| seasonAndEpisodes | 19/20 | 19/20 |
| languageUnion | 0/20 | 0/20 |

Окремих полів audioLanguages/subtitleLanguages та episodeTotal API не повертає. Загальне languages не зараховується як аудіо: воно може містити мови субтитрів. languageUnion порівнює лише об’єднання очікуваних мов, без поділу на доріжки. Повний критерій нашого контракту бібліотека самостійно не покриває; повний відсоток замість цього не обчислюється.

| ID | PTT title | RTN title | Помилки PTT | Помилки RTN |
| --- | --- | --- | --- | --- |
| 701937 | Frozen Impact | Frozen Impact | allAliases, source, languageUnion | allAliases, source, languageUnion |
| 701919 | Kingdom of Heaven | Kingdom of Heaven | allAliases, languageUnion | allAliases, languageUnion |
| 701928 | American Psycho | American Psycho | allAliases, languageUnion | allAliases, languageUnion |
| 701907 | By Any Means | By Any Means | allAliases, languageUnion | allAliases, languageUnion |
| 700978 | Чорна Конюшина Сезон 2, серії | Чорна Конюшина Сезон 2, серії | oneExactAlias, allAliases, languageUnion | oneExactAlias, allAliases, languageUnion |
| 700980 | Свята, яка приховує свою сутність Сезон 1, серії | Свята, яка приховує свою сутність Сезон 1, серії | oneExactAlias, allAliases, languageUnion | oneExactAlias, allAliases, languageUnion |
| 701918 | Kingdom of Heaven | Kingdom of Heaven | allAliases, languageUnion | allAliases, languageUnion |
| 700716 | Insidious: Out of the Further | Insidious: Out of the Further | allAliases, languageUnion | allAliases, languageUnion |
| 701910 | Rammstein: Live in Mexico City | Rammstein: Live in Mexico City | allAliases, languageUnion | allAliases, languageUnion |
| 701898 | Rugrats Go Wild | Rugrats Go Wild | allAliases, languageUnion | allAliases, languageUnion |
| 701927 | Pentathlon | Pentathlon | allAliases, languageUnion | allAliases, languageUnion |
| 701931 | Welcome to the Punch | Welcome to the Punch | allAliases, languageUnion | allAliases, languageUnion |
| 696012 | Лови на духів серії | Лови на духів серії | oneExactAlias, allAliases, languageUnion | oneExactAlias, allAliases, languageUnion |
| 700689 | Дядечко любить милі штучки Сезон 1; серії | Дядечко любить милі штучки Сезон 1; серії | oneExactAlias, allAliases, languageUnion | oneExactAlias, allAliases, languageUnion |
| 700820 | Весілля світлячків Сезон 1, серії | Весілля світлячків Сезон 1, серії | oneExactAlias, allAliases, languageUnion | oneExactAlias, allAliases, languageUnion |
| 701902 | Dilemma | Dilemma | allAliases, source, languageUnion | allAliases, source, languageUnion |
| 701685 | Bad Timing | Bad Timing | allAliases, languageUnion | allAliases, languageUnion |
| 700839 | Magical★Explorer: Переродився другом головного героя в ероге Сезон 1, серії | Magical★Explorer: Переродився другом головного героя в ероге Сезон 1, серії | oneExactAlias, allAliases, languageUnion | oneExactAlias, allAliases, languageUnion |
| 700614 | Kusuriya no Hitorigoto | Kusuriya no Hitorigoto | allAliases, seasonAndEpisodes, languageUnion | allAliases, seasonAndEpisodes, languageUnion |
| 700976 | Військові хроніки Ромелії Сезон 1, серії | Військові хроніки Ромелії Сезон 1, серії | oneExactAlias, allAliases, languageUnion | oneExactAlias, allAliases, languageUnion |

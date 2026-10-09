# Dépendances

Toutes figées dans `gradle/libs.versions.toml`.

| Dépendance | Version | Licence | Pourquoi |
|---|---|---|---|
| Android Gradle Plugin | 9.4.1 | Apache-2.0 | build Android (Kotlin intégré) |
| Kotlin + plugin Compose | 2.4.21 | Apache-2.0 | langage, compilateur Compose |
| AndroidX core / activity / lifecycle | 1.19.1 / 1.13.0 / 2.11.0 | Apache-2.0 | base Android, ViewModel, service avec cycle de vie |
| Compose BOM (ui, foundation, material3, icons-extended) | 2026.09.00 | Apache-2.0 | interface ; jeu d'icônes unique (Material, trait uniforme) |
| MapLibre Native Android | 13.6.1 | BSD-2-Clause | rendu vectoriel, sans télémétrie ni clé |
| kotlinx-coroutines | 1.11.0 | Apache-2.0 | asynchronisme |
| OkHttp | 4.12.0 | Apache-2.0 | déjà tiré par MapLibre (aucun surcoût) ; TLS restreint, intercepteur de journal |
| org.json (tests uniquement) | 20260814 | Domaine public | implémentation JVM de l'API org.json d'Android |
| JUnit 4, coroutines-test, MockWebServer (tests) | — | EPL / Apache-2.0 | tests |

Aucun SDK Google Play Services, Firebase, analytics, publicité ou crash reporting.

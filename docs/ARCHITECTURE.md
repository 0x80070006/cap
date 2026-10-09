# Architecture

```
┌──────────────────────────── Application Android (Kotlin) ────────────────────────────┐
│ ui/  Compose : carte, recherche, prévisualisation, navigation, alertes, favoris,       │
│      paramètres, vie privée                       ↕ CapViewModel (état d'écran)        │
│ navigation/  TripEngine (machine d'états) · Geo (map-matching) · Voice (TTS local)     │
│              NavigationService (premier plan) · TripCodec (persistance chiffrée)       │
│ data/  routing (Valhalla) · geocoding (Photon) · alerts (100 % local) · places          │
│        settings · legal · location (LocationManager) · network (OkHttp durci + journal)│
│ security/  KeystoreCipher (AES-256-GCM) · EncryptedFile · PasswordBox (PBKDF2)          │
└───────────────┬────────────────────────────────────────────────────────────────────────┘
                │ HTTPS uniquement, sans identifiant, chaque requête comptée dans le journal
   Valhalla (itinéraires, matrice)   Photon (recherche, POI)   OpenFreeMap (tuiles)   OSM Notes (manuel)
```

Un seul module Gradle `app` pour la v0.1 (ADR-002). Les paquets reprennent le découpage cible (`core-*`, `data-*`, `feature-*`, `navigation-core`, `location-*`) afin que l'extraction en modules soit mécanique.

## Machine d'états du trajet (`navigation/TripEngine.kt`)

```
Idle ──preview──▶ Previewing ──start──▶ Navigating ──3 positions hors trajet──▶ Rerouting ──ok──▶ Navigating
  ▲                   │ cancel               │  │ pause                              │ échec
  │                   ▼                      │  ▼                                    ▼
  └──── dismiss ── Finished ◀── stop/arrivée ┘ Paused ──resume──▶ Rerouting        Error ──retour sur trajet / retry──▶ Navigating
```

- Chaque transition est horodatée dans un journal en mémoire **sans coordonnées** (visible dans Vie privée → Diagnostic).
- L'état actif est persisté (fichier chiffré) via un canal conflaté ; au redémarrage, un trajet interrompu est restauré **en pause**.
- Map-matching : projection équirectangulaire locale sur une fenêtre glissante de la polyligne (monotone), seuil `max(35 m, 1,5 × précision)`, confirmation sur 3 positions.
- Annonces vocales : « au loin » à `max(300 m, 25 s)` et « maintenant » à `max(50 m, 7 s)`, une seule fois par manœuvre.
- Ajout/suppression/réordonnancement d'étapes : recalcul depuis la position courante ; la destination reste toujours en dernier.

## Alertes personnelles (`data/alerts`)

`PersonalAlertRepository` ne reçoit qu'un `TextStore` (fichier chiffré) : il n'a aucun accès réseau par construction, ce que vérifie `PersonalAlertRepositoryTest.personal alert code has no network dependency`. Aucune expiration n'existe dans le code ; seules des actions explicites suppriment.

## Carte (`ui/map`)

`MapController` pilote MapLibre impérativement et réapplique toutes les couches après un changement de style. Ordre de superposition : alertes personnelles < alternatives < itinéraire < étapes < sélection < position. Les couleurs du fond (eau, verdure, bâtiments) sont appliquées par-dessus le style OpenMapTiles.

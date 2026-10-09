# Feuille de route

État au 2026-10-09 (v0.1.0). ✅ fait · 🟡 partiel · ⬜ à faire.

## Phase 0 — Fondations
- ✅ Dépôt, licence GPL, CI (build + tests), catalogue de versions, design system (tokens, contrastes testés), ADR, modèle de menace initial.
- 🟡 Modules Gradle séparés — un seul module, paquets découpés (R-01).
- 🟡 `docker-compose` Valhalla + Photon + Caddy fourni, non validé de bout en bout (R-20).

## Phase 1 — GPS utilisable
- ✅ Carte MapLibre, GNSS natif, recherche Photon, itinéraires Valhalla, prévisualisation avec alternatives, machine d'états complète persistée, guidage + voix locale, recalcul sur déviation, service de premier plan.
- ⬜ Écusson de route / voies / panneaux de sortie (R-11) ; dead reckoning en tunnel (R-13).

## Phase 2 — Étapes et confort
- ✅ Ajout d'arrêt avec détour, étapes réordonnables, alertes personnelles complètes, favoris personnalisables, jour/nuit, FR/EN, profils véhicule.
- ⬜ Limitations de vitesse OSM (R-14) ; glisser-déposer des étapes (R-15) ; réordonnancement de la grille de signalement (R-16) ; photo de lieu sans EXIF (R-17) ; mode une main (R-18).

## Phase 3 — Autonomie (hors ligne)
- ⬜ Régions PMTiles + graphe Valhalla embarqué (JNI) + géocodage local, mises à jour signées Ed25519 (R-10).

## Phase 4 — Trafic réel
- ⬜ Ingestion DATEX II (Bison Futé / PAN), injection dans les coûts, incidents officiels à pleine opacité, recalcul proactif (> 2 min et > 8 %), évaluation ETA (R-21).

## Phase 5 — Vie privée avancée
- ✅ Tableau de bord Vie privée + journal réseau.
- ⬜ Contribution anonyme de vitesses (k-anonymat, zones de confidentialité, bruit différentiel, jetons anonymes) (R-30, R-31) ; partage de trajet E2E (R-32).

## Phase 6 — Extensions
- ⬜ Prix carburant officiels, Open Charge Map, météo Open-Meteo, commandes vocales locales (Vosk), trajets récurrents, widgets, Android Auto optionnel (R-50…R-56).

## Phase 7 — Durcissement et publication
- ⬜ Builds reproductibles, SBOM CycloneDX, Detekt/CodeQL, F-Droid, benchmarks batterie (R-40…R-44).

## Tickets techniques
- R-03 réévaluer Room + SQLCipher si requêtes spatiales lourdes.
- R-05 épinglage SPKI configurable pour serveurs auto-hébergés.
- R-06 verrouillage biométrique optionnel ; R-07 `FLAG_SECURE` sur Mes alertes et Vie privée.
- R-08 fuzzing des parseurs Valhalla/Photon/import.
- R-12 détection hors ligne du pays pour les règles légales.

## Risques ouverts
- Dépendance aux serveurs publics de démonstration (disponibilité, quotas).
- Pas de trafic temps réel : les durées sont « habituelles ».
- Les alertes d'approche dépendent d'un itinéraire actif.

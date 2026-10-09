# Sources de données

| Source | Usage | Licence données | Couverture | Mise à jour | Quotas | Auto-hébergeable |
|---|---|---|---|---|---|---|
| OpenStreetMap | toutes les données routières et POI | ODbL 1.0 (attribution affichée sur la carte) | mondiale | continue | — | oui |
| OpenFreeMap (tuiles, styles positron/dark) | fond de carte | ODbL (données), OpenMapTiles CC-BY 4.0 / BSD-3 (schéma, styles) | mondiale | hebdomadaire | usage raisonnable, sans clé | oui (Planetiler + nginx) |
| Valhalla FOSSGIS `valhalla1.openstreetmap.de` | itinéraires, alternatives, matrice | ODbL | mondiale | ~hebdomadaire | usage raisonnable, sans garantie | oui (`infra/`) |
| Photon komoot `photon.komoot.io` | recherche, catégories | ODbL | mondiale | ~hebdomadaire | usage raisonnable | oui (`infra/`) |
| OSM Notes API | note publique manuelle | ODbL | mondiale | temps réel | anti-abus OSM | non (service OSM) |

Les instances publiques ne doivent pas être utilisées en production à grande échelle : voir [SELF_HOSTING.md](SELF_HOSTING.md).

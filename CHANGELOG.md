# Changelog

## [0.1.0] — 2026-10-09

Première version publique (phases 0 et 1 du plan, plus une partie de la phase 2).

### Ajouté
- Nouveau logo (icône adaptative vectorielle, icône thématique et icône de notification).
- Paramètres modifiés en brouillon puis appliqués par un bouton « Enregistrer » collant en bas d’écran (confirmation si on quitte sans enregistrer) ; zoom et inclinaison appliqués immédiatement à la carte.
- Bouton son (guidage vocal) directement sur l’écran d’accueil.
- Sélecteur de langue Français / English en haut des paramètres.
- Choix du point de départ dans la prévisualisation (position actuelle par défaut).
- Proposition de déviation en cours de trajet quand un ralentissement est détecté (allure observée + zone d'exclusion Valhalla), ETA corrigée.
- Position précise : requêtes haute précision, filtrage des positions réseau, alertes « position approximative » et « GPS désactivé ».
- Vue plate par défaut, zoom rapproché, zoom et inclinaison réglables avec retour par défaut, pincement mémorisé.
- Carte MapLibre (OpenFreeMap), thèmes jour/nuit, eau bleue / espaces verts / bâtiments gris.
- Position GNSS native, curseur bleu orienté, bouton de recalibrage (sens de marche / nord en haut).
- Recherche Photon avec autocomplétion, catégories, historique ; coordonnées résolues localement.
- Favoris Maison / Travail / personnalisés avec nom et icône personnalisables.
- Prévisualisation de 1 à 3 itinéraires Valhalla, options, étapes réordonnables, liste des manœuvres.
- Machine d'états du trajet (Idle, Previewing, Navigating, Paused, Rerouting, Error, Finished) persistée et chiffrée.
- Guidage virage par virage, voix TTS locale, recalcul sur déviation avec hystérésis, service de premier plan.
- Ajout d'un arrêt en route avec détour estimé, saut/suppression/réordonnancement d'étapes.
- Pause / reprise / arrêt annulable, résumé de fin.
- Alertes personnelles locales : 12 catégories + radars/travaux, anti-doublon 30 m, opacité réduite permanente, rappels d'approche, « Mes alertes », export/import chiffrés, note OSM manuelle.
- Règles légales par pays, tableau de bord Vie privée avec journal réseau, « Tout effacer ».
- 36 tests unitaires (machine d'états, géométrie, alertes et non-fuite réseau, contrastes WCAG, parseurs).

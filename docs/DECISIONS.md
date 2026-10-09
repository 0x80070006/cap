# Décisions d'architecture (ADR)

Format : contexte → décision → conséquences. Le choix le plus conservateur (sécurité, vie privée, licence libre) est retenu par défaut.

## ADR-001 — Licence GPL-3.0-or-later
Application libre, copyleft compatible avec MapLibre (BSD), OkHttp/AndroidX (Apache-2.0). Le futur backend sera AGPL-3.0.

## ADR-002 — Un seul module Gradle pour la v0.1
Le découpage en ~30 modules du cahier des charges multiplie le temps de build et la surface de configuration avant qu'il y ait du code à isoler. Les paquets suivent le découpage cible ; l'extraction se fera en phase 2 (ticket ROADMAP R-01).

## ADR-003 — Valhalla par HTTP, moteur de guidage maison
MapLibre Navigation Android n'est plus maintenu activement et tire des dépendances Mapbox. Valhalla fournit itinéraires, alternatives, instructions localisées et matrice de temps ; le suivi de trajet (`TripEngine`) est implémenté localement, testé, et ne dépend d'aucun SDK. Valhalla embarqué (JNI) est reporté en phase 3 (R-10).

## ADR-004 — Photon pour la recherche
Autocomplétion tolérante, catégories (`include=osm.amenity.*`) sans requête texte, auto-hébergeable. La position de biais est arrondie à 0,01° (~1 km).

## ADR-005 — Position via LocationManager uniquement
Fonctionne sans Google Play Services (GrapheneOS). Le fournisseur « fused » sera un module optionnel `location-gms` (R-02).

## ADR-006 — TLS : 1.3 préféré, 1.2 AEAD minimum
Le serveur public Photon ne négocie pas TLS 1.3. Profil OkHttp `RESTRICTED_TLS` (TLS 1.3 + TLS 1.2 avec suites AEAD/PFS uniquement), `networkSecurityConfig` interdisant le clair et les CA utilisateur. L'épinglage SPKI est supporté par `HttpClients.base(pins)` pour les serveurs auto-hébergés ; il n'est pas activé sur les serveurs publics tiers dont les certificats tournent sans préavis (R-05).

## ADR-007 — Stockage chiffré par fichier plutôt que Room + SQLCipher
Les volumes (quelques milliers d'alertes, favoris, réglages) tiennent en documents JSON. Chaque document est chiffré en AES-256-GCM avec une clé non exportable de l'Android Keystore ; écriture atomique. « Tout effacer » détruit la clé (crypto-effacement). Évite une dépendance native (SQLCipher) et KSP. À réévaluer si des requêtes spatiales lourdes deviennent nécessaires (R-03).

## ADR-008 — Injection de dépendances manuelle
Un `AppContainer` explicite remplace Hilt : pas de génération de code, graphe lisible, démarrage plus rapide. Hilt pourra être introduit avec la modularisation (R-01).

## ADR-009 — Signature de release hors dépôt
Clé dans `~/.cap-signing/` (ou chemin `CAP_SIGNING_PROPERTIES`), jamais versionnée. Perdre cette clé empêche les mises à jour : la sauvegarder hors ligne.

## ADR-010 — Serveurs publics de démonstration par défaut
Pour que l'APK soit utilisable immédiatement. Ils sont signalés comme tels à l'accueil et dans Paramètres, remplaçables, et chaque requête est visible dans le journal Vie privée. La production passe par l'auto-hébergement (R-20).

## ADR-012 — Bouchons détectés par l'allure observée
Sans flux de trafic ouvert branché (phase 4), la seule information fiable et privée est la propre allure du conducteur. Sur une fenêtre de 5 min, le moteur compare le temps réellement mis à progresser au temps prévu par l'itinéraire ; le facteur de ralentissement est appliqué aux 3 km suivants (`Progress.delayS`). Au-delà de 2 min de retard, l'app demande à Valhalla un itinéraire excluant (`exclude_polygons`) un corridor de 25 m autour des 2,5 km suivants, et ne le propose que s'il fait gagner > 2 min et > 8 %. Proposé, jamais imposé ; ignoré = silence 5 min. Aucune donnée supplémentaire ne quitte l'appareil par rapport à un recalcul classique.

## ADR-013 — Langue de l'application
Android 13+ : API système de langue par application (`LocaleManager`). Versions antérieures : préférence non sensible appliquée dans `attachBaseContext`, puis redémarrage (le trajet persisté reprend en pause). Évite d'ajouter AppCompat.

## ADR-011 — Palette : demandes explicites du propriétaire
À la demande du propriétaire du projet : itinéraire actif en violet (`brand.purple`, liseré `#5B2DB8`), curseur de position en bleu (`brand.blue`), eau bleue, espaces verts verts, bâtiments gris. La palette sémantique trafic reste disjointe de ces couleurs.

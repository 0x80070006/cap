# Modèle de menace (STRIDE)

Actifs : position et destinations, alertes personnelles (lieux sensibles : domicile, trajets), favoris, intégrité du guidage.

| Menace | Catégorie | Mesure en place | Reste à faire |
|---|---|---|---|
| Un serveur reconstitue les trajets | Information disclosure | Aucun identifiant, pas de cookies, biais de recherche arrondi ~1 km, journal visible, serveurs remplaçables | Routage 100 % local hors ligne (R-10), jetons anonymes (R-30) |
| Fuite du domicile par les traces | Information disclosure | Aucune trace GPS envoyée ; seule la requête d'itinéraire contient départ/arrivée | Zones de confidentialité pour la contribution trafic (R-31) |
| Vol / perte de l'appareil | Information disclosure | AES-256-GCM, clé Keystore non exportable, `allowBackup=false`, règles d'extraction vides | Protection biométrique optionnelle (R-06), `FLAG_SECURE` sur écrans sensibles (R-07) |
| MITM | Tampering | HTTPS obligatoire (OkHttp + networkSecurityConfig), CA système uniquement, TLS 1.2 AEAD minimum | Épinglage pour serveurs auto-hébergés (R-05) |
| Réponse serveur malveillante | Tampering / DoS | Corps limités à 8 Mio, timeouts, textes nettoyés (contrôle, longueur), rendu texte uniquement, coordonnées validées | Fuzzing des parseurs (R-08) |
| Fichier d'import piégé | Tampering | Taille ≤ 8 Mio, GCM authentifié, ≤ 100 000 entités, champs validés | — |
| Application malveillante locale | Elevation | Composants non exportés sauf l'activité de lancement, service non exporté, aucun deep link | — |
| Empoisonnement du trafic | Tampering | Sans objet en v0.1 (pas de contribution) | Rejet d'outliers, k-anonymat (R-31) |
| Chaîne de build | Tampering | Versions figées dans `libs.versions.toml`, wrapper Gradle versionné, signature hors dépôt, CI publique | Builds reproductibles, SBOM, vérification des dépendances (R-40) |
| Avertisseur de radars illégal | Conformité | Règles par pays versionnées, désactivé par défaut là où c'est interdit | Détection automatique du pays hors ligne (R-12) |

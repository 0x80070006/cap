# Politique de sécurité

## Signaler une vulnérabilité

Merci de **ne pas** ouvrir d'issue publique. Utilisez « Report a vulnerability » (GitHub Security Advisories) sur ce dépôt. Indiquez la version, l'appareil, les étapes de reproduction et l'impact. Réponse visée sous 7 jours.

## Périmètre

- L'application Android (`app/`) et les fichiers de règles (`data/`).
- Les serveurs publics de démonstration (Valhalla FOSSGIS, Photon komoot, OpenFreeMap) ne sont pas opérés par ce projet.

## Mesures en place

Voir [docs/THREAT_MODEL.md](docs/THREAT_MODEL.md) : chiffrement AES-256-GCM (clé Android Keystore) de toutes les données locales, sauvegardes cloud désactivées, HTTPS obligatoire, aucun identifiant envoyé, journal réseau consultable, composants non exportés, R8 en release.

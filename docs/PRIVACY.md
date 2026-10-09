# Politique de confidentialité (langage clair)

**Cap ne vous demande aucun compte et ne collecte rien sur vous.** Il n'y a ni identifiant d'installation, ni publicité, ni outil de statistiques, ni rapport de plantage envoyé automatiquement.

## Ce qui reste sur votre téléphone

Alertes personnelles, favoris, historique de recherche, trajet en cours, réglages, journal réseau. Tout est chiffré (AES-256-GCM, clé dans l'Android Keystore), exclu des sauvegardes cloud et des transferts d'appareil. « Tout effacer » détruit aussi la clé.

## Ce qui quitte votre téléphone, et seulement quand vous l'utilisez

| Quand | Quoi | Vers |
|---|---|---|
| Vous tapez une recherche | le texte saisi, votre position arrondie à ~1 km | serveur de recherche (Photon) |
| Vous demandez un itinéraire / recalcul | départ, étapes, destination, options | serveur d'itinéraires (Valhalla) |
| Vous cherchez un arrêt en route | points échantillonnés sur le trajet, position | Photon et Valhalla |
| Vous regardez la carte | la zone affichée (tuiles) | serveur de tuiles (OpenFreeMap) |
| Vous envoyez une note OSM (manuel, après aperçu) | le texte et le point de la note | api.openstreetmap.org (public) |
| Vous partagez votre position d'urgence | un SMS rédigé par vous | le destinataire que vous choisissez |

**Jamais envoyé** : vos alertes personnelles, vos favoris, votre historique, vos trajets passés, un quelconque identifiant. Les adresses IP sont visibles des serveurs contactés, comme pour tout service web ; auto-hébergez les serveurs pour supprimer ce tiers.

Le **tableau de bord Vie privée** affiche en temps réel le nombre de requêtes et le volume échangé par usage et par serveur.

## Base légale (RGPD)

Aucune donnée personnelle n'est traitée par le projet lui-même. Les envois listés ci-dessus sont déclenchés par vous, pour exécuter votre demande. Les opérateurs des serveurs publics par défaut ont leurs propres politiques (FOSSGIS e.V., komoot, OpenFreeMap) ; tous sont situés dans l'UE.

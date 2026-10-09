# Auto-hébergement

`infra/docker-compose.yml` lance Valhalla et Photon derrière Caddy (TLS automatique) pour une région.

```bash
cd infra
cp .env.example .env    # renseigner DOMAIN, REGION_PBF_URL, PHOTON_COUNTRY
docker compose up -d
```

Puis, dans l'app : Paramètres → Serveurs → `https://route.<domaine>` et `https://search.<domaine>`.

- **Valhalla** : image `ghcr.io/gis-ops/docker-valhalla/valhalla`, construit les tuiles depuis l'extrait PBF Geofabrik indiqué (France : ~4 Go, plusieurs heures au premier lancement).
- **Photon** : image `rtuszik/photon-docker`, télécharge l'index du pays.
- **Tuiles** : pour le fond de carte, servir un fichier PMTiles/MBTiles généré par Planetiler avec un style OpenMapTiles ; ou conserver OpenFreeMap.

> Statut : configuration fournie comme point de départ, **non testée de bout en bout** dans cette version (ROADMAP R-20). Les journaux Caddy sont configurés sans adresse IP.

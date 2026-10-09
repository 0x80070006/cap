# Contribuer à Cap

1. Ouvrez une issue décrivant le besoin avant une grosse modification.
2. Branche depuis `main`, commits courts et explicites.
3. `./gradlew :app:testDebugUnitTest :app:assembleDebug` doit passer ; ajoutez des tests pour toute logique de domaine.
4. Règles non négociables (voir `docs/DECISIONS.md`) :
   - aucune donnée personnelle (position, destination, alerte) dans les logs ;
   - les alertes personnelles ne doivent jamais dépendre du réseau (`data/alerts` est vérifié par un test) ;
   - aucune dépendance propriétaire obligatoire, aucun SDK d'analytics ;
   - toute nouvelle dépendance est justifiée dans `docs/DEPENDENCIES.md` ;
   - pas de couleur en dur dans les écrans : utilisez les tokens de `ui/theme`.
5. Textes : français et anglais (`values-fr/` et `values/`).

En contribuant, vous acceptez que votre code soit publié sous GPL-3.0-or-later. Le [Code de conduite](CODE_OF_CONDUCT.md) s'applique.

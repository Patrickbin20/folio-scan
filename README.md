# Folio — Scan & documents (PWA → APK / AAB)

App web progressive 100 % hors-ligne, prête à être hébergée gratuitement sur Cloudflare
puis empaquetée pour Android avec PWABuilder (même méthode que Flash Scanner DC).

## Contenu
```
wrangler.jsonc            → configuration Cloudflare Workers (site statique)
public/
  index.html              → l'application
  manifest.webmanifest    → nom, icônes, couleurs (installation Android)
  sw.js                   → service worker (fonctionnement hors-ligne)
  confidentialite.html    → politique de confidentialité (exigée par Google Play)
  lib/                    → Lucide (icônes) + jsPDF, embarqués localement
  icons/                  → icônes 192/512 + maskable
  .well-known/            → y déposer assetlinks.json (étape 4)
```

## 1. GitHub
Créer un dépôt (ex. `folio-scan`) et y envoyer TOUT le contenu de ce dossier
(wrangler.jsonc à la racine, dossier public/ à côté).

## 2. Cloudflare (gratuit)
Workers & Pages → Create → Import a repository → choisir le dépôt.
- Build command : laisser vide
- Deploy command : `npx wrangler deploy`
→ URL du type https://folio-scan.<compte>.workers.dev
Vérifier sur le téléphone : l'app s'ouvre, la caméra marche (HTTPS), « Installer l'application » est proposé.

## 3. PWABuilder
https://www.pwabuilder.com → coller l'URL → Package for stores → Android.
- Package ID : identifiant unique et définitif, ex. `com.foliodocs.scan`
- App name : Folio — Short name : Folio
- Signing key : « Create new »
Télécharger le zip : il contient le .aab (Play Store), le .apk (installation directe),
**signing.keystore**, **signing-key-info.txt** et **assetlinks.json**.

⚠️ SAUVEGARDER IMMÉDIATEMENT signing.keystore + signing-key-info.txt
(Google Drive + clé USB). Sans eux, impossible de publier une mise à jour de l'app.

## 4. Supprimer la barre d'adresse dans l'app
Copier `assetlinks.json` dans `public/.well-known/assetlinks.json`, pousser sur GitHub
(Cloudflare redéploie seul). Vérifier : https://<url>/.well-known/assetlinks.json
Si l'app est publiée sur Play avec la « signature d'application Google Play », ajouter aussi
l'empreinte SHA-256 affichée dans Play Console → Intégrité de l'app.

## 5. Google Play
- Politique de confidentialité : https://<url>/confidentialite.html (remplacer l'e-mail dedans)
- Section « Sécurité des données » : aucune donnée collectée ni partagée
- Permission caméra : utilisée pour scanner des documents

## Mises à jour
Modifier les fichiers dans public/, puis dans sw.js passer `VERSION = 'folio-v1'` à `'folio-v2'`, etc.
Pas besoin de nouvel APK pour mettre à jour le contenu : l'app charge la nouvelle version du site.

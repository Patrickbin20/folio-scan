# DocuScan Pro

Application Android native de numérisation et de gestion de documents.
Kotlin · Jetpack Compose · Material 3 · MVVM · Room · DataStore · Google ML Kit Document Scanner.

📥 **Dernier APK :** https://github.com/Patrickbin20/folio-scan/releases/latest/download/DocuScanPro.apk

## Phase 1 (actuelle)
- Accueil : recherche, grande carte Scanner, import galerie / PDF, raccourcis, documents récents
- Scanner (ML Kit, sur l'appareil) : détection des bords, recadrage, correction de perspective,
  capture automatique et manuelle, multi-pages
- Retouche : 5 filtres (Original, Amélioré, Noir & blanc avec suppression des ombres, Niveaux de gris, Photo),
  luminosité, contraste, rotation, réorganisation, suppression de pages
- PDF léger (JPEG intégré sans recompression), export PDF ou JPEG (Storage Access Framework), partage Android
- Bibliothèque : liste/grille, recherche, tri (date/nom/taille), favoris, historique, dossiers,
  renommer, dupliquer, déplacer, supprimer
- Lecteur PDF : zoom à deux doigts / double-tap, miniatures, numéro de page
- Profil : nom, thème clair/sombre/système, qualité de scan, format d'export, stockage, confidentialité
- Aucune permission Android requise, aucun envoi de données

## Prochaines phases
2. Signature électronique · 3. Outils PDF (fusion, division, compression, mot de passe, filigrane) · 4. OCR et annotations

## Architecture
```
app/src/main/java/com/docuscanpro/app/
  data/        Room (documents, dossiers), DataStore (réglages), DocumentRepository, ExportManager
  pdf/         JpegPdfWriter (création PDF), PdfTools / PdfSession (rendu PdfRenderer)
  imaging/     ImageProcessor (filtres, seuil adaptatif, rotation, EXIF)
  ui/          AppRoot (navigation 5 onglets), state/ (ViewModels), écrans home, documents, scan, viewer, tools, profile
```

## Compilation
GitHub Actions compile et publie l'APK à chaque modification (`.github/workflows/build-apk.yml`).
Signature permanente : secrets `FOLIO_KEYSTORE_B64` et `FOLIO_KEYSTORE_PASSWORD`
(Settings → Secrets and variables → Actions). Sans eux, une clé temporaire est utilisée.
En cas d'échec, le journal d'erreurs est publié sur la branche `build-log`.

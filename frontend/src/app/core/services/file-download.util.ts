/**
 * Ouvre un onglet vide de maniere <b>synchrone</b> (dans le gestionnaire de clic), avant tout
 * appel reseau : un navigateur n'autorise {@code window.open} sans blocage que depuis un geste
 * utilisateur direct. On garde la reference et on y navigue une fois le blob recu
 * ({@link resolveBlobTab}).
 *
 * <p><b>Important</b> : ne jamais passer {@code 'noopener'} ici — avec cette option
 * {@code window.open} renvoie <b>toujours {@code null}</b> (spec HTML), on perd la reference,
 * et l'onglet reste bloque sur {@code about:blank}. Le blob affiche est same-origin
 * ({@code blob:}), il n'y a pas de risque {@code opener} a couvrir.
 */
export function openPendingTab(): Window | null {
  return window.open('about:blank', '_blank');
}

/** Ferme un onglet ouvert par {@link openPendingTab} (chemin d'erreur). */
export function closePendingTab(tab: Window | null): void {
  try {
    tab?.close();
  } catch {
    /* onglet deja ferme ou inaccessible : rien a faire */
  }
}

/**
 * Fait consulter un blob deja telecharge via {@code HttpClient} (donc avec le jeton JWT
 * attache par l'intercepteur — une navigation brute du navigateur ne porterait pas
 * {@code Authorization}).
 *
 * <p>Si l'onglet pre-ouvert est disponible, on y navigue (rendu inline : image, PDF).
 * Sinon (bloqueur de popups), repli sur un telechargement {@code <a download>}, qui n'est
 * jamais bloque meme appele de maniere asynchrone.
 */
export function resolveBlobTab(tab: Window | null, blob: Blob, fallbackFileName = 'document'): void {
  const url = URL.createObjectURL(blob);
  if (tab && !tab.closed) {
    tab.location.href = url;
  } else {
    downloadObjectUrl(url, fallbackFileName);
  }
  setTimeout(() => URL.revokeObjectURL(url), 60_000);
}

/**
 * Telecharge un blob (deja recu via {@code HttpClient}) sous {@code fileName}. Un
 * {@code <a download>} clique par programme depuis le callback d'une action utilisateur
 * n'est jamais refuse par le bloqueur de popups, contrairement a {@code window.open}
 * asynchrone.
 */
export function downloadBlob(blob: Blob, fileName: string): void {
  const url = URL.createObjectURL(blob);
  downloadObjectUrl(url, fileName);
  setTimeout(() => URL.revokeObjectURL(url), 60_000);
}

function downloadObjectUrl(url: string, fileName: string): void {
  const anchor = document.createElement('a');
  anchor.href = url;
  anchor.download = fileName;
  anchor.rel = 'noopener';
  document.body.appendChild(anchor);
  anchor.click();
  anchor.remove();
}

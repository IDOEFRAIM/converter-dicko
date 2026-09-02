/**
 * Ouvre un onglet vide de maniere synchrone (dans le gestionnaire de
 * clic), avant tout appel reseau. Les navigateurs n'autorisent
 * {@code window.open} sans blocage que lorsqu'il est appele directement
 * depuis un geste utilisateur ; si on attend la reponse HTTP (async)
 * avant d'ouvrir l'onglet, le blocueur de popups l'empeche silencieusement.
 * Passez la reference retournee a {@link resolveBlobTab} une fois le blob recu.
 */
export function openPendingTab(): Window | null {
  return window.open('', '_blank', 'noopener');
}

/**
 * Redirige un onglet deja ouvert (voir {@link openPendingTab}) vers un
 * blob telecharge via {@code HttpClient} (donc avec le jeton JWT
 * correctement attache par l'intercepteur). Un simple lien
 * {@code <a href>} vers l'API ne fonctionnerait pas : une navigation
 * brute du navigateur ne porte pas l'en-tete {@code Authorization}, et
 * l'endpoint de preuve exige une session authentifiee.
 */
export function resolveBlobTab(tab: Window | null, blob: Blob): void {
  const url = URL.createObjectURL(blob);
  if (tab) {
    tab.location.href = url;
  } else {
    // Le blocueur de popups a deja refuse l'onglet vide : repli sur
    // window.open direct (fonctionnera si les popups sont autorises
    // pour ce site, sinon l'utilisateur devra les autoriser).
    window.open(url, '_blank', 'noopener');
  }
  setTimeout(() => URL.revokeObjectURL(url), 60_000);
}

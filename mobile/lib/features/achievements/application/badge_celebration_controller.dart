import 'package:flutter/foundation.dart';

import '../../../core/errors/api_exception.dart';
import '../../../core/storage/celebrated_badges_store.dart';
import '../../notifications/data/notification_api.dart';
import '../../notifications/models/notification_models.dart';

/// Detecte un franchissement de palier de badge a celebrer *une seule fois*
/// (mission "differenciation marketing" : le moment doit etre ressenti, pas
/// seulement decouvert au prochain chargement de "Mes gains").
///
/// La source de verite reste le backend : lors du passage d'un ordre a
/// `COMPLETED`, il cree deja une notification `BADGE_UNLOCKED` avec un texte
/// pret pour l'utilisateur (voir `AchievementService.checkBadgeUnlock`). Ce
/// controleur ne fait que :
///   1. reperer la plus recente notification `BADGE_UNLOCKED` encore NON LUE
///      et jamais deja celebree localement ;
///   2. l'exposer a l'overlay de celebration ;
///   3. a la fermeture, memoriser son id localement (garde-fou) ET tenter de
///      la marquer lue cote serveur.
///
/// Aucun texte n'est synthetise ici : `title`/`message` affiches viennent
/// tels quels de la notification (meme discipline que `NotificationsPage`).
class BadgeCelebrationController extends ChangeNotifier {
  final NotificationApi _notificationApi;
  final CelebratedBadgesStore _store;

  BadgeCelebrationController({
    required NotificationApi notificationApi,
    required CelebratedBadgesStore store,
  })  : _notificationApi = notificationApi,
        _store = store;

  AppNotification? _pending;

  /// La celebration a afficher, ou `null` s'il n'y a rien a celebrer.
  AppNotification? get pending => _pending;

  bool _checking = false;

  /// A appeler a l'ouverture de la coquille a onglets et a chaque retour de
  /// l'app au premier plan. Silencieux en cas d'echec reseau : une
  /// celebration ratee n'est jamais bloquante, elle sera retentee au
  /// prochain appel. Ne relance rien tant qu'une celebration est deja en
  /// attente d'affichage.
  Future<void> check() async {
    if (_checking || _pending != null) return;
    _checking = true;
    try {
      final page = await _notificationApi.list(size: 20);
      final celebrated = await _store.read();
      // page.content est deja trie du plus recent au plus ancien : le
      // premier `BADGE_UNLOCKED` non lu / non deja celebre est le bon.
      for (final notification in page.content) {
        if (notification.kind != NotificationKind.badgeUnlocked) continue;
        if (notification.isRead) continue;
        if (celebrated.contains(notification.id)) continue;
        _pending = notification;
        notifyListeners();
        break;
      }
    } on ApiException {
      // ignore volontairement : retente au prochain check()
    } finally {
      _checking = false;
    }
  }

  /// La celebration a ete vue : on la retire de l'etat, on memorise son id
  /// localement (pour ne jamais la rejouer, meme si le `markRead` echoue),
  /// puis on tente de la marquer lue cote serveur.
  Future<void> consume() async {
    final done = _pending;
    _pending = null;
    notifyListeners();
    if (done == null) return;
    await _store.add(done.id);
    try {
      await _notificationApi.markRead(done.id);
    } on ApiException {
      // Non bloquant : l'id est deja memorise localement.
    }
  }
}

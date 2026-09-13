import 'package:flutter/material.dart';

/// Catalogue des types de notification — utilise UNIQUEMENT pour choisir une
/// icone/un regroupement. Le texte affiche (`title`/`message`) vient
/// TOUJOURS du backend, deja pret pour l'utilisateur — jamais synthetise a
/// partir du seul type (mission section 31).
enum NotificationKind {
  quoteCreated,
  paymentSubmitted,
  paymentConfirmed,
  exchangeStarted,
  exchangeProgress,
  exchangeCompleted,
  preferredRateReached,
  preferredRateExpired,
  exchangeCancelled,
  orderExpired,
  rateAlertTriggered,
  poolSucceeded,
  poolExpired,
  badgeUnlocked,
  supportReply,
  unknown;

  static NotificationKind fromCode(String? code) {
    switch (code) {
      case 'QUOTE_CREATED':
        return NotificationKind.quoteCreated;
      case 'PAYMENT_SUBMITTED':
        return NotificationKind.paymentSubmitted;
      case 'PAYMENT_CONFIRMED':
        return NotificationKind.paymentConfirmed;
      case 'EXCHANGE_STARTED':
        return NotificationKind.exchangeStarted;
      case 'EXCHANGE_PROGRESS':
        return NotificationKind.exchangeProgress;
      case 'EXCHANGE_COMPLETED':
        return NotificationKind.exchangeCompleted;
      case 'PREFERRED_RATE_REACHED':
        return NotificationKind.preferredRateReached;
      case 'PREFERRED_RATE_EXPIRED':
        return NotificationKind.preferredRateExpired;
      case 'EXCHANGE_CANCELLED':
        return NotificationKind.exchangeCancelled;
      case 'ORDER_EXPIRED':
        return NotificationKind.orderExpired;
      case 'RATE_ALERT_TRIGGERED':
        return NotificationKind.rateAlertTriggered;
      case 'POOL_SUCCEEDED':
        return NotificationKind.poolSucceeded;
      case 'POOL_EXPIRED':
        return NotificationKind.poolExpired;
      case 'BADGE_UNLOCKED':
        return NotificationKind.badgeUnlocked;
      case 'SUPPORT_REPLY':
        return NotificationKind.supportReply;
      default:
        return NotificationKind.unknown;
    }
  }

  /// Miroir exact du mapping icone Angular (`notifications.page.ts`).
  IconData get icon => switch (this) {
        NotificationKind.quoteCreated => Icons.request_quote_outlined,
        NotificationKind.paymentSubmitted => Icons.payments_outlined,
        NotificationKind.paymentConfirmed => Icons.check_circle_outline,
        NotificationKind.exchangeStarted => Icons.sync,
        NotificationKind.exchangeProgress => Icons.hourglass_top_outlined,
        NotificationKind.exchangeCompleted => Icons.task_alt_outlined,
        NotificationKind.preferredRateReached => Icons.trending_up,
        NotificationKind.preferredRateExpired => Icons.schedule_outlined,
        NotificationKind.exchangeCancelled => Icons.cancel_outlined,
        NotificationKind.orderExpired => Icons.timer_off_outlined,
        NotificationKind.rateAlertTriggered => Icons.notifications_active_outlined,
        NotificationKind.poolSucceeded => Icons.celebration_outlined,
        NotificationKind.poolExpired => Icons.hourglass_disabled_outlined,
        NotificationKind.badgeUnlocked => Icons.workspace_premium_outlined,
        NotificationKind.supportReply => Icons.chat_bubble_outline,
        NotificationKind.unknown => Icons.notifications_outlined,
      };
}

/// Miroir de `NotificationResponse` / `InboxNotification`. `title`/`message`
/// sont deja des chaines finies destinees a l'utilisateur.
class AppNotification {
  final String id;
  final NotificationKind kind;
  final String title;
  final String message;
  final DateTime createdAt;
  final DateTime? readAt;

  const AppNotification({
    required this.id,
    required this.kind,
    required this.title,
    required this.message,
    required this.createdAt,
    required this.readAt,
  });

  bool get isRead => readAt != null;

  factory AppNotification.fromJson(Map<String, dynamic> json) {
    return AppNotification(
      id: json['id'] as String,
      kind: NotificationKind.fromCode(json['type'] as String?),
      title: json['title'] as String? ?? '',
      message: json['message'] as String? ?? '',
      createdAt: DateTime.parse(json['createdAt'] as String),
      readAt: json['readAt'] == null ? null : DateTime.parse(json['readAt'] as String),
    );
  }
}

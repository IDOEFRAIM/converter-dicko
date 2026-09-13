/// Messagerie SAV (retour client : "les utilisateurs doivent pouvoir faire des reclamations") --
/// un seul fil de discussion continu par utilisateur, jamais un ticket par reclamation.
class SupportMessage {
  final String id;
  final bool fromAdmin;
  final String body;
  final DateTime createdAt;

  const SupportMessage({
    required this.id,
    required this.fromAdmin,
    required this.body,
    required this.createdAt,
  });

  factory SupportMessage.fromJson(Map<String, dynamic> json) {
    return SupportMessage(
      id: json['id'] as String,
      fromAdmin: json['fromAdmin'] as bool? ?? false,
      body: json['body'] as String? ?? '',
      createdAt: DateTime.parse(json['createdAt'] as String),
    );
  }
}

class SupportThread {
  final String userId;
  final List<SupportMessage> messages;

  const SupportThread({required this.userId, required this.messages});

  factory SupportThread.fromJson(Map<String, dynamic> json) {
    return SupportThread(
      userId: json['userId'] as String,
      messages: (json['messages'] as List<dynamic>? ?? const [])
          .map((e) => SupportMessage.fromJson(e as Map<String, dynamic>))
          .toList(growable: false),
    );
  }
}

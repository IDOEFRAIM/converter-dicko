/**
 * Messagerie SAV (retour client : "les utilisateurs doivent pouvoir faire des reclamations") —
 * un seul fil de discussion continu par utilisateur, jamais un ticket par reclamation. N'importe
 * quel administrateur peut repondre dans ce meme fil.
 */
export interface SupportMessage {
  id: string;
  /** true si envoye par un administrateur — jamais lequel precisement (fil unique, pas nominatif). */
  fromAdmin: boolean;
  body: string;
  createdAt: string;
}

export interface SupportThread {
  userId: string;
  messages: SupportMessage[];
}

/** Ligne de la boite de reception admin — un fil par utilisateur, tries par activite recente. */
export interface SupportThreadSummary {
  userId: string;
  userDisplayName: string;
  userPhone: string | null;
  lastMessageBody: string | null;
  lastMessageFromAdmin: boolean;
  lastMessageAt: string;
  hasUnreadFromUser: boolean;
}

export interface SendSupportMessageRequest {
  body: string;
}

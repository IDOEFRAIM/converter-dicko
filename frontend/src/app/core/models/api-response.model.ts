/** Enveloppe unique de toute reponse de succes de l'API backend. */
export interface ApiResponse<T> {
  data: T;
  message: string;
}

export interface FieldViolation {
  field: string;
  message: string;
}

/** Enveloppe unique de toute reponse d'erreur de l'API backend. */
export interface ErrorResponse {
  timestamp: string;
  status: number;
  error: string;
  code: string;
  message: string;
  path: string;
  traceId?: string;
  violations?: FieldViolation[];
}

export interface PageResponse<T> {
  content: T[];
  page: number;
  size: number;
  totalElements: number;
  totalPages: number;
  first: boolean;
  last: boolean;
}

import { HttpClient, HttpParams } from '@angular/common/http';
import { Injectable } from '@angular/core';
import { Observable } from 'rxjs';
import { environment } from '../../../environments/environment';
import { ApiResponse, PageResponse } from '../models/api-response.model';
import { Payment, RejectPaymentRequest } from '../models/payment.model';
import { idempotencyHeaders } from './idempotency.util';

@Injectable({ providedIn: 'root' })
export class AdminPaymentService {
  private readonly baseUrl = `${environment.apiBaseUrl}/admin/payments`;

  constructor(private readonly http: HttpClient) {}

  pending(page = 0, size = 20): Observable<ApiResponse<PageResponse<Payment>>> {
    const params = new HttpParams().set('page', page).set('size', size);
    return this.http.get<ApiResponse<PageResponse<Payment>>>(`${this.baseUrl}/pending`, { params });
  }

  get(id: string): Observable<ApiResponse<Payment>> {
    return this.http.get<ApiResponse<Payment>>(`${this.baseUrl}/${id}`);
  }

  /**
   * En-tete Idempotency-Key fortement recommande : sans elle, une reponse perdue (timeout,
   * coupure reseau) puis un nouvel essai renvoie 409 "deja traite" au lieu de la confirmation
   * deja effectuee par le premier essai (voir {@code AdminPaymentController#confirm}).
   */
  confirm(id: string, idempotencyKey?: string | null): Observable<ApiResponse<Payment>> {
    return this.http.post<ApiResponse<Payment>>(
      `${this.baseUrl}/${id}/confirm`,
      {},
      { headers: idempotencyHeaders(idempotencyKey) },
    );
  }

  reject(
    id: string,
    request: RejectPaymentRequest,
    idempotencyKey?: string | null,
  ): Observable<ApiResponse<Payment>> {
    return this.http.post<ApiResponse<Payment>>(`${this.baseUrl}/${id}/reject`, request, {
      headers: idempotencyHeaders(idempotencyKey),
    });
  }

  /** Le jeton JWT doit accompagner la requete : voir {@code resolveBlobTab}. */
  downloadProof(paymentId: string, proofId: string): Observable<Blob> {
    return this.http.get(`${this.baseUrl}/${paymentId}/proofs/${proofId}`, {
      responseType: 'blob',
    });
  }
}

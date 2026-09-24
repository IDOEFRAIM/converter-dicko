import { HttpClient } from '@angular/common/http';
import { Injectable } from '@angular/core';
import { Observable } from 'rxjs';
import { environment } from '../../../environments/environment';
import { ApiResponse } from '../models/api-response.model';
import { Payment, PaymentProof, SubmitPaymentRequest } from '../models/payment.model';
import { idempotencyHeaders } from './idempotency.util';

@Injectable({ providedIn: 'root' })
export class PaymentService {
  private readonly baseUrl = environment.apiBaseUrl;

  constructor(private readonly http: HttpClient) {}

  /** {@code idempotencyKey} : un rejeu avec la meme cle ne soumet jamais un second paiement. */
  submit(
    orderId: string,
    request: SubmitPaymentRequest,
    idempotencyKey?: string | null,
  ): Observable<ApiResponse<Payment>> {
    return this.http.post<ApiResponse<Payment>>(`${this.baseUrl}/v1/orders/${orderId}/payments`, request, {
      headers: idempotencyHeaders(idempotencyKey),
    });
  }

  get(paymentId: string): Observable<ApiResponse<Payment>> {
    return this.http.get<ApiResponse<Payment>>(`${this.baseUrl}/v1/payments/${paymentId}`);
  }

  uploadProof(paymentId: string, file: File): Observable<ApiResponse<PaymentProof>> {
    const formData = new FormData();
    formData.append('file', file);
    return this.http.post<ApiResponse<PaymentProof>>(
      `${this.baseUrl}/v1/payments/${paymentId}/proofs`,
      formData,
    );
  }

  /** Le jeton JWT doit accompagner la requete : voir {@code resolveBlobTab}. */
  downloadProof(paymentId: string, proofId: string): Observable<Blob> {
    return this.http.get(`${this.baseUrl}/v1/payments/${paymentId}/proofs/${proofId}`, {
      responseType: 'blob',
    });
  }
}

import { HttpClient, HttpParams } from '@angular/common/http';
import { Injectable } from '@angular/core';
import { Observable } from 'rxjs';
import { environment } from '../../../environments/environment';
import { ApiResponse, PageResponse } from '../models/api-response.model';
import {
  CancelOrderRequest,
  CreateOrderRequest,
  OrderDetail,
  OrderFeasibility,
  OrderHistoryEntry,
  OrderHistoryQuery,
  OrderSummary,
} from '../models/order.model';
import { OrderTracking } from '../models/tracking.model';
import { idempotencyHeaders } from './idempotency.util';

@Injectable({ providedIn: 'root' })
export class OrderService {
  private readonly baseUrl = `${environment.apiBaseUrl}/v1/orders`;

  constructor(private readonly http: HttpClient) {}

  /** {@code idempotencyKey} : un rejeu (double clic, timeout) avec la meme cle ne cree jamais un second ordre. */
  create(request: CreateOrderRequest, idempotencyKey?: string | null): Observable<ApiResponse<OrderDetail>> {
    return this.http.post<ApiResponse<OrderDetail>>(this.baseUrl, request, {
      headers: idempotencyHeaders(idempotencyKey),
    });
  }

  list(page = 0, size = 20): Observable<ApiResponse<PageResponse<OrderSummary>>> {
    const params = new HttpParams().set('page', page).set('size', size);
    return this.http.get<ApiResponse<PageResponse<OrderSummary>>>(this.baseUrl, { params });
  }

  /**
   * Historique enrichi, filtre et pagine <b>cote serveur</b> (jamais un filtrage local).
   * Un {@code supplierId} d'un autre utilisateur ne renvoie jamais ses ordres, seulement
   * aucun resultat.
   */
  history(query: OrderHistoryQuery = {}): Observable<ApiResponse<PageResponse<OrderHistoryEntry>>> {
    let params = new HttpParams()
      .set('page', query.page ?? 0)
      .set('size', query.size ?? 20);
    if (query.status) {
      params = params.set('status', query.status);
    }
    if (query.purpose) {
      params = params.set('purpose', query.purpose);
    }
    if (query.supplierId) {
      params = params.set('supplierId', query.supplierId);
    }
    if (query.from) {
      params = params.set('from', query.from);
    }
    if (query.to) {
      params = params.set('to', query.to);
    }
    return this.http.get<ApiResponse<PageResponse<OrderHistoryEntry>>>(`${this.baseUrl}/history`, {
      params,
    });
  }

  get(id: string): Observable<ApiResponse<OrderDetail>> {
    return this.http.get<ApiResponse<OrderDetail>>(`${this.baseUrl}/${id}`);
  }

  /**
   * Vérifie, avant la saisie du bénéficiaire, qu'un ordre est réalisable à partir de ce devis
   * (accepté, propriété du client, liquidité CNY suffisante actuellement). Aucun solde exposé.
   */
  checkFeasibility(quoteId: string): Observable<ApiResponse<OrderFeasibility>> {
    const params = new HttpParams().set('quoteId', quoteId);
    return this.http.get<ApiResponse<OrderFeasibility>>(`${this.baseUrl}/feasibility`, { params });
  }

  cancel(id: string, request: CancelOrderRequest): Observable<ApiResponse<OrderDetail>> {
    return this.http.post<ApiResponse<OrderDetail>>(`${this.baseUrl}/${id}/cancel`, request);
  }

  /** Timeline agregee (lecture seule) — jamais une seconde machine d'etat cote client. */
  getTracking(id: string): Observable<ApiResponse<OrderTracking>> {
    return this.http.get<ApiResponse<OrderTracking>>(`${this.baseUrl}/${id}/tracking`);
  }

  /**
   * Justificatif PDF (uniquement pour un ordre COMPLETED). Renvoie le blob brut — le PDF est
   * genere par le backend a partir du snapshot financier immuable, jamais reconstruit ici.
   * Le jeton JWT est attache par l'intercepteur car la requete passe par HttpClient.
   */
  downloadReceipt(id: string): Observable<Blob> {
    return this.http.get(`${this.baseUrl}/${id}/receipt`, { responseType: 'blob' });
  }

  /** Facture proforma PDF — memes regles que le justificatif (`GET /v1/orders/{id}/proforma`). */
  downloadProforma(id: string): Observable<Blob> {
    return this.http.get(`${this.baseUrl}/${id}/proforma`, { responseType: 'blob' });
  }
}

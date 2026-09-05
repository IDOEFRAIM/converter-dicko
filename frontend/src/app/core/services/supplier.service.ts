import { HttpClient, HttpParams } from '@angular/common/http';
import { Injectable } from '@angular/core';
import { Observable } from 'rxjs';
import { environment } from '../../../environments/environment';
import { ApiResponse, PageResponse } from '../models/api-response.model';
import { OrderDetail } from '../models/order.model';
import {
  PayAgainRequest,
  SupplierDetail,
  SupplierRequest,
  SupplierStatus,
  SupplierSummary,
} from '../models/supplier.model';
import { idempotencyHeaders } from './idempotency.util';

/**
 * Carnet de fournisseurs du client + "payer a nouveau". Aucune logique metier ici :
 * chaque methode transmet la requete et retourne la reponse backend telle quelle.
 * L'ownership (404, jamais 403) et l'immutabilite des ordres deja crees sont garanties
 * cote backend.
 */
@Injectable({ providedIn: 'root' })
export class SupplierService {
  private readonly baseUrl = `${environment.apiBaseUrl}/v1/suppliers`;

  constructor(private readonly http: HttpClient) {}

  list(
    page = 0,
    size = 20,
    status?: SupplierStatus,
  ): Observable<ApiResponse<PageResponse<SupplierSummary>>> {
    let params = new HttpParams().set('page', page).set('size', size);
    if (status) {
      params = params.set('status', status);
    }
    return this.http.get<ApiResponse<PageResponse<SupplierSummary>>>(this.baseUrl, { params });
  }

  listFavorites(page = 0, size = 20): Observable<ApiResponse<PageResponse<SupplierSummary>>> {
    const params = new HttpParams().set('page', page).set('size', size);
    return this.http.get<ApiResponse<PageResponse<SupplierSummary>>>(`${this.baseUrl}/favorites`, {
      params,
    });
  }

  get(id: string): Observable<ApiResponse<SupplierDetail>> {
    return this.http.get<ApiResponse<SupplierDetail>>(`${this.baseUrl}/${id}`);
  }

  create(request: SupplierRequest): Observable<ApiResponse<SupplierDetail>> {
    return this.http.post<ApiResponse<SupplierDetail>>(this.baseUrl, request);
  }

  update(id: string, request: SupplierRequest): Observable<ApiResponse<SupplierDetail>> {
    return this.http.put<ApiResponse<SupplierDetail>>(`${this.baseUrl}/${id}`, request);
  }

  setFavorite(id: string, favorite: boolean): Observable<ApiResponse<SupplierDetail>> {
    const action = favorite ? 'favorite' : 'unfavorite';
    return this.http.post<ApiResponse<SupplierDetail>>(`${this.baseUrl}/${id}/${action}`, {});
  }

  deactivate(id: string): Observable<ApiResponse<SupplierDetail>> {
    return this.http.post<ApiResponse<SupplierDetail>>(`${this.baseUrl}/${id}/deactivate`, {});
  }

  /**
   * Cree un NOUVEAU devis (pricing courant) puis un NOUVEL ordre. Ne copie jamais le taux,
   * les frais ni le montant d'une transaction passee. {@code idempotencyKey} : voir
   * {@link newIdempotencyKey}.
   */
  payAgain(
    id: string,
    request: PayAgainRequest,
    idempotencyKey?: string | null,
  ): Observable<ApiResponse<OrderDetail>> {
    return this.http.post<ApiResponse<OrderDetail>>(`${this.baseUrl}/${id}/pay-again`, request, {
      headers: idempotencyHeaders(idempotencyKey),
    });
  }
}

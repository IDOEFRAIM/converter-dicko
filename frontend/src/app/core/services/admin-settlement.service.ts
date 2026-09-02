import { HttpClient, HttpParams } from '@angular/common/http';
import { Injectable } from '@angular/core';
import { Observable } from 'rxjs';
import { environment } from '../../../environments/environment';
import { ApiResponse, PageResponse } from '../models/api-response.model';
import { ExecuteSettlementRequest, Settlement, SettlementProof } from '../models/settlement.model';

@Injectable({ providedIn: 'root' })
export class AdminSettlementService {
  private readonly baseUrl = `${environment.apiBaseUrl}/admin`;

  constructor(private readonly http: HttpClient) {}

  createForOrder(orderId: string): Observable<ApiResponse<Settlement>> {
    return this.http.post<ApiResponse<Settlement>>(`${this.baseUrl}/orders/${orderId}/settlement`, {});
  }

  pending(page = 0, size = 20): Observable<ApiResponse<PageResponse<Settlement>>> {
    const params = new HttpParams().set('page', page).set('size', size);
    return this.http.get<ApiResponse<PageResponse<Settlement>>>(`${this.baseUrl}/settlements/pending`, { params });
  }

  get(id: string): Observable<ApiResponse<Settlement>> {
    return this.http.get<ApiResponse<Settlement>>(`${this.baseUrl}/settlements/${id}`);
  }

  uploadProof(settlementId: string, file: File): Observable<ApiResponse<SettlementProof>> {
    const formData = new FormData();
    formData.append('file', file);
    return this.http.post<ApiResponse<SettlementProof>>(
      `${this.baseUrl}/settlements/${settlementId}/proofs`,
      formData,
    );
  }

  execute(id: string, request: ExecuteSettlementRequest): Observable<ApiResponse<Settlement>> {
    return this.http.post<ApiResponse<Settlement>>(`${this.baseUrl}/settlements/${id}/execute`, request);
  }

  /** Le jeton JWT doit accompagner la requete : voir {@code resolveBlobTab}. */
  downloadProof(settlementId: string, proofId: string): Observable<Blob> {
    return this.http.get(`${this.baseUrl}/settlements/${settlementId}/proofs/${proofId}`, {
      responseType: 'blob',
    });
  }
}

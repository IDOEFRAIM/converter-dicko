import { HttpClient, HttpParams } from '@angular/common/http';
import { Injectable } from '@angular/core';
import { Observable } from 'rxjs';
import { environment } from '../../../environments/environment';
import { ApiResponse, PageResponse } from '../models/api-response.model';
import { KycAdminSubmission, KycFileKind, RejectKycRequest } from '../models/kyc.model';

/** Acces a `/api/admin/kyc/**` — revue manuelle des dossiers KYC (remarque produit #6). */
@Injectable({ providedIn: 'root' })
export class AdminKycService {
  private readonly baseUrl = `${environment.apiBaseUrl}/admin/kyc`;

  constructor(private readonly http: HttpClient) {}

  pending(page = 0, size = 20): Observable<ApiResponse<PageResponse<KycAdminSubmission>>> {
    const params = new HttpParams().set('page', page).set('size', size);
    return this.http.get<ApiResponse<PageResponse<KycAdminSubmission>>>(`${this.baseUrl}/submissions`, {
      params,
    });
  }

  approve(id: string): Observable<ApiResponse<void>> {
    return this.http.post<ApiResponse<void>>(`${this.baseUrl}/submissions/${id}/approve`, {});
  }

  reject(id: string, request: RejectKycRequest): Observable<ApiResponse<void>> {
    return this.http.post<ApiResponse<void>>(`${this.baseUrl}/submissions/${id}/reject`, request);
  }

  /** Le jeton JWT doit accompagner la requete : voir {@code resolveBlobTab}. */
  downloadFile(id: string, kind: KycFileKind): Observable<Blob> {
    return this.http.get(`${this.baseUrl}/submissions/${id}/files/${kind}`, {
      responseType: 'blob',
    });
  }
}

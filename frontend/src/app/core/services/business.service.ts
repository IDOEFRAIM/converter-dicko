import { HttpClient, HttpParams } from '@angular/common/http';
import { Injectable } from '@angular/core';
import { Observable } from 'rxjs';
import { environment } from '../../../environments/environment';
import { ApiResponse } from '../models/api-response.model';
import {
  BusinessPaymentSummary,
  BusinessProfile,
  UpsertBusinessProfileRequest,
} from '../models/business.model';

/**
 * Profil professionnel + reporting. Aucune logique metier : `PUT` est un upsert idempotent
 * cote backend (crее si absent, met a jour sinon), le reporting est une lecture pure des
 * ordres deja enregistres. Un `404 BUSINESS_PROFILE_NOT_FOUND` signifie simplement que
 * l'utilisateur n'a pas (encore) de profil — il est alors PERSONAL.
 */
@Injectable({ providedIn: 'root' })
export class BusinessService {
  private readonly profileUrl = `${environment.apiBaseUrl}/v1/business-profile`;
  private readonly reportingUrl = `${environment.apiBaseUrl}/v1/business`;

  constructor(private readonly http: HttpClient) {}

  getProfile(): Observable<ApiResponse<BusinessProfile>> {
    return this.http.get<ApiResponse<BusinessProfile>>(this.profileUrl);
  }

  upsertProfile(request: UpsertBusinessProfileRequest): Observable<ApiResponse<BusinessProfile>> {
    return this.http.put<ApiResponse<BusinessProfile>>(this.profileUrl, request);
  }

  paymentsSummary(from?: string | null, to?: string | null): Observable<ApiResponse<BusinessPaymentSummary>> {
    let params = new HttpParams();
    if (from) {
      params = params.set('from', from);
    }
    if (to) {
      params = params.set('to', to);
    }
    return this.http.get<ApiResponse<BusinessPaymentSummary>>(`${this.reportingUrl}/payments/summary`, {
      params,
    });
  }
}
